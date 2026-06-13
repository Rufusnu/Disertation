package Bears.Experiments;

import Bears.BearEnvironment.BearEnvironment;
import Bears.BearEnvironment.BearSimulation;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.DeathCause;
import Bears.BearEnvironment.MapGridLoader;
import MASInterface.Settings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Reproducible experiment driver.
 *
 * Responsibilities:
 *  - Run N replicates of each scenario, each with its own deterministic seed.
 *  - Apply parameter overrides to the static {@link Settings} before each run
 *    and restore them afterwards.
 *  - Write one per-tick CSV per replicate to {@code <outputDir>/<scenarioId>/run_<runId>.csv}.
 *  - Write a per-replicate summary CSV with MAE/RMSE vs reference data.
 *  - Write a {@code run_metadata.csv} capturing seed and parameter overrides.
 *
 * Note on reproducibility: per-run seeds are recorded and used to seed the
 * derived RNG via {@link RngSupport}. Because simulation work is dispatched
 * across virtual threads, exact tick-level reproducibility is only guaranteed
 * when a single-threaded executor is used. The seed always makes initial
 * conditions (map generation, agent ages, agent genders) reproducible.
 */
public final class BatchRunner {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static final class BatchOutcome {
        public final List<RunResult> runResults = new ArrayList<>();
    }

    public static final class RunResult {
        public final RunConfig config;
        public final RunMetricsRecorder recorder;
        public final MetricsEvaluator.Result evaluation;
        /** Path to the per-tick CSV written for this run (for later re-import). */
        public final Path runCsvPath;
        public RunResult(RunConfig config, RunMetricsRecorder recorder, MetricsEvaluator.Result evaluation,
                         Path runCsvPath) {
            this.config = config;
            this.recorder = recorder;
            this.evaluation = evaluation;
            this.runCsvPath = runCsvPath;
        }
    }

    public static BatchOutcome runScenarios(List<Scenario> scenarios, Path outputDir) throws IOException {
        BatchOutcome outcome = new BatchOutcome();
        String stamp = LocalDateTime.now().format(TS);
        Path batchDir = outputDir.resolve("batch-" + stamp);

        Path metadataPath = batchDir.resolve("run_metadata.csv");
        Path summaryPath = batchDir.resolve("summary.csv");

        List<String> metadataHeader = Arrays.asList(
                "scenarioId", "runId", "replicateIndex", "seed", "maxTicks",
                "initialBearCount", "mapLength", "mapSource", "parameterOverrides",
                "scheduleSource", "scheduleEntries"
        );
        List<String> summaryHeader = Arrays.asList(
                "scenarioId", "runId", "replicateIndex", "seed",
                "finalPopulation", "totalBirths",
                "deathsOldAge", "deathsStarvation", "deathsDanger",
                "totalConflictEvents",
                "meanSatietyFinal", "comparedYears", "mae", "rmse"
        );

        for (Scenario scenario : scenarios) {
            if (Settings.STOP_REQUESTED) {
                System.out.println("Stop requested. Aborting remaining scenarios.");
                break;
            }
            System.out.println("===== Scenario: " + scenario.scenarioId()
                    + " (replicates=" + scenario.replicates() + ") =====");
            for (int replicate = 0; replicate < scenario.replicates(); replicate++) {
                if (Settings.STOP_REQUESTED) {
                    System.out.println("Stop requested. Aborting remaining replicates for scenario " + scenario.scenarioId() + ".");
                    break;
                }
                String runId = scenario.scenarioId() + "-r" + replicate;
                long seed = deriveSeed(scenario.baseSeed(), replicate);
                RunConfig runConfig = new RunConfig(
                        scenario.scenarioId(),
                        runId,
                        replicate,
                        seed,
                        scenario.maxTicks(),
                        1,
                        scenario.parameterOverrides()
                );

                Map<String, Object> previousValues = applyOverrides(scenario, runConfig);
                try {
                    RunResult result = runSingle(runConfig, scenario, batchDir);
                    outcome.runResults.add(result);

                    CsvWriter.appendRow(metadataPath, metadataHeader, Arrays.asList(
                            runConfig.scenarioId(),
                            runConfig.runId(),
                            String.valueOf(runConfig.replicateIndex()),
                            String.valueOf(runConfig.seed()),
                            String.valueOf(runConfig.maxTicks()),
                            String.valueOf(scenario.initialBearCount()),
                            String.valueOf(Settings.MAP_LENGTH),
                            scenario.mapSource() == null ? "" : scenario.mapSource(),
                            formatOverrides(runConfig.parameterOverrides()),
                            scenario.parameterSchedule().sourceDescription(),
                            String.valueOf(scenario.parameterSchedule().entries().size())
                    ));

                    List<TickMetrics> samples = result.recorder.samples();
                    int finalPop = samples.isEmpty() ? 0 : samples.get(samples.size() - 1).population;
                    double finalSatiety = samples.isEmpty() ? 0.0 : samples.get(samples.size() - 1).meanSatiety;
                    int totalBirths = 0;
                    int totalConflictEvents = 0;
                    Map<DeathCause, Integer> deathTotals = new LinkedHashMap<>();
                    for (DeathCause cause : DeathCause.values()) deathTotals.put(cause, 0);
                    for (TickMetrics sample : samples) {
                        totalBirths += sample.births;
                        totalConflictEvents += sample.conflictEvents;
                        for (Map.Entry<DeathCause, Integer> e : sample.deathsByCause.entrySet()) {
                            deathTotals.merge(e.getKey(), e.getValue(), Integer::sum);
                        }
                    }

                    CsvWriter.appendRow(summaryPath, summaryHeader, Arrays.asList(
                            runConfig.scenarioId(),
                            runConfig.runId(),
                            String.valueOf(runConfig.replicateIndex()),
                            String.valueOf(runConfig.seed()),
                            String.valueOf(finalPop),
                            String.valueOf(totalBirths),
                            String.valueOf(deathTotals.getOrDefault(DeathCause.OLD_AGE, 0)),
                            String.valueOf(deathTotals.getOrDefault(DeathCause.STARVATION, 0)),
                            String.valueOf(deathTotals.getOrDefault(DeathCause.DANGER, 0)),
                            String.valueOf(totalConflictEvents),
                            String.format(Locale.ROOT, "%.4f", finalSatiety),
                            String.valueOf(result.evaluation.sampleCount),
                            formatDouble(result.evaluation.mae),
                            formatDouble(result.evaluation.rmse)
                    ));
                } finally {
                    restoreOverrides(previousValues);
                }
            }
        }

        System.out.println("Batch written to: " + batchDir.toAbsolutePath());
        return outcome;
    }

