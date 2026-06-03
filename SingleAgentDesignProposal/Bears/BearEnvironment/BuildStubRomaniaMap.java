package Bears.BearEnvironment;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One-shot generator for a stub "Romania-shaped" habitat grid that ships with
 * the repo so the grid-loading pipeline works out of the box, without
 * requiring users to run the Python / GDAL preprocessor first.
 *
 * <p>The stub is a deliberate caricature: a rectangular country footprint with
 * the four corners trimmed, a diagonal Carpathian-arc mountain band, a forest
 * buffer around the mountains, lowland fields filling the rest, a Poisson-disk
 * sample of villages biased to lowlands, and short road segments connecting
 * nearby villages. It is NOT a real habitat map; it just gives the simulation
 * a spatially structured landscape with the right cell-type vocabulary so
 * dispersal, conflict events and crowding behave realistically until a real
 * CLC2018-derived grid is dropped in to replace it.</p>
 *
 * <p>Run via {@code java Bears.BearEnvironment.BuildStubRomaniaMap
 * reference-data/romania-map-stub.txt} from the {@code SingleAgentDesignProposal}
 * directory. The output file is consumed by {@link MapGridLoader}.</p>
 */
public final class BuildStubRomaniaMap {

    private BuildStubRomaniaMap() {}

    public static void main(String[] args) throws IOException {
        int size = 120;
        long seed = 20260602L;
        Path outPath = Paths.get(args.length > 0 ? args[0] : "reference-data/romania-map-stub.txt");

        BearCellType[][] grid = generate(size, seed);
        writeGrid(outPath, grid, size, seed);
        System.out.println("Wrote stub Romania grid (" + size + "x" + size + ") to " + outPath.toAbsolutePath());
    }

