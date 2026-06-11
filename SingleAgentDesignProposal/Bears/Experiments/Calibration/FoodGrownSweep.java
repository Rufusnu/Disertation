package Bears.Experiments.Calibration;

import Bears.BearEnvironment.BearEnvironment;
import Bears.BearEnvironment.BearSimulation;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.MapGridLoader;
import Bears.Experiments.CsvWriter;
import Bears.Experiments.RngSupport;
import Bears.Experiments.RunConfig;
import Bears.Experiments.RunMetricsRecorder;
import Bears.Experiments.TickMetrics;
import MASInterface.Settings;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One-dimensional sweep over {@link Settings#FOOD_GROWN_PER_TICK} (food
 * regrowth rate, the dominant lever on the map's carrying capacity), holding
 * the calibrated demographic parameters fixed.
 *
 * <p>Purpose: the demographic calibration produced a population whose
 * equilibrium sits far above the 5800-bear target, so the population merely
 * passes through 5800 on its way up. This driver finds the food-regrowth value
 * at which equilibrium lands on ~5800 and the trajectory is flat over the
 * comparison window, so it can serve as a stable baseline.</p>
 *
 * <p>For each swept value it runs the full 1000x1000 simulation for
 * {@code sweep.years} simulation years from {@code sweep.initialBears} bears,
 * then reports the final population, the mean and relative slope over the last
 * {@code sweep.tailYears} years, and the transient dip. Writes:</p>
 *
 * <pre>
 * food-sweep-output/sweep-&lt;ts&gt;/
 *     summary.csv              - one row per food value (the decision table)
 *     trajectory-&lt;value&gt;.csv    - per-year population for plotting
 * </pre>
 *
 * CLI flags (all optional):
 *   -Dsweep.values=0.005,0.004,0.003,0.0025,0.002,0.0015
 *   -Dsweep.initialBears=5800
 *   -Dsweep.years=35
 *   -Dsweep.tailYears=8
 *   -Dsweep.replicates=1
 *   -Dsweep.mapLength=1000
 *   -Dsweep.mapSource=reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt
 *   -Dsweep.seed=20260609
 *   -Dsweep.sampleEveryTicks=730          (~1 sample/month; keeps memory light)
 *   -Dsweep.popCap=30000                  (abort a runaway replicate; 0 = no cap)
 *   -Dsweep.targetPop=5800                (used only to rank rows by closeness)
 *   -Dsweep.output=food-sweep-output
 */
public final class FoodGrownSweep {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) throws IOException {
        double[] values   = dblList("sweep.values", new double[]{0.005, 0.004, 0.003, 0.0025, 0.002, 0.0015});
        int initialBears  = intProp("sweep.initialBears", 5800);
        double years      = dblProp("sweep.years", 35.0);
        double tailYears  = dblProp("sweep.tailYears", 8.0);
        int replicates    = intProp("sweep.replicates", 1);
        int mapLength     = intProp("sweep.mapLength", 1000);
        String mapSource  = strProp("sweep.mapSource",
                "reference-data/generated-maps/U2018_CLC2018_V2020_20u1-1000x1000.txt");
        long seed         = longProp("sweep.seed", 20260609L);
        int sampleEvery   = intProp("sweep.sampleEveryTicks", 730);
        long popCap       = longProp("sweep.popCap", 30000L);
        double targetPop  = dblProp("sweep.targetPop", 5800.0);
        Path outputRoot   = Paths.get(strProp("sweep.output", "food-sweep-output"));

        String stamp = LocalDateTime.now().format(TS);
        Path outDir = outputRoot.resolve("sweep-" + stamp);
        Path summaryCsv = outDir.resolve("summary.csv");

        long maxTicks = (long) Math.ceil(years / Settings.ONE_TICK_IN_YEARS);

        System.out.println("=== FOOD_GROWN_PER_TICK sweep ===");
        System.out.printf(Locale.ROOT,
                "values=%s  initialBears=%d  years=%.0f  tailYears=%.0f  replicates=%d  map=%s%n",
                Arrays.toString(values), initialBears, years, tailYears, replicates, mapSource);
        System.out.println("Output: " + outDir.toAbsolutePath());
        System.out.println();

        List<String> summaryHeader = Arrays.asList(
                "foodGrownPerTick", "replicates",
                "startPop", "finalPop", "tailMeanPop", "tailSlopeRelPerYear",
                "minPop", "minYear", "peakPop", "peakYear", "finalMeanSatiety",
                "absDiffFromTarget", "elapsedMillis");

        List<double[]> rankRows = new ArrayList<>(); // {value, finalPop, slope, absDiff}

        for (double food : values) {
            long t0 = System.currentTimeMillis();

            // Per-year population averaged across replicates.
            TreeMap<Integer, double[]> yearAccum = new TreeMap<>(); // year -> {sumPop, count}
            double sumFinal = 0, sumTailMean = 0, sumTailSlope = 0;
            double sumMin = 0, sumMinYear = 0, sumPeak = 0, sumPeakYear = 0, sumFinalSat = 0;
            double startPop = 0;

            for (int r = 0; r < replicates; r++) {
                long repSeed = mix(seed ^ Double.doubleToLongBits(food), r);
                ReplicateTrace tr = runOne(food, initialBears, mapLength, mapSource,
                        maxTicks, sampleEvery, popCap, tailYears, repSeed);

                startPop = tr.startPop;
                sumFinal     += tr.finalPop;
                sumTailMean  += tr.tailMeanPop;
                sumTailSlope += tr.tailSlopeRel;
                sumMin       += tr.minPop;
                sumMinYear   += tr.minYear;
                sumPeak      += tr.peakPop;
                sumPeakYear  += tr.peakYear;
                sumFinalSat  += tr.finalMeanSatiety;

                for (Map.Entry<Integer, Double> e : tr.annualPop.entrySet()) {
                    double[] acc = yearAccum.computeIfAbsent(e.getKey(), k -> new double[2]);
                    acc[0] += e.getValue();
                    acc[1] += 1;
                }
            }

            double inv = 1.0 / Math.max(1, replicates);
            double finalPop    = sumFinal * inv;
            double tailMeanPop = sumTailMean * inv;
            double tailSlope   = sumTailSlope * inv;
            double minPop      = sumMin * inv;
            double minYear     = sumMinYear * inv;
            double peakPop     = sumPeak * inv;
            double peakYear    = sumPeakYear * inv;
            double finalSat    = sumFinalSat * inv;
            double absDiff     = Math.abs(finalPop - targetPop);
            long elapsed       = System.currentTimeMillis() - t0;

            // Write the averaged trajectory for this value.
            Path trajCsv = outDir.resolve("trajectory-" + fmtTag(food) + ".csv");
            List<String> trajHeader = Arrays.asList("year", "meanPopulation");
            List<List<String>> trajRows = new ArrayList<>();
            for (Map.Entry<Integer, double[]> e : yearAccum.entrySet()) {
                double meanPop = e.getValue()[1] > 0 ? e.getValue()[0] / e.getValue()[1] : 0;
                trajRows.add(Arrays.asList(String.valueOf(e.getKey()), fmt(meanPop)));
            }
            CsvWriter.writeRows(trajCsv, trajHeader, trajRows);

            CsvWriter.appendRow(summaryCsv, summaryHeader, Arrays.asList(
                    fmt(food), String.valueOf(replicates),
                    fmt(startPop), fmt(finalPop), fmt(tailMeanPop), fmt(tailSlope),
                    fmt(minPop), fmt(minYear), fmt(peakPop), fmt(peakYear), fmt(finalSat),
                    fmt(absDiff), String.valueOf(elapsed)));

            rankRows.add(new double[]{food, finalPop, tailSlope, absDiff});

            System.out.printf(Locale.ROOT,
                    "food=%-9s finalPop=%-7.0f tailMean=%-7.0f slope=%+.4f/yr  dip=%.0f@y%.0f  peak=%.0f@y%.0f  |Δtarget|=%.0f  (%.1fs)%n",
                    fmt(food), finalPop, tailMeanPop, tailSlope, minPop, minYear, peakPop, peakYear,
                    absDiff, elapsed / 1000.0);
        }

        // Recommend the value whose trajectory is closest to flat-at-target:
        // small |finalPop - target| AND small |slope|.
        double bestScore = Double.MAX_VALUE;
        double[] best = null;
        for (double[] row : rankRows) {
            double relDiff = row[3] / Math.max(1.0, targetPop);   // closeness to target
            double slopeMag = Math.abs(row[2]);                    // flatness
            double score = relDiff + slopeMag;                     // both ~unitless, comparable
            if (score < bestScore) {
                bestScore = score;
                best = row;
            }
        }

        System.out.println();
        System.out.println("================ Food sweep done ================");
        if (best != null) {
            System.out.printf(Locale.ROOT,
                    "Closest to flat-at-%.0f: FOOD_GROWN_PER_TICK=%s -> finalPop=%.0f, slope=%+.4f/yr%n",
                    targetPop, fmt(best[0]), best[1], best[2]);
            System.out.println("If the bracket does not straddle the target, rerun with values around "
                    + fmt(best[0]) + ".");
        }
        System.out.println("Summary: " + summaryCsv.toAbsolutePath());
    }

    // ------------------------------------------------------------------

    private static final class ReplicateTrace {
        final TreeMap<Integer, Double> annualPop = new TreeMap<>();
        double startPop;
        double finalPop;
        double tailMeanPop;
        double tailSlopeRel;
        double minPop;
        double minYear;
        double peakPop;
        double peakYear;
        double finalMeanSatiety;
    }

    private static ReplicateTrace runOne(double foodGrownPerTick, int initialBears, int mapLength,
                                         String mapSource, long maxTicks, int sampleEvery,
                                         long popCap, double tailYears, long seed) {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("AGENTS_NUMBER_SINGLE_EXECUTION", Settings.AGENTS_NUMBER_SINGLE_EXECUTION);
        previous.put("MAP_LENGTH", Settings.MAP_LENGTH);
        previous.put("SIMULATION_MAX_TICKS", Settings.SIMULATION_MAX_TICKS);
        previous.put("POPULATION_CAP", Settings.POPULATION_CAP);
        previous.put("BENCHMARK", Settings.BENCHMARK);
        previous.put("VERBOSE", Settings.VERBOSE);
        previous.put("FOOD_GROWN_PER_TICK", Settings.FOOD_GROWN_PER_TICK);

        Settings.AGENTS_NUMBER_SINGLE_EXECUTION = initialBears;
        Settings.MAP_LENGTH = mapLength;
        Settings.SIMULATION_MAX_TICKS = maxTicks;
        Settings.POPULATION_CAP = popCap;
        Settings.BENCHMARK = false;
        Settings.VERBOSE = false;
        Settings.FOOD_GROWN_PER_TICK = foodGrownPerTick;

        PrintStream originalOut = System.out;
        List<TickMetrics> samples;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            RngSupport.setSeed(seed);
            try {
                BearEnvironment env = new BearEnvironment();
                BearSimulation sim = new BearSimulation(env);
                RunConfig rc = new RunConfig("food-sweep", "food-sweep", 0, seed,
                        maxTicks, sampleEvery, new LinkedHashMap<>());
                RunMetricsRecorder recorder = new RunMetricsRecorder(rc);
                sim.setMetricsRecorder(recorder);

                BearState initState;
                if (mapSource != null && !mapSource.isBlank()) {
                    Path gridPath = resolveDataPath(mapSource);
                    try {
                        MapGridLoader.Grid grid = MapGridLoader.load(gridPath);
                        Settings.MAP_LENGTH = grid.mapLength();
                        initState = BearState.getInitStateFromGrid(grid);
                    } catch (IOException ioe) {
                        throw new RuntimeException("Failed to load grid " + gridPath + ": " + ioe.getMessage(), ioe);
                    }
                } else {
                    initState = BearState.getInitState(mapLength);
                }

                sim.startNoPrompt(initState);
                samples = recorder.samples();
            } finally {
                RngSupport.clearSeed();
            }
        } finally {
            System.setOut(originalOut);
            for (Map.Entry<String, Object> e : previous.entrySet()) {
                try {
                    java.lang.reflect.Field f = Settings.class.getField(e.getKey());
                    f.set(null, e.getValue());
                } catch (NoSuchFieldException | IllegalAccessException ignored) {
                    // best effort
                }
            }
        }

        return summarise(samples, maxTicks, tailYears);
    }

    private static ReplicateTrace summarise(List<TickMetrics> samples, long maxTicks, double tailYears) {
        ReplicateTrace tr = new ReplicateTrace();
        if (samples.isEmpty()) {
            return tr;
        }

        double evalEndYears = maxTicks * Settings.ONE_TICK_IN_YEARS;
        double tailStart = Math.max(0.0, evalEndYears - tailYears);

        // End-of-year population (last sample whose floor(year)==y wins).
        TreeMap<Integer, Double> annual = new TreeMap<>();
        TreeMap<Integer, Double> annualSat = new TreeMap<>();
        double peakPop = 0, peakYear = 0;
        double minPop = Double.MAX_VALUE, minYear = 0;

        // Tail linear regression accumulators.
        double tX = 0, tY = 0, tXX = 0, tXY = 0, tSum = 0;
        int tN = 0;

        for (TickMetrics s : samples) {
            int year = (int) Math.floor(s.simulationYear);
            annual.put(year, (double) s.population);
            annualSat.put(year, s.meanSatiety);

            if (s.population > peakPop) { peakPop = s.population; peakYear = s.simulationYear; }
            if (s.population < minPop)  { minPop = s.population;  minYear = s.simulationYear; }

            if (s.simulationYear >= tailStart) {
                double x = s.simulationYear, y = s.population;
                tN++; tX += x; tY += y; tXX += x * x; tXY += x * y; tSum += y;
            }
        }

        tr.annualPop.putAll(annual);
        tr.startPop = annual.firstEntry().getValue();
        tr.finalPop = annual.lastEntry().getValue();
        tr.finalMeanSatiety = annualSat.lastEntry().getValue();
        tr.peakPop = peakPop;
        tr.peakYear = peakYear;
        tr.minPop = (minPop == Double.MAX_VALUE) ? 0 : minPop;
        tr.minYear = minYear;

        double tailMean = tN > 0 ? tSum / tN : tr.finalPop;
        tr.tailMeanPop = tailMean;
        double slope = 0;
        if (tN >= 2) {
            double denom = (tN * tXX) - (tX * tX);
            if (Math.abs(denom) > 1e-12) {
                slope = ((tN * tXY) - (tX * tY)) / denom;
            }
        }
        tr.tailSlopeRel = tailMean > 0 ? slope / tailMean : 0;
        return tr;
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

    private static long mix(long seed, long key) {
        long z = seed + (key + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String fmt(double d) {
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Inf" : "-Inf";
        return String.format(Locale.ROOT, "%.6g", d);
    }

    /** File-name-safe tag for a food value, e.g. 0.0025 -> "0p0025000". */
    private static String fmtTag(double d) {
        return String.format(Locale.ROOT, "%.7f", d).replace('.', 'p');
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
    private static double[] dblList(String name, double[] def) {
        String s = System.getProperty(name);
        if (s == null || s.isBlank()) return def;
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

    private FoodGrownSweep() {}
}
