package Bears.Experiments;

import Bears.BearEnvironment.BearEnvironment;
import Bears.BearEnvironment.BearSimulation;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.DeathCause;
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
        public RunResult(RunConfig config, RunMetricsRecorder recorder, MetricsEvaluator.Result evaluation) {
            this.config = config;
            this.recorder = recorder;
            this.evaluation = evaluation;
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
                "initialBearCount", "mapLength", "parameterOverrides"
        );
        List<String> summaryHeader = Arrays.asList(
                "scenarioId", "runId", "replicateIndex", "seed",
                "finalPopulation", "totalBirths",
                "deathsOldAge", "deathsStarvation", "deathsDanger",
                "meanSatietyFinal", "comparedYears", "mae", "rmse"
        );

        for (Scenario scenario : scenarios) {
            System.out.println("===== Scenario: " + scenario.scenarioId()
                    + " (replicates=" + scenario.replicates() + ") =====");
            for (int replicate = 0; replicate < scenario.replicates(); replicate++) {
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
                            String.valueOf(scenario.mapLength()),
                            formatOverrides(runConfig.parameterOverrides())
                    ));

                    List<TickMetrics> samples = result.recorder.samples();
                    int finalPop = samples.isEmpty() ? 0 : samples.get(samples.size() - 1).population;
                    double finalSatiety = samples.isEmpty() ? 0.0 : samples.get(samples.size() - 1).meanSatiety;
                    int totalBirths = 0;
                    Map<DeathCause, Integer> deathTotals = new LinkedHashMap<>();
                    for (DeathCause cause : DeathCause.values()) deathTotals.put(cause, 0);
                    for (TickMetrics sample : samples) {
                        totalBirths += sample.births;
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

        BearState initState = BearState.getInitState(scenario.mapLength());
        simulation.startNoPrompt(initState);

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
        return new RunResult(runConfig, recorder, evaluation);
    }

    private static void writePerTickCsv(Path path, List<TickMetrics> samples) throws IOException {
        List<String> header = Arrays.asList(
                "tick", "year", "population", "females", "males", "births",
                "deathsOldAge", "deathsStarvation", "deathsDanger", "deathsTotal",
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
                    String.format(Locale.ROOT, "%.4f", sample.meanSatiety),
                    String.format(Locale.ROOT, "%.4f", sample.meanAge),
                    String.valueOf(sample.pregnantFemales)
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

        // Smoke-test scenario.
        long maxTicks = (long) Math.ceil(5.0 / Settings.ONE_TICK_IN_YEARS); // 5 simulated years

        // Reference data: load from CSV if -Dreference=<path> is provided,
        // otherwise fall back to a synthetic stub so the smoke test still
        // exercises the comparison pipeline end-to-end.
        NavigableMap<Integer, Double> reference;
        String referencePath = System.getProperty("reference");
        if (referencePath != null && !referencePath.isBlank()) {
            reference = ReferenceData.loadAnnualPopulation(Paths.get(referencePath));
            System.out.println("Loaded reference series with " + reference.size() + " years from " + referencePath);
        } else {
            reference = new TreeMap<>();
            // Synthetic reference: linear growth from initial population.
            // Replace with real data when available.
            for (int year = 0; year <= 5; year++) {
                reference.put(year, 500.0 + year * 30.0);
            }
            System.out.println("No -Dreference=<path> given; using synthetic reference for smoke test.");
        }

        Scenario baseline = new Scenario(
                "baseline",
                3,           // replicates
                42L,         // base seed
                maxTicks,
                500,         // initial bears
                100,         // map length
                new LinkedHashMap<>(), // no overrides
                reference
        );

        runScenarios(java.util.Collections.singletonList(baseline), outputDir);
    }
}
