#!/usr/bin/env python3
"""Build a habitat grid for the Romanian brown-bear simulation from a
Corine Land Cover (CLC) raster (or any compatible land-cover GeoTIFF).

Input:
    --clc       Path to a CLC GeoTIFF clipped to (at least) Romania.
                Recommended: CLC2018, 100 m, EPSG:3035.
                Download:  https://land.copernicus.eu/pan-european/corine-land-cover
    --mask      Optional Romania-shape polygon (GeoJSON/Shapefile/GeoTIFF).
                If supplied, cells outside the polygon are emitted as NONE.
                A free polygon is available from Natural Earth or GADM.
    --dem       Optional DEM GeoTIFF (e.g. EU-DEM v1.1, 25 m). When supplied,
                cells whose mean elevation exceeds --mountain-threshold-m are
                reclassified MOUNTAIN regardless of their CLC class. This is
                useful because CLC does not distinguish alpine bare rock
                consistently and bears use the Carpathian arc heavily.

Output:
    A plain-text grid file consumable by Bears.BearEnvironment.MapGridLoader.
    Each line of the data block is one row of single-character cell codes:
        F = FOREST  I = FIELD  V = VILLAGE  R = ROAD
        M = MOUNTAIN  N = NONE (outside study area)

Dependencies:
    rasterio>=1.3, numpy, optionally fiona/shapely for vector masks.
    `pip install rasterio numpy` is enough for the raster-only path.

Example:
    python tools/build_romania_map.py \
        --clc data/U2018_CLC2018_V2020_20u1.tif \
        --mask data/romania_outline.geojson \
        --dem data/eu_dem_v11.tif \
        --grid-size 200 \
        --out reference-data/romania-map-clc2018.txt

The grid is built by majority-voting CLC pixels into each coarse cell, so
the output resolution is determined by --grid-size (e.g. 200x200 cells over
the Romanian bear range ~= 4-5 km per cell).
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    import numpy as np
    import rasterio
    from rasterio.warp import calculate_default_transform, reproject, Resampling, transform_bounds
    from rasterio.windows import from_bounds
    from rasterio import features
except ImportError as exc:  # pragma: no cover
    sys.stderr.write(
        "Missing dependency: %s\nInstall with: pip install rasterio numpy\n" % exc
    )
    raise SystemExit(2)


def _load_vector_mask(path: Path):
    """Return (geometries, src_crs) from a vector file (.gpkg/.shp/.geojson).

    Tries fiona first; falls back to pyogrio's low-level reader (no geopandas
    required). Geometries are returned as GeoJSON-style dicts.
    """
    try:
        import fiona  # type: ignore
        with fiona.open(path) as src:
            geoms = [feat["geometry"] for feat in src]
            return geoms, src.crs_wkt or src.crs
    except ImportError:
        pass
    try:
        import pyogrio  # type: ignore
        from shapely.geometry import mapping  # type: ignore
        from shapely import from_wkb  # type: ignore
        # Default to the country-outline layer (ADM_ADM_0) for GADM packages;
        # falls back to the first layer for other vector files.
        layers = pyogrio.list_layers(path)
        layer_name = None
        if len(layers) > 0:
            preferred = [name for name, *_ in layers if str(name).endswith("_0")]
            layer_name = preferred[0] if preferred else layers[0][0]
        info = pyogrio.read_info(path, layer=layer_name)
        crs = info.get("crs")
        # pyogrio.raw.read returns (meta, fids, geometries, field_data).
        result = pyogrio.raw.read(path, layer=layer_name, read_geometry=True)
        geom_wkbs = result[2]
        if geom_wkbs is None or len(geom_wkbs) == 0:
            raise SystemExit(
                f"No geometries returned from {path} (layer={layer_name}). "
                "Available layers: " + repr(layers)
            )
        geoms = [mapping(from_wkb(b)) for b in geom_wkbs if b is not None]
        return geoms, crs
    except ImportError as exc:
        raise SystemExit(
            "Vector mask requires fiona or (pyogrio + shapely). Install with:\n"
            "  pip install fiona\n  or\n  pip install pyogrio shapely\n"
            f"(original error: {exc})"
        )


# ---------------------------------------------------------------------------
# CLC -> simulation cell-type mapping.
# CLC level-3 codes (1..44). Anything not listed falls through to FIELD.
# Sources:
#   https://land.copernicus.eu/user-corner/technical-library/corine-land-cover-nomenclature-guidelines/html
# ---------------------------------------------------------------------------
CLC_TO_SIM = {}
# 1.x Artificial surfaces -> VILLAGE (sequential class indices 1..11)
#   1=111 urban, 2=112 urban, 3=121 industrial, 4=122 road/rail, 5=123 port,
#   6=124 airport, 7=131 mineral, 8=132 dump, 9=133 construction,
#   10=141 green urban, 11=142 sport/leisure.
for code in range(1, 12):
    CLC_TO_SIM[code] = "V"
# Road and rail networks specifically -> ROAD (overrides VILLAGE).
CLC_TO_SIM[4] = "R"
# 2.x Agricultural areas -> FIELD (indices 12..22).
#   12=211 non-irrigated arable, ..., 18=231 pastures, 22=244 agro-forestry.
for code in range(12, 23):
    CLC_TO_SIM[code] = "I"
# 3.1.x Forests -> FOREST (23 broad-leaved, 24 coniferous, 25 mixed).
for code in (23, 24, 25):
    CLC_TO_SIM[code] = "F"
# 3.2.x Scrub and herbaceous vegetation -> FOREST (low-quality bear habitat).
for code in (26, 27, 28, 29):
    CLC_TO_SIM[code] = "F"
# 3.3.x Open spaces with little or no vegetation -> MOUNTAIN.
for code in (30, 31, 32, 33, 34):
    CLC_TO_SIM[code] = "M"
# 4.x Wetlands and 5.x Water bodies -> NONE (bears avoid open water).
for code in range(35, 45):
    CLC_TO_SIM[code] = "N"


SIM_CODE_ORDER = ["F", "I", "V", "R", "M", "N"]
SIM_CODE_TO_INDEX = {c: i for i, c in enumerate(SIM_CODE_ORDER)}


def majority_downsample(reclass: np.ndarray, target: int) -> np.ndarray:
    """Block-aggregate a (H, W) int8 array of sim codes into (target, target).

    Pure majority vote loses linear (R) and small (V, M) features at coarse
    resolutions, so we use a priority+threshold scheme:
      * If R covers >= R_THRESH of the block -> R (roads are thin)
      * else if V covers >= V_THRESH -> V (villages are small but matter)
      * else if M covers >= M_THRESH -> M (mountain refugia)
      * else argmax over (F, I, N).
    N still wins outright when most pixels are masked out."""
    H, W = reclass.shape
    side = min(H, W)
    y0 = (H - side) // 2
    x0 = (W - side) // 2
    cropped = reclass[y0 : y0 + side, x0 : x0 + side]

    block = side // target
    if block < 1:
        raise SystemExit("--grid-size %d is larger than the raster side (%d)." % (target, side))
    trimmed = cropped[: block * target, : block * target]
    reshaped = trimmed.reshape(target, block, target, block)
    pixels_per_block = block * block

    counts = np.zeros((target, target, len(SIM_CODE_ORDER)), dtype=np.int32)
    for idx in range(len(SIM_CODE_ORDER)):
        counts[:, :, idx] = (reshaped == idx).sum(axis=(1, 3))

    f_idx = SIM_CODE_TO_INDEX["F"]
    i_idx = SIM_CODE_TO_INDEX["I"]
    v_idx = SIM_CODE_TO_INDEX["V"]
    r_idx = SIM_CODE_TO_INDEX["R"]
    m_idx = SIM_CODE_TO_INDEX["M"]
    n_idx = SIM_CODE_TO_INDEX["N"]

    # Thresholds expressed as fractions of the block.
    R_THRESH = 0.003   # ~0.3% : a single 100m road segment crossing the block
    V_THRESH = 0.15    # ~15%  : block dominated by built environment
    M_THRESH = 0.04    # ~4%   : modest bare-rock / sparse-veg patch

    r_min = max(1, int(round(R_THRESH * pixels_per_block)))
    v_min = max(1, int(round(V_THRESH * pixels_per_block)))
    m_min = max(1, int(round(M_THRESH * pixels_per_block)))

    # Background = argmax over F / I / N (the broad land-cover classes).
    bg_stack = np.stack(
        [counts[:, :, f_idx], counts[:, :, i_idx], counts[:, :, n_idx]],
        axis=-1,
    )
    bg_pick = np.argmax(bg_stack, axis=-1)
    bg_map = np.array([f_idx, i_idx, n_idx], dtype=np.int8)
    out = bg_map[bg_pick]

    # Promote priority classes in order N < bg < M < V < R.
    # N still dominates if it is the bg pick AND coverage is overwhelming.
    overwhelming_n = counts[:, :, n_idx] >= int(0.85 * pixels_per_block)

    promote_m = (counts[:, :, m_idx] >= m_min) & ~overwhelming_n
    out = np.where(promote_m, m_idx, out)
    promote_v = (counts[:, :, v_idx] >= v_min) & ~overwhelming_n
    out = np.where(promote_v, v_idx, out)
    promote_r = (counts[:, :, r_idx] >= r_min) & ~overwhelming_n
    out = np.where(promote_r, r_idx, out)
    return out.astype(np.int8)


def apply_dem(out: np.ndarray, dem: np.ndarray, threshold_m: float) -> np.ndarray:
    """Override cells with mean elevation above threshold to MOUNTAIN."""
    if dem.shape != out.shape:
        # Resample DEM with simple block-mean.
        H_d, W_d = dem.shape
        H_o, W_o = out.shape
        block_y = max(H_d // H_o, 1)
        block_x = max(W_d // W_o, 1)
        trimmed = dem[: block_y * H_o, : block_x * W_o]
        dem = trimmed.reshape(H_o, block_y, W_o, block_x).mean(axis=(1, 3))
    mountain_idx = SIM_CODE_TO_INDEX["M"]
    none_idx = SIM_CODE_TO_INDEX["N"]
    high = dem > threshold_m
    out = np.where(high & (out != none_idx), mountain_idx, out)
    return out


def reclassify(clc_arr: np.ndarray) -> np.ndarray:
    """Map raw CLC codes to sim-code indices."""
    lookup = np.full(256, SIM_CODE_TO_INDEX["I"], dtype=np.int8)  # default FIELD
    for code, sim in CLC_TO_SIM.items():
        lookup[code] = SIM_CODE_TO_INDEX[sim]
    safe = np.clip(clc_arr, 0, 255).astype(np.uint8)
    return lookup[safe]


def load_raster_aligned(path: Path, like_src: rasterio.io.DatasetReader) -> np.ndarray:
    """Reproject `path` to match `like_src` grid; return as 2D array."""
    with rasterio.open(path) as src:
        dst = np.zeros((like_src.height, like_src.width), dtype=src.dtypes[0])
        reproject(
            source=rasterio.band(src, 1),
            destination=dst,
            src_transform=src.transform,
            src_crs=src.crs,
            dst_transform=like_src.transform,
            dst_crs=like_src.crs,
            resampling=Resampling.nearest,
        )
        return dst


def write_grid(path: Path, indices: np.ndarray, source_meta: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    H, W = indices.shape
    with path.open("w", encoding="utf-8") as fh:
        fh.write("# Romania bear habitat grid built from CLC raster.\n")
        for k, v in source_meta.items():
            fh.write(f"# {k}={v}\n")
        fh.write(f"width={W}\n")
        fh.write(f"height={H}\n")
        for row in indices:
            fh.write("".join(SIM_CODE_ORDER[i] for i in row) + "\n")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--clc", required=True, type=Path, help="CLC GeoTIFF (e.g. CLC2018 100m)")
    p.add_argument("--mask", type=Path,
                   help="Optional country mask. Either a vector (.gpkg/.shp/.geojson) "
                        "polygon - recommended - or an aligned raster where 0 = outside.")
    p.add_argument("--dem", type=Path, help="Optional DEM raster for mountain override")
    p.add_argument("--mountain-threshold-m", type=float, default=1200.0,
                   help="Cells above this mean elevation are forced to MOUNTAIN (default: 1200)")
    p.add_argument("--grid-size", type=int, default=200, help="Output grid side (default: 200)")
    p.add_argument("--out", required=True, type=Path, help="Output grid file")
    args = p.parse_args()

    with rasterio.open(args.clc) as clc_src:
        # If a vector mask is supplied, crop CLC to its bounding box first so
        # we don't carry all of Europe through reclassification/downsampling.
        vector_geoms = None
        vector_crs = None
        if args.mask is not None and args.mask.suffix.lower() in {".gpkg", ".shp", ".geojson", ".json"}:
            vector_geoms, vector_crs = _load_vector_mask(args.mask)
            if not vector_geoms:
                raise SystemExit(f"No geometries read from {args.mask}")
            # Reproject the vector polygons into CLC's CRS to build a read window.
            from rasterio.warp import transform_geom
            from shapely.geometry import shape  # type: ignore
            reproj_geoms = [transform_geom(vector_crs, clc_src.crs, g) for g in vector_geoms]
            shapes = [shape(g) for g in reproj_geoms]
            minx = min(s.bounds[0] for s in shapes)
            miny = min(s.bounds[1] for s in shapes)
            maxx = max(s.bounds[2] for s in shapes)
            maxy = max(s.bounds[3] for s in shapes)
            print(f"Mask bounds in CLC CRS ({clc_src.crs}): "
                  f"x=[{minx:.0f},{maxx:.0f}] y=[{miny:.0f},{maxy:.0f}]")
            window = from_bounds(minx, miny, maxx, maxy, clc_src.transform)
            window = window.round_offsets().round_lengths()
            clc_arr = clc_src.read(1, window=window)
            window_transform = clc_src.window_transform(window)
            # Build the in-window mask: 1 inside the polygon, 0 outside.
            inside = features.rasterize(
                [(g, 1) for g in reproj_geoms],
                out_shape=clc_arr.shape,
                transform=window_transform,
                fill=0,
                dtype=np.uint8,
            )
            print(f"Cropped CLC window: {clc_arr.shape}, vector mask coverage: "
                  f"{inside.sum() / inside.size:.1%}")
        else:
            clc_arr = clc_src.read(1)
            window_transform = clc_src.transform
            inside = None

        reclassified = reclassify(clc_arr)

        if inside is not None:
            reclassified = np.where(inside == 0, SIM_CODE_TO_INDEX["N"], reclassified)
        elif args.mask is not None:
            # Raster mask path (legacy).
            mask_arr = load_raster_aligned(args.mask, clc_src)
            outside = mask_arr == 0
            reclassified = np.where(outside, SIM_CODE_TO_INDEX["N"], reclassified)

        if args.dem is not None:
            dem_arr = load_raster_aligned(args.dem, clc_src).astype(np.float32)

        downsampled = majority_downsample(reclassified, args.grid_size)

        if args.dem is not None:
            # Downsample DEM with simple block-mean to match.
            downsampled = apply_dem(downsampled, dem_arr, args.mountain_threshold_m)

        meta = {
            "source": str(args.clc.name),
            "crs": str(clc_src.crs),
            "grid_size": args.grid_size,
            "mask": str(args.mask.name) if args.mask else "none",
            "dem": str(args.dem.name) if args.dem else "none",
        }
        write_grid(args.out, downsampled, meta)
        print(f"Wrote {args.grid_size}x{args.grid_size} grid to {args.out}")
        # Per-class fractions for sanity.
        total = downsampled.size
        for idx, code in enumerate(SIM_CODE_ORDER):
            frac = (downsampled == idx).sum() / total
            print(f"  {code}: {frac:6.2%}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