    private static RunResult runSingle(RunConfig runConfig, Scenario scenario, Path batchDir) throws IOException {
        System.out.println("--- Run: " + runConfig.runId()
                + " seed=" + runConfig.seed()
                + " maxTicks=" + runConfig.maxTicks() + " ---");

        // Seed RNG support BEFORE any agent / state is constructed.
        RngSupport.setSeed(runConfig.seed());

        BearEnvironment env = new BearEnvironment();
        BearSimulation simulation = new BearSimulation(env);
        RunMetricsRecorder recorder = new RunMetricsRecorder(runConfig);
        simulation.setMetricsRecorder(recorder);

        ScheduleApplier scheduleApplier = new ScheduleApplier(scenario.parameterSchedule());
        simulation.setScheduleApplier(scheduleApplier);

        BearState initState;
        String mapSource = scenario.mapSource();
        if (mapSource != null && !mapSource.isBlank()) {
            Path gridPath = resolveDataPath(mapSource);
            MapGridLoader.Grid grid = MapGridLoader.load(gridPath);
            // Several code paths still read Settings.MAP_LENGTH; keep it in
            // sync with the loaded grid so they agree.
            Settings.MAP_LENGTH = grid.mapLength();
            initState = BearState.getInitStateFromGrid(grid);
            System.out.println("    map=" + gridPath + " (" + grid.width + "x" + grid.height + ")");
        } else {
            initState = BearState.getInitState(scenario.mapLength());
        }
        try {
            simulation.startNoPrompt(initState);
        } finally {
            // Always restore Settings touched by the schedule, even on failure.
            scheduleApplier.restore();
        }

        // Per-run audit of which schedule events fired and when.
        if (!scheduleApplier.isEmpty()) {
            Path scheduleCsv = batchDir.resolve(scenario.scenarioId())
                    .resolve("schedule_" + runConfig.runId() + ".csv");
            writeScheduleAuditCsv(scheduleCsv, scheduleApplier.appliedEvents());
        }

        // Per-tick CSV.
        Path runCsv = batchDir.resolve(scenario.scenarioId())
                .resolve("run_" + runConfig.runId() + ".csv");
        writePerTickCsv(runCsv, recorder.samples());

        // Pair with reference data and evaluate.
        Map<Integer, Double> simulatedAnnual = recorder.annualEndPopulation();
        NavigableMap<Integer, MetricsEvaluator.YearSample> paired =
                MetricsEvaluator.pair(simulatedAnnual, scenario.referencePopulationByYear());
        MetricsEvaluator.Result evaluation = MetricsEvaluator.evaluate(paired);
        System.out.println("    mae=" + evaluation.mae + " rmse=" + evaluation.rmse
                + " comparedYears=" + evaluation.sampleCount);

        // Per-year comparison CSV (only if reference data was supplied).
        if (!scenario.referencePopulationByYear().isEmpty()) {
            Path comparisonCsv = batchDir.resolve(scenario.scenarioId())
                    .resolve("comparison_" + runConfig.runId() + ".csv");
            writeComparisonCsv(comparisonCsv, paired);
        }

        RngSupport.clearSeed();
        return new RunResult(runConfig, recorder, evaluation, runCsv);
    }