    static BearCellType[][] generate(int size, long seed) {
        Random rng = new Random(seed);
        BearCellType[][] grid = new BearCellType[size][size];

        // 1. Country footprint: rectangle with trimmed corners and a soft
        //    notch on the south to roughly evoke Romania's shape.
        boolean[][] inCountry = new boolean[size][size];
        double cx = (size - 1) / 2.0;
        double cy = (size - 1) / 2.0;
        double rx = size * 0.46;
        double ry = size * 0.40;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double nx = (x - cx) / rx;
                double ny = (y - cy) / ry;
                // Squircle-ish region trims the corners.
                inCountry[x][y] = (nx * nx * nx * nx + ny * ny * ny * ny) <= 1.0;
            }
        }

        // 2. Carpathian arc: a smooth "C" curve through the country.
        //    Distance to this curve drives MOUNTAIN / FOREST / FIELD zoning.
        List<double[]> ridgePoints = new ArrayList<>();
        int ridgeSamples = 200;
        for (int i = 0; i < ridgeSamples; i++) {
            double t = i / (double) (ridgeSamples - 1);
            // Parametric arc opening to the east.
            double angle = Math.PI * (0.25 + 0.5 * t);
            double rxRidge = size * 0.30;
            double ryRidge = size * 0.30;
            double xRidge = cx + rxRidge * Math.cos(angle);
            double yRidge = cy - ryRidge * Math.sin(angle) * 0.9;
            ridgePoints.add(new double[]{xRidge, yRidge});
        }

        double mountainDistance = size * 0.045;
        double forestDistance = size * 0.16;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                if (!inCountry[x][y]) {
                    grid[x][y] = BearCellType.NONE;
                    continue;
                }
                double d = minDistance(ridgePoints, x, y);
                double jitter = (rng.nextDouble() - 0.5) * size * 0.03;
                double dj = d + jitter;
                if (dj < mountainDistance) {
                    grid[x][y] = BearCellType.MOUNTAIN;
                } else if (dj < forestDistance) {
                    grid[x][y] = BearCellType.FOREST;
                } else {
                    grid[x][y] = BearCellType.FIELD;
                }
            }
        }

        // 3. Forest patches in the lowlands - small clusters scattered through
        //    the FIELD region so dispersal-friendly stepping stones exist.
        int extraForestPatches = (int) (size * size * 0.0015);
        for (int p = 0; p < extraForestPatches; p++) {
            int x = rng.nextInt(size);
            int y = rng.nextInt(size);
            if (grid[x][y] != BearCellType.FIELD) {
                continue;
            }
            int radius = 1 + rng.nextInt(3);
            stamp(grid, x, y, radius, BearCellType.FOREST,
                    new BearCellType[]{BearCellType.FIELD});
        }

        // 4. Villages: Poisson-disk-ish sample biased to lowland fields.
        int targetVillages = (int) (size * size * 0.002);
        List<int[]> villageCells = new ArrayList<>();
        int attempts = 0;
        while (villageCells.size() < targetVillages && attempts++ < targetVillages * 50) {
            int x = rng.nextInt(size);
            int y = rng.nextInt(size);
            if (grid[x][y] != BearCellType.FIELD) {
                continue;
            }
            boolean tooClose = false;
            for (int[] other : villageCells) {
                if (Math.abs(other[0] - x) + Math.abs(other[1] - y) < size * 0.08) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            stamp(grid, x, y, 1, BearCellType.VILLAGE,
                    new BearCellType[]{BearCellType.FIELD, BearCellType.FOREST});
            villageCells.add(new int[]{x, y});
        }

        // 5. Roads connecting nearby village pairs (Manhattan path).
        for (int i = 0; i < villageCells.size(); i++) {
            int[] a = villageCells.get(i);
            int[] nearest = null;
            int nearestDist = Integer.MAX_VALUE;
            for (int j = 0; j < villageCells.size(); j++) {
                if (i == j) continue;
                int[] b = villageCells.get(j);
                int d = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
                if (d < nearestDist && d < size * 0.18) {
                    nearestDist = d;
                    nearest = b;
                }
            }
            if (nearest != null) {
                paintRoad(grid, a[0], a[1], nearest[0], nearest[1]);
            }
        }

        return grid;
    }

    private static double minDistance(List<double[]> points, int x, int y) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] p : points) {
            double dx = p[0] - x;
            double dy = p[1] - y;
            double d2 = dx * dx + dy * dy;
            if (d2 < best) {
                best = d2;
            }
        }
        return Math.sqrt(best);
    }

    private static void stamp(BearCellType[][] grid, int cx, int cy, int radius,
                              BearCellType paint, BearCellType[] overwriteOnly) {
        int size = grid.length;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= size || y >= size) continue;
                if (dx * dx + dy * dy > radius * radius) continue;
                BearCellType current = grid[x][y];
                boolean allowed = false;
                for (BearCellType t : overwriteOnly) {
                    if (current == t) { allowed = true; break; }
                }
                if (allowed) {
                    grid[x][y] = paint;
                }
            }
        }
    }

    private static void paintRoad(BearCellType[][] grid, int x0, int y0, int x1, int y1) {
        int x = x0;
        int y = y0;
        while (x != x1 || y != y1) {
            if (grid[x][y] == BearCellType.FIELD || grid[x][y] == BearCellType.FOREST) {
                grid[x][y] = BearCellType.ROAD;
            }
            if (x != x1 && (y == y1 || Math.abs(x1 - x) > Math.abs(y1 - y))) {
                x += Integer.signum(x1 - x);
            } else {
                y += Integer.signum(y1 - y);
            }
        }
    }

    private static void writeGrid(Path path, BearCellType[][] grid, int size, long seed) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write("# Stub Romania-shaped habitat grid generated by BuildStubRomaniaMap.\n");
            w.write("# Replace with the output of tools/build_romania_map.py for a CLC-derived grid.\n");
            w.write("# seed=" + seed + "\n");
            w.write("# source=stub\n");
            w.write("width=" + size + "\n");
            w.write("height=" + size + "\n");
            for (int y = 0; y < size; y++) {
                StringBuilder row = new StringBuilder(size);
                for (int x = 0; x < size; x++) {
                    row.append(grid[x][y].mapSymbol());
                }
                w.write(row.toString());
                w.newLine();
            }
        }
    }
}
