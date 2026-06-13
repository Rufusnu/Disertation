package Bears.Experiments.Calibration;

import Bears.BearAgent.BearAgent;
import Bears.BearEnvironment.BearCell;
import Bears.BearEnvironment.BearCellType;
import Bears.BearEnvironment.BearEnvironment;
import Bears.BearEnvironment.BearSimulation;
import Bears.BearEnvironment.BearSnapshot;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.MapGridLoader;
import Bears.Experiments.CsvWriter;
import Bears.Experiments.RngSupport;
import MASInterface.Environment.Coords;
import MASInterface.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates or inspects a {@link BearSnapshot} - a reusable spin-up checkpoint.
 *
 * <p>{@code create} runs the founding population forward to
 * {@code -Dsnap.years} simulation years on the real map and writes the
 * equilibrated state to {@code -Dsnap.path}. {@code verify} loads a snapshot
 * and prints what it contains. Scenario runs then set
 * {@link Settings#SNAPSHOT_SOURCE} to that path to start from the spun-up state
 * instead of re-running the spin-up every time.</p>
 *
 * CLI flags:
 *   -Dsnap.mode=create|verify           (default create)
 *   -Dsnap.path=spin-up-snapshots/romania-y13.bsnp
 *   -Dsnap.years=13
 *   -Dsnap.initialBears=5800
 *   -Dsnap.mapLength=1000
 *   -Dsnap.mapSource=reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt
 *   -Dsnap.food=0.0045                  (FOOD_GROWN_PER_TICK during spin-up)
 *   -Dsnap.seed=20260609
 */
public final class SnapshotTool {

    public static void main(String[] args) throws IOException {
        String mode      = strProp("snap.mode", "create");
        Path path        = Path.of(strProp("snap.path", "spin-up-snapshots/romania-y13.bsnp"));

        if ("verify".equalsIgnoreCase(mode)) {
            BearSnapshot.Loaded loaded = BearSnapshot.load(path);
            double year = loaded.tick * Settings.ONE_TICK_IN_YEARS;
            System.out.printf(Locale.ROOT,
                    "Snapshot %s%n  bears=%d  mapLength=%d  savedAtTick=%d (year %.2f)  lastAgentId=%d%n",
                    path.toAbsolutePath(), loaded.agentsById.size(), loaded.state.mapLength(),
                    loaded.tick, year, loaded.lastAgentId);
            return;
        }

        if ("characterize".equalsIgnoreCase(mode)) {
            String outDir = strProp("snap.outDir", path.toString() + ".characterization");
            characterize(BearSnapshot.load(path), Path.of(outDir));
            return;
        }

        if ("run".equalsIgnoreCase(mode)) {
            // Smoke-test / preview an init path: start a sim from either a
            // snapshot or a balanced characterization, run a short while, and
            // report start/end population.
            double runYears = dblProp("snap.runYears", 1.0);
            String balanced = strProp("snap.balancedInit", "");
            Settings.BENCHMARK = false;
            Settings.VERBOSE = false;
            Settings.POPULATION_CAP = 0;
            Settings.SNAPSHOT_SAVE_PATH = "";
            Settings.SIMULATION_MAX_TICKS = (long) Math.ceil(runYears / Settings.ONE_TICK_IN_YEARS);
            RngSupport.setSeed(longProp("snap.seed", 20260609L));

            boolean useBalanced = !balanced.isBlank();
            int startCount;
            BearState initState;
            try {
                if (useBalanced) {
                    Settings.SNAPSHOT_SOURCE = "";
                    Settings.BALANCED_INIT_SOURCE = balanced;
                    Settings.AGENTS_NUMBER_SINGLE_EXECUTION = intProp("snap.initialBears", 5800);
                    Settings.MAP_LENGTH = intProp("snap.mapLength", 1000);
                    Settings.FOOD_GROWN_PER_TICK = dblProp("snap.food", 0.0045);
                    startCount = Settings.AGENTS_NUMBER_SINGLE_EXECUTION;
                    String mapSource = strProp("snap.mapSource",
                            "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt");
                    MapGridLoader.Grid grid = MapGridLoader.load(resolveDataPath(mapSource));
                    Settings.MAP_LENGTH = grid.mapLength();
                    initState = BearState.getInitStateFromGrid(grid);
                } else {
                    Settings.BALANCED_INIT_SOURCE = "";
                    Settings.SNAPSHOT_SOURCE = path.toString();
                    startCount = BearSnapshot.load(path).agentsById.size();
                    initState = BearState.getInitState(2); // dummy; ignored when SNAPSHOT_SOURCE set
                }
                BearEnvironment env = new BearEnvironment();
                BearSimulation sim = new BearSimulation(env);
                sim.startNoPrompt(initState);
                System.out.printf(Locale.ROOT,
                        "Ran %.2f years from %s: startPop=%d endPop=%d%n",
                        runYears, useBalanced ? "balanced init" : "snapshot",
                        startCount, sim.agentsById().size());
            } finally {
                RngSupport.clearSeed();
                Settings.SNAPSHOT_SOURCE = "";
                Settings.BALANCED_INIT_SOURCE = "";
            }
            return;
        }

        int initialBears = intProp("snap.initialBears", 5800);
        double years     = dblProp("snap.years", 13.0);
        int mapLength    = intProp("snap.mapLength", 1000);
        String mapSource = strProp("snap.mapSource",
                "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt");
        double food      = dblProp("snap.food", 0.0045);
        long seed        = longProp("snap.seed", 20260609L);

        // Run a normal sim, but ask it to save and stop once it reaches `years`.
        Settings.AGENTS_NUMBER_SINGLE_EXECUTION = initialBears;
        Settings.MAP_LENGTH = mapLength;
        Settings.FOOD_GROWN_PER_TICK = food;
        Settings.BENCHMARK = false;
        Settings.VERBOSE = false;
        Settings.POPULATION_CAP = 0;
        Settings.SNAPSHOT_SOURCE = "";                 // create from founders, not from a snapshot
        Settings.SNAPSHOT_SAVE_PATH = path.toString();
        Settings.SNAPSHOT_SAVE_AT_YEAR = years;
        // Give the loop a couple of years of headroom beyond the save point.
        Settings.SIMULATION_MAX_TICKS = (long) Math.ceil((years + 2.0) / Settings.ONE_TICK_IN_YEARS);

        System.out.printf(Locale.ROOT,
                "Creating snapshot: spin up %d bears for %.0f years on %s (food=%s, seed=%d)%n",
                initialBears, years, mapSource, fmt(food), seed);
        long t0 = System.currentTimeMillis();

        RngSupport.setSeed(seed);
        try {
            BearEnvironment env = new BearEnvironment();
            BearSimulation sim = new BearSimulation(env);

            BearState initState;
            if (mapSource != null && !mapSource.isBlank()) {
                Path gridPath = resolveDataPath(mapSource);
                MapGridLoader.Grid grid = MapGridLoader.load(gridPath);
                Settings.MAP_LENGTH = grid.mapLength();
                initState = BearState.getInitStateFromGrid(grid);
            } else {
                initState = BearState.getInitState(mapLength);
            }
            sim.startNoPrompt(initState);
        } finally {
            RngSupport.clearSeed();
            Settings.SNAPSHOT_SAVE_PATH = "";
        }

        System.out.printf(Locale.ROOT, "Done in %.1fs. Snapshot at %s%n",
                (System.currentTimeMillis() - t0) / 1000.0, path.toAbsolutePath());
        System.out.println("Use it by setting Settings.SNAPSHOT_SOURCE=" + path
                + " (or -Dsnap.source) before a scenario run.");
    }

    /**
     * Loads a (stable-state) snapshot and writes the distributions a balanced
     * initializer would need to reproduce: age and satiety histograms, sex
     * ratio, founding reproductive-state fractions, bears per habitat, and the
     * per-habitat food-stock fill level. Console prints a summary; CSVs land in
     * {@code outDir} for downstream use.
     */
    private static void characterize(BearSnapshot.Loaded loaded, Path outDir) throws IOException {
        BearState state = loaded.state;
        Map<Integer, BearAgent> agents = loaded.agentsById;
        int n = agents.size();
        double year = loaded.tick * Settings.ONE_TICK_IN_YEARS;

        // --- Per-bear aggregates ---
        int males = 0, females = 0;
        int reproFemales = 0, pregnant = 0, onCooldown = 0, available = 0;
        double ageSum = 0, satSum = 0;
        long[] ageHist = new long[32];                 // 1-year bins, 0..31+
        long[] satHist = new long[10];                 // 0.1 bins, 0..1
        Map<BearCellType, long[]> bearsByHabitat = new EnumMap<>(BearCellType.class);
        for (BearCellType t : BearCellType.values()) bearsByHabitat.put(t, new long[1]);

        for (BearAgent a : agents.values()) {
            if (a.getGender() == BearAgent.Gender.MALE) males++; else females++;
            ageSum += a.getAge();
            satSum += a.getSatiety();
            ageHist[Math.min(ageHist.length - 1, (int) Math.floor(Math.max(0, a.getAge())))]++;
            satHist[Math.min(satHist.length - 1, Math.max(0, (int) (a.getSatiety() * 10)))]++;

            boolean reproFemale = a.getGender() == BearAgent.Gender.FEMALE
                    && a.getAge() >= Settings.BEAR_MIN_REPRODUCTION_AGE;
            if (reproFemale) {
                reproFemales++;
                if (a.isPregnant()) pregnant++;
                else if (a.getReproductionCooldownYearsRemaining() > 0) onCooldown++;
                else available++;
            }

            Coords c = state.getAgentCoords(a.getId());
            BearCell cell = state.getBearCell(c.x, c.y);
            BearCellType type = cell != null ? cell.bearCellType() : BearCellType.NONE;
            bearsByHabitat.get(type)[0]++;
        }

        // --- Per-cell food-stock by habitat ---
        int mapLength = state.mapLength();
        Map<BearCellType, double[]> cellAgg = new EnumMap<>(BearCellType.class); // {count, foodSum}
        for (BearCellType t : BearCellType.values()) cellAgg.put(t, new double[2]);
        for (int x = 0; x < mapLength; x++) {
            for (int y = 0; y < mapLength; y++) {
                BearCell cell = state.getBearCell(x, y);
                if (cell == null) continue;
                BearCellType type = cell.bearCellType();
                double[] agg = cellAgg.get(type);
                agg[0] += 1;
                if (type != BearCellType.NONE) agg[1] += cell.food();
            }
        }

        // --- Write CSVs ---
        List<List<String>> ageRows = new ArrayList<>();
        for (int b = 0; b < ageHist.length; b++) {
            ageRows.add(Arrays.asList(b + "-" + (b + 1), String.valueOf(ageHist[b]),
                    fmt(n > 0 ? (double) ageHist[b] / n : 0)));
        }
        CsvWriter.writeRows(outDir.resolve("age_histogram.csv"),
                Arrays.asList("ageBinYears", "count", "fraction"), ageRows);

        List<List<String>> satRows = new ArrayList<>();
        for (int b = 0; b < satHist.length; b++) {
            satRows.add(Arrays.asList(fmt(b / 10.0) + "-" + fmt((b + 1) / 10.0),
                    String.valueOf(satHist[b]), fmt(n > 0 ? (double) satHist[b] / n : 0)));
        }
        CsvWriter.writeRows(outDir.resolve("satiety_histogram.csv"),
                Arrays.asList("satietyBin", "count", "fraction"), satRows);

        List<List<String>> habRows = new ArrayList<>();
        for (BearCellType t : BearCellType.values()) {
            long bears = bearsByHabitat.get(t)[0];
            double cells = cellAgg.get(t)[0];
            double meanFood = cells > 0 && t != BearCellType.NONE ? cellAgg.get(t)[1] / cells : 0;
            double cap = t.maxFood();
            double fill = (cap > 0) ? meanFood / cap : 0;
            habRows.add(Arrays.asList(
                    t.name(), String.valueOf((long) cells), String.valueOf(bears),
                    fmt(cells > 0 ? bears / cells : 0),
                    fmt(meanFood), fmt(cap), fmt(fill)));
        }
        CsvWriter.writeRows(outDir.resolve("habitat.csv"),
                Arrays.asList("habitat", "cells", "bears", "bearsPerCell",
                        "meanFood", "maxFood", "fillFraction"), habRows);

        // Scalar fractions the balanced initializer reads back.
        List<List<String>> summaryRows = new ArrayList<>();
        summaryRows.add(Arrays.asList("totalBears", String.valueOf(n)));
        summaryRows.add(Arrays.asList("femaleFraction", fmt(n > 0 ? (double) females / n : 0)));
        summaryRows.add(Arrays.asList("maleFraction", fmt(n > 0 ? (double) males / n : 0)));
        summaryRows.add(Arrays.asList("meanAge", fmt(n > 0 ? ageSum / n : 0)));
        summaryRows.add(Arrays.asList("meanSatiety", fmt(n > 0 ? satSum / n : 0)));
        summaryRows.add(Arrays.asList("reproFemales", String.valueOf(reproFemales)));
        summaryRows.add(Arrays.asList("pregnantFraction", fmt(reproFemales > 0 ? (double) pregnant / reproFemales : 0)));
        summaryRows.add(Arrays.asList("cooldownFraction", fmt(reproFemales > 0 ? (double) onCooldown / reproFemales : 0)));
        summaryRows.add(Arrays.asList("availableFraction", fmt(reproFemales > 0 ? (double) available / reproFemales : 0)));
        CsvWriter.writeRows(outDir.resolve("summary.csv"),
                Arrays.asList("metric", "value"), summaryRows);

        // --- Console summary ---
        System.out.printf(Locale.ROOT, "=== Snapshot characterization (year %.1f, %d bears) ===%n", year, n);
        System.out.printf(Locale.ROOT, "Sex ratio: %.1f%% female, %.1f%% male%n",
                pct(females, n), pct(males, n));
        System.out.printf(Locale.ROOT, "Age: mean %.2f yr (founding generator currently targets mean %.1f)%n",
                n > 0 ? ageSum / n : 0, Settings.BEAR_FOUNDING_AGE_MEAN);
        System.out.printf(Locale.ROOT, "Satiety: mean %.3f (generator currently uniform [%.2f, %.2f])%n",
                n > 0 ? satSum / n : 0, Settings.BEAR_INITIAL_SATIETY_MIN, Settings.BEAR_INITIAL_SATIETY_MAX);
        System.out.printf(Locale.ROOT,
                "Reproductive females: %d  ->  pregnant %.1f%%, on-cooldown %.1f%%, available %.1f%% "
                        + "(generator currently seeds %.0f%% pregnant)%n",
                reproFemales, pct(pregnant, reproFemales), pct(onCooldown, reproFemales),
                pct(available, reproFemales), Settings.BEAR_INITIAL_PREGNANT_FRACTION * 100);
        System.out.println("Bears per habitat (generator currently forest-only):");
        for (BearCellType t : BearCellType.values()) {
            long bears = bearsByHabitat.get(t)[0];
            if (bears == 0 && t == BearCellType.NONE) continue;
            double cells = cellAgg.get(t)[0];
            double fill = t.maxFood() > 0 && cells > 0 ? (cellAgg.get(t)[1] / cells) / t.maxFood() : 0;
            System.out.printf(Locale.ROOT, "  %-8s bears=%-6d (%.1f%%)  foodFill=%.0f%% of cap%n",
                    t.name(), bears, pct((int) bears, n), fill * 100);
        }
        System.out.println("CSVs written to: " + outDir.toAbsolutePath());
    }

    private static double pct(int part, int whole) {
        return whole > 0 ? 100.0 * part / whole : 0.0;
    }

    private static Path resolveDataPath(String relativeOrAbsolute) {
        Path direct = Path.of(relativeOrAbsolute);
        if (direct.isAbsolute() || java.nio.file.Files.exists(direct)) return direct;
        Path probe = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 6; depth++) {
            Path candidate = probe.resolve(relativeOrAbsolute);
            if (java.nio.file.Files.exists(candidate)) return candidate;
            Path parent = probe.getParent();
            if (parent == null) break;
            probe = parent;
        }
        return direct;
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.6g", d);
    }
    private static int intProp(String name, int def) {
        String s = System.getProperty(name);
        return (s == null || s.isBlank()) ? def : Integer.parseInt(s.trim());
    }
    private static long longProp(String name, long def) {
        String s = System.getProperty(name);
        return (s == null || s.isBlank()) ? def : Long.parseLong(s.trim());
    }
    private static double dblProp(String name, double def) {
        String s = System.getProperty(name);
        return (s == null || s.isBlank()) ? def : Double.parseDouble(s.trim());
    }
    private static String strProp(String name, String def) {
        String s = System.getProperty(name);
        return (s == null) ? def : s;
    }

    private SnapshotTool() {}
}