    private static void writePerTickCsv(Path path, List<TickMetrics> samples) throws IOException {
        List<String> header = Arrays.asList(
                "tick", "year", "population", "females", "males", "births",
                "deathsOldAge", "deathsStarvation", "deathsDanger", "deathsTotal",
                "conflictEvents",
                "meanSatiety", "meanAge", "pregnantFemales"
        );
        List<List<String>> rows = new ArrayList<>(samples.size());
        for (TickMetrics sample : samples) {
            rows.add(Arrays.asList(
                    String.valueOf(sample.tick),
                    String.format(Locale.ROOT, "%.4f", sample.simulationYear),
                    String.valueOf(sample.population),
                    String.valueOf(sample.females),
                    String.valueOf(sample.males),
                    String.valueOf(sample.births),
                    String.valueOf(sample.deathsByCause.getOrDefault(DeathCause.OLD_AGE, 0)),
                    String.valueOf(sample.deathsByCause.getOrDefault(DeathCause.STARVATION, 0)),
                    String.valueOf(sample.deathsByCause.getOrDefault(DeathCause.DANGER, 0)),
                    String.valueOf(sample.deathsTotal),
                    String.valueOf(sample.conflictEvents),
                    String.format(Locale.ROOT, "%.4f", sample.meanSatiety),
                    String.format(Locale.ROOT, "%.4f", sample.meanAge),
                    String.valueOf(sample.pregnantFemales)
            ));
        }
        CsvWriter.writeRows(path, header, rows);
    }

    private static void writeScheduleAuditCsv(Path path, List<ScheduleApplier.AppliedEvent> events) throws IOException {
        List<String> header = Arrays.asList("tick", "simulationYear", "parameter", "oldValue", "newValue");
        List<List<String>> rows = new ArrayList<>(events.size());
        for (ScheduleApplier.AppliedEvent ev : events) {
            rows.add(Arrays.asList(
                    String.valueOf(ev.tick),
                    String.format(Locale.ROOT, "%.4f", ev.simulationYear),
                    ev.parameter,
                    ev.oldValue,
                    ev.newValue
            ));
        }
        CsvWriter.writeRows(path, header, rows);
    }

    private static void writeComparisonCsv(Path path, NavigableMap<Integer, MetricsEvaluator.YearSample> paired) throws IOException {
        List<String> header = Arrays.asList("year", "simulatedPopulation", "referencePopulation", "absoluteError");
        List<List<String>> rows = new ArrayList<>(paired.size());
        for (MetricsEvaluator.YearSample sample : paired.values()) {
            rows.add(Arrays.asList(
                    String.valueOf(sample.year),
                    String.format(Locale.ROOT, "%.2f", sample.simulated),
                    String.format(Locale.ROOT, "%.2f", sample.reference),
                    String.format(Locale.ROOT, "%.2f", Math.abs(sample.simulated - sample.reference))
            ));
        }
        CsvWriter.writeRows(path, header, rows);
    }

