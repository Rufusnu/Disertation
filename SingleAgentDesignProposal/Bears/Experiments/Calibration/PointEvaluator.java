package Bears.Experiments.Calibration;

import Bears.BearEnvironment.BearEnvironment;
import Bears.BearEnvironment.BearSimulation;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.DeathCause;
import Bears.BearEnvironment.MapGridLoader;
import Bears.Experiments.RngSupport;
import Bears.Experiments.RunConfig;
import Bears.Experiments.RunMetricsRecorder;
import Bears.Experiments.TickMetrics;
import MASInterface.Settings;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a candidate {@link CandidatePoint} for {@link Config#replicates}
 * replicates and turns each run into a {@link StationarityTargets.ReplicateSummary}
 * over the [burnIn, evalEnd] window.
 *
 * The evaluator mutates the static {@link Settings} for the duration of each
 * run and restores the previous values in a {@code finally} block. Stdout from
 * the simulation is suppressed so calibration logs stay readable.
 */
public final class PointEvaluator {

    public static final class Config {
        public final int replicates;
        public final int initialBearCount;
        public final int mapLength;          // used when mapSource is null/blank
        public final String mapSource;       // optional path to a habitat grid
        public final double burnInYears;
        public final double evalEndYears;
        public final long baseSeed;
        public final double kTarget;
        public final boolean verbose;

        public Config(int replicates, int initialBearCount, int mapLength, String mapSource,
                      double burnInYears, double evalEndYears, long baseSeed,
                      double kTarget, boolean verbose) {
            this.replicates = replicates;
            this.initialBearCount = initialBearCount;
            this.mapLength = mapLength;
            this.mapSource = mapSource;
            this.burnInYears = burnInYears;
            this.evalEndYears = evalEndYears;
            this.baseSeed = baseSeed;
            this.kTarget = kTarget;
            this.verbose = verbose;
        }
    }

    private final Config config;
    private final StationarityTargets targets;

    public PointEvaluator(Config config) {
        this.config = config;
        this.targets = new StationarityTargets(config.kTarget, config.burnInYears, config.evalEndYears);
    }

    public EvaluationResult evaluate(CandidatePoint point) {
        long t0 = System.currentTimeMillis();
        long maxTicks = (long) Math.ceil(config.evalEndYears / Settings.ONE_TICK_IN_YEARS);

        Map<String, Object> previous = applyAll(point, maxTicks);
        List<StationarityTargets.ReplicateSummary> summaries = new ArrayList<>(config.replicates);
        List<StationarityTargets.Score> scores = new ArrayList<>(config.replicates);
        PrintStream originalOut = System.out;
        try {
            if (!config.verbose) System.setOut(new PrintStream(OutputStream.nullOutputStream()));

            for (int r = 0; r < config.replicates; r++) {
                long seed = deriveSeed(config.baseSeed, point, r);
                RngSupport.setSeed(seed);
                try {
                    List<TickMetrics> samples = runOne();
                    StationarityTargets.ReplicateSummary summary = summarise(samples);
                    summaries.add(summary);
                    scores.add(targets.score(summary));
                } finally {
                    RngSupport.clearSeed();
                }
            }
        } finally {
            System.setOut(originalOut);
            restoreAll(previous);
        }

        double meanLoss = 0;
        for (StationarityTargets.Score s : scores) meanLoss += s.total;
        meanLoss /= Math.max(1, scores.size());

        long elapsed = System.currentTimeMillis() - t0;
        return new EvaluationResult(point, meanLoss, scores, summaries, elapsed);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private List<TickMetrics> runOne() {
        BearEnvironment env = new BearEnvironment();
        BearSimulation sim = new BearSimulation(env);
        RunConfig rc = new RunConfig("calib", "calib", 0, 0L,
                (long) Math.ceil(config.evalEndYears / Settings.ONE_TICK_IN_YEARS),
                1, new LinkedHashMap<>());
        RunMetricsRecorder recorder = new RunMetricsRecorder(rc);
        sim.setMetricsRecorder(recorder);

        BearState initState;
        if (config.mapSource != null && !config.mapSource.isBlank()) {
            Path gridPath = resolveDataPath(config.mapSource);
            try {
                MapGridLoader.Grid grid = MapGridLoader.load(gridPath);
                Settings.MAP_LENGTH = grid.mapLength();
                initState = BearState.getInitStateFromGrid(grid);
            } catch (java.io.IOException ioe) {
                throw new RuntimeException("Failed to load grid " + gridPath + ": " + ioe.getMessage(), ioe);
            }
        } else {
            initState = BearState.getInitState(config.mapLength);
        }

        sim.startNoPrompt(initState);
        return recorder.samples();
    }

    private StationarityTargets.ReplicateSummary summarise(List<TickMetrics> samples) {
        if (samples.isEmpty()) {
            return new StationarityTargets.ReplicateSummary(0, 0, true, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
        }

        double burnIn = config.burnInYears;
        double evalEnd = config.evalEndYears;

        // Population stats over the window.
        double popSum = 0;
        double popSqSum = 0;
        int popN = 0;
        double satSum = 0;
        int satN = 0;
        long birthSum = 0;
        long deathOld = 0;
        long deathStarv = 0;
        long deathDanger = 0;
        boolean extinct = false;
        Double startPopInWindow = null;
        double endPopInWindow = 0;
        double peakPopInWindow = 0;

        // Tail-window linear trend inputs.
        double tailStartYear = Math.max(burnIn, evalEnd - 8.0);
        double tailX = 0, tailY = 0, tailXX = 0, tailXY = 0, tailPopSum = 0;
        int tailN = 0;

        for (TickMetrics s : samples) {
            if (s.population == 0 && s.simulationYear >= burnIn) extinct = true;
            if (s.simulationYear < burnIn || s.simulationYear > evalEnd) continue;
            popSum += s.population;
            popSqSum += (double) s.population * s.population;
            popN++;
            if (startPopInWindow == null) startPopInWindow = (double) s.population;
            endPopInWindow = s.population;
            if (s.population > peakPopInWindow) peakPopInWindow = s.population;
            if (s.population > 0) {
                satSum += s.meanSatiety;
                satN++;
            }

            if (s.simulationYear >= tailStartYear) {
                double x = s.simulationYear;
                double y = s.population;
                tailN++;
                tailX += x;
                tailY += y;
                tailXX += x * x;
                tailXY += x * y;
                tailPopSum += y;
            }
            birthSum += s.births;
            deathOld += s.deathsByCause.getOrDefault(DeathCause.OLD_AGE, 0);
            deathStarv += s.deathsByCause.getOrDefault(DeathCause.STARVATION, 0);
            deathDanger += s.deathsByCause.getOrDefault(DeathCause.DANGER, 0);
        }

        // Also flag extinction if the final population is 0 even when the
        // window-aware loop above missed it (very short evaluation window).
        if (samples.get(samples.size() - 1).population == 0) extinct = true;

        double meanPop = popN > 0 ? popSum / popN : 0;
        double varPop = popN > 0 ? (popSqSum / popN) - (meanPop * meanPop) : 0;
        if (varPop < 0) varPop = 0;
        double cv = meanPop > 0 ? Math.sqrt(varPop) / meanPop : 0;
        double years = Math.max(1e-6, evalEnd - burnIn);
        double perCapitaBirth = meanPop > 0 ? (birthSum / meanPop / years) : 0;
        double meanSat = satN > 0 ? satSum / satN : 0;
        long totalDeaths = deathOld + deathStarv + deathDanger;
        double starvShare = totalDeaths > 0 ? (double) deathStarv / totalDeaths : 0;
        double oldShare = totalDeaths > 0 ? (double) deathOld / totalDeaths : 0;
        double startPop = startPopInWindow != null ? startPopInWindow : samples.get(0).population;
        double endPop = popN > 0 ? endPopInWindow : samples.get(samples.size() - 1).population;
        double endToMean = meanPop > 0 ? endPop / meanPop : 0;
        double endToStart = startPop > 0 ? endPop / startPop : 0;

        double tailSlope = 0;
        if (tailN >= 2) {
            double denom = (tailN * tailXX) - (tailX * tailX);
            if (Math.abs(denom) > 1e-12) {
                tailSlope = ((tailN * tailXY) - (tailX * tailY)) / denom;
            }
        }
        double tailMeanPop = tailN > 0 ? tailPopSum / tailN : meanPop;
        double tailSlopeRel = tailMeanPop > 0 ? tailSlope / tailMeanPop : 0;
        double maxDrawdown = peakPopInWindow > 0 ? Math.max(0, (peakPopInWindow - endPop) / peakPopInWindow) : 0;

        return new StationarityTargets.ReplicateSummary(
            meanPop, cv, extinct, perCapitaBirth, meanSat, starvShare, oldShare,
                startPop, endPop, endToMean, endToStart, tailSlopeRel, maxDrawdown);
    }

    private Map<String, Object> applyAll(CandidatePoint point, long maxTicks) {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("AGENTS_NUMBER_SINGLE_EXECUTION", Settings.AGENTS_NUMBER_SINGLE_EXECUTION);
        previous.put("MAP_LENGTH", Settings.MAP_LENGTH);
        previous.put("SIMULATION_MAX_TICKS", Settings.SIMULATION_MAX_TICKS);
        previous.put("POPULATION_CAP", Settings.POPULATION_CAP);
        previous.put("BENCHMARK", Settings.BENCHMARK);
        previous.put("VERBOSE", Settings.VERBOSE);

        Settings.AGENTS_NUMBER_SINGLE_EXECUTION = config.initialBearCount;
        Settings.MAP_LENGTH = config.mapLength;
        Settings.SIMULATION_MAX_TICKS = maxTicks;
        Settings.POPULATION_CAP = Math.max(50L, (long) Math.ceil(config.kTarget * 3));
        Settings.BENCHMARK = false;
        Settings.VERBOSE = false;

        for (Map.Entry<String, Double> e : point.values().entrySet()) {
            try {
                Field f = Settings.class.getField(e.getKey());
                previous.put(e.getKey(), f.get(null));
                Class<?> t = f.getType();
                if (t == double.class || t == Double.class) {
                    f.set(null, e.getValue());
                } else if (t == int.class || t == Integer.class) {
                    f.set(null, (int) Math.round(e.getValue()));
                } else if (t == long.class || t == Long.class) {
                    f.set(null, Math.round(e.getValue()));
                } else if (t == float.class || t == Float.class) {
                    f.set(null, e.getValue().floatValue());
                } else {
                    throw new IllegalStateException("Unsupported Settings type for " + e.getKey() + ": " + t);
                }
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                throw new IllegalStateException("Cannot apply override " + e.getKey(), ex);
            }
        }
        return previous;
    }

    private void restoreAll(Map<String, Object> previous) {
        for (Map.Entry<String, Object> e : previous.entrySet()) {
            try {
                Field f = Settings.class.getField(e.getKey());
                f.set(null, e.getValue());
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // best effort
            }
        }
    }

    /** SplitMix-style deterministic seed derivation; same recipe as BatchRunner. */
    private static long deriveSeed(long baseSeed, CandidatePoint point, int replicate) {
        long h = baseSeed ^ point.shortId().hashCode();
        long z = h + (replicate + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Same upward search used by BatchRunner so calibration works regardless
     *  of which directory the JVM was started from. */
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
}
