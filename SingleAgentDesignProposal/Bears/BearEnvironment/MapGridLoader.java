package Bears.BearEnvironment;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader for pre-processed Romania habitat grids.
 *
 * <p>File format ("RBG1" - Romania Bear Grid v1) is intentionally trivial so
 * the Java side has zero external dependencies; the real GeoTIFF / CLC
 * processing happens in a Python preprocessor (see
 * {@code tools/build_romania_map.py}). The same loader also accepts the stub
 * map produced by {@code Bears.BearEnvironment.BuildStubRomaniaMap}.</p>
 *
 * <pre>
 * # Free-form comment lines start with '#'.
 * # All key=value header lines come BEFORE the data block.
 * width=200
 * height=200
 * # Optional headers (recorded but not enforced):
 * # source=CLC2018
 * # cell_km=5.0
 * # crs=EPSG:3035
 * #
 * # Data: one line per row, exactly `width` single-character cell codes.
 * #   F = FOREST, I = FIELD, V = VILLAGE, R = ROAD,
 * #   M = MOUNTAIN, N or '~' or ' ' = NONE (outside study area)
 * FFFFIIIIVVMM...
 * FFFIIVVVVMMM...
 * </pre>
 *
 * <p>Currently only square grids are supported (mapLength = width = height),
 * matching the rest of the simulation. The loader throws an
 * {@link IllegalArgumentException} otherwise.</p>
 */
public final class MapGridLoader {

    public static final class Grid {
        public final int width;
        public final int height;
        public final BearCellType[][] cells; // cells[x][y]

        Grid(int width, int height, BearCellType[][] cells) {
            this.width = width;
            this.height = height;
            this.cells = cells;
        }

        public int mapLength() {
            if (width != height) {
                throw new IllegalStateException(
                        "Non-square grid is not supported (width=" + width + ", height=" + height + ")");
            }
            return width;
        }
    }

    private MapGridLoader() {}

    public static Grid load(Path path) throws IOException {
        Integer width = null;
        Integer height = null;
        List<String> dataLines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.contains("=")) {
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim().toLowerCase();
                    String value = trimmed.substring(eq + 1).trim();
                    if (key.equals("width")) {
                        width = Integer.parseInt(value);
                    } else if (key.equals("height")) {
                        height = Integer.parseInt(value);
                    }
                    // unknown headers are recorded as metadata but ignored.
                    continue;
                }
                dataLines.add(trimmed);
            }
        }

        if (width == null || height == null) {
            throw new IllegalArgumentException(
                    "Grid file " + path + " is missing required width / height headers.");
        }
        if (dataLines.size() != height) {
            throw new IllegalArgumentException("Grid file " + path
                    + " declared height=" + height + " but contains " + dataLines.size() + " data rows.");
        }

        BearCellType[][] cells = new BearCellType[width][height];
        for (int y = 0; y < height; y++) {
            String row = dataLines.get(y);
            if (row.length() != width) {
                throw new IllegalArgumentException("Grid file " + path + " row " + y
                        + " has length " + row.length() + " (expected " + width + ").");
            }
            for (int x = 0; x < width; x++) {
                cells[x][y] = BearCellType.fromChar(row.charAt(x));
            }
        }
        return new Grid(width, height, cells);
    }
}