    private static long deriveSeed(long baseSeed, int replicate) {
        // SplitMix-style derivation so consecutive replicates get well-spread seeds.
        long z = baseSeed + (replicate + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Applies parameter overrides + scenario-level Settings (initialBearCount, mapLength, maxTicks). Returns previous values. */
    private static Map<String, Object> applyOverrides(Scenario scenario, RunConfig runConfig) {
        Map<String, Object> previous = new LinkedHashMap<>();
        previous.put("AGENTS_NUMBER_SINGLE_EXECUTION", Settings.AGENTS_NUMBER_SINGLE_EXECUTION);
        previous.put("MAP_LENGTH", Settings.MAP_LENGTH);
        previous.put("SIMULATION_MAX_TICKS", Settings.SIMULATION_MAX_TICKS);

        Settings.AGENTS_NUMBER_SINGLE_EXECUTION = scenario.initialBearCount();
        Settings.MAP_LENGTH = scenario.mapLength();
        Settings.SIMULATION_MAX_TICKS = runConfig.maxTicks();

        for (Map.Entry<String, String> override : runConfig.parameterOverrides().entrySet()) {
            try {
                Field field = Settings.class.getField(override.getKey());
                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    System.err.println("WARN: override target not static: " + override.getKey());
                    continue;
                }
                previous.put(override.getKey(), field.get(null));
                Object parsed = parseFieldValue(field.getType(), override.getValue());
                field.set(null, parsed);
            } catch (NoSuchFieldException e) {
                System.err.println("WARN: unknown Settings field: " + override.getKey());
            } catch (IllegalAccessException e) {
                System.err.println("WARN: cannot set Settings field: " + override.getKey() + " - " + e);
            }
        }
        return previous;
    }

    private static void restoreOverrides(Map<String, Object> previousValues) {
        for (Map.Entry<String, Object> entry : previousValues.entrySet()) {
            try {
                Field field = Settings.class.getField(entry.getKey());
                field.set(null, entry.getValue());
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // best effort
            }
        }
    }

    private static Object parseFieldValue(Class<?> type, String raw) {
        if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
        if (type == long.class || type == Long.class) return Long.parseLong(raw);
        if (type == double.class || type == Double.class) return Double.parseDouble(raw);
        if (type == float.class || type == Float.class) return Float.parseFloat(raw);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);
        return raw;
    }

    private static String formatOverrides(Map<String, String> overrides) {
        if (overrides.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            if (!first) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String formatDouble(double d) {
        if (Double.isNaN(d)) return "NaN";
        return String.format(Locale.ROOT, "%.4f", d);
    }

    // ----------------------------------------------------------------------
    // Convenience main: runs a default 3-replicate scenario without
    // reference data, useful as a smoke test for the pipeline.
    // ----------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        Path outputDir = Paths.get(args.length > 0 ? args[0] : "experiment-output");

        // Smoke-test scenario. Length defaults to 20 simulated years, override
        // with -Dyears=<n> for shorter dry runs.
        double smokeYears = 20.0;
        String yearsProp = System.getProperty("years");
        if (yearsProp != null && !yearsProp.isBlank()) {
            smokeYears = Double.parseDouble(yearsProp.trim());
        }
        long maxTicks = (long) Math.ceil(smokeYears / Settings.ONE_TICK_IN_YEARS);

        // Reference data: by default load the in-repo Romanian brown bear
        // annual series (see reference-data/README.md for sources). Override
        // with -Dreference=<path> to point at a different CSV. Calendar years
        // in the CSV are shifted to simulation years (0,1,...) using
        // -Dreference.startYear (default: first year present in the CSV).
        NavigableMap<Integer, Double> reference;
        String referencePath = System.getProperty(
                "reference",
                "reference-data/romania_brown_bear_population.csv");
        Path resolvedReference = Paths.get(referencePath);
        if (!resolvedReference.isAbsolute() && !java.nio.file.Files.exists(resolvedReference)) {
            // Resolve robustly: search upward from the current working directory
            // so the same default works whether the JVM was started from the
            // repo root, from SingleAgentDesignProposal/, or from a build dir.
            Path probe = Paths.get("").toAbsolutePath();
            for (int depth = 0; depth < 6; depth++) {
                Path candidate = probe.resolve(referencePath);
                if (java.nio.file.Files.exists(candidate)) {
                    resolvedReference = candidate;
                    break;
                }
                Path parent = probe.getParent();
                if (parent == null) {
                    break;
                }
                probe = parent;
            }
        }
        if (java.nio.file.Files.exists(resolvedReference)) {
            NavigableMap<Integer, Double> raw = ReferenceData.loadAnnualPopulation(resolvedReference);
            String startYearProp = System.getProperty("reference.startYear");
            int startYear;
            if (startYearProp != null && !startYearProp.isBlank()) {
                startYear = Integer.parseInt(startYearProp.trim());
            } else {
                startYear = raw.isEmpty() ? 0 : raw.firstKey();
            }
            reference = new TreeMap<>();
            for (Map.Entry<Integer, Double> e : raw.entrySet()) {
                reference.put(e.getKey() - startYear, e.getValue());
            }
            System.out.println("Loaded reference series with " + reference.size()
                    + " years from " + resolvedReference.toAbsolutePath()
                    + " (calendar start year = " + startYear + ")");
        } else {
            reference = new TreeMap<>();
            for (int year = 0; year <= 5; year++) {
                reference.put(year, 500.0 + year * 30.0);
            }
            System.out.println("Reference CSV not found at " + resolvedReference
                    + "; using synthetic reference for smoke test.");
        }

        // Optional time-varying parameter schedule. Use -Dschedule=<path>
        // pointing at a CSV (year,parameter,value) or JSON file. Stationary
        // by default.
        ParameterSchedule schedule = ParameterSchedule.empty();
        String schedulePath = new String("E:\\Github\\Disertation\\SingleAgentDesignProposal\\Bears\\Experiments\\Schedules\\schedule.csv");   // System.getProperty("schedule");
        if (schedulePath != null && !schedulePath.isBlank()) {
            schedule = ParameterSchedule.load(Paths.get(schedulePath));
            System.out.println("Loaded parameter schedule with " + schedule.entries().size()
                    + " entries from " + schedulePath);
        }

        // Initial bear count: by default, take the reference population at
        // simulation year 0 (i.e. the first year present in the loaded CSV)
        // so the simulation starts from the same baseline as the empirical
        // series. Override with -DinitialBears=<n> if needed.
        int initialBears;
        String initialBearsProp = System.getProperty("initialBears");
        if (initialBearsProp != null && !initialBearsProp.isBlank()) {
            initialBears = Integer.parseInt(initialBearsProp.trim());
        } else if (!reference.isEmpty()) {
            initialBears = (int) Math.round(reference.firstEntry().getValue());
        } else {
            initialBears = 500;
        }
        System.out.println("Initial bear count for scenario: " + initialBears);

        Scenario baseline = new Scenario(
                "baseline",
                3,           // replicates
                42L,         // base seed
                maxTicks,
                initialBears,
                100,         // map length (overridden by grid when -Dmap.source is set)
                new LinkedHashMap<>(), // no overrides
                reference,
                schedule,
                resolveOptionalMapSource()
        );

        runScenarios(java.util.Collections.singletonList(baseline), outputDir);
    }

    /**
     * Resolves a workspace-relative data path by searching upward from the
     * current working directory; used for both the reference series and the
     * habitat grid so the same defaults work whether the JVM is started from
     * the repo root, from {@code SingleAgentDesignProposal/}, or from a build
     * directory.
     */
    private static Path resolveDataPath(String relativeOrAbsolute) {
        Path direct = Paths.get(relativeOrAbsolute);
        if (direct.isAbsolute() || java.nio.file.Files.exists(direct)) {
            return direct;
        }
        Path probe = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6; depth++) {
            Path candidate = probe.resolve(relativeOrAbsolute);
            if (java.nio.file.Files.exists(candidate)) {
                return candidate;
            }
            Path parent = probe.getParent();
            if (parent == null) {
                break;
            }
            probe = parent;
        }
        return direct;
    }

    private static String resolveOptionalMapSource() {
        String prop = System.getProperty("map.source");
        if (prop == null || prop.isBlank()) {
            // Honour Settings.MAP_SOURCE as a secondary opt-in.
            if (Settings.MAP_SOURCE != null && !Settings.MAP_SOURCE.isBlank()) {
                return Settings.MAP_SOURCE;
            }
            return "";
        }
        return prop.trim();
    }
}
