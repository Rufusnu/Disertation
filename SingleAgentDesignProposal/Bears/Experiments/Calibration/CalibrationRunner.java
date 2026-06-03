package Bears.Experiments.Calibration;

import Bears.Experiments.CsvWriter;
import MASInterface.Settings;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase-1 calibration driver: Latin Hypercube survey of
 * {@link CalibrationSpace#PARAMS}, followed by Nelder-Mead refinement of
 * the top-K candidates. Writes:
 *
 * <pre>
 * calibration-output/sweep-&lt;ts&gt;/
 *     manifest.txt        - human-readable run header
 *     points.csv          - every evaluated point (LHS + NM steps)
 *     best.csv            - top-K candidates by mean loss
 *     convergence.csv     - per-iteration trace for each NM refinement
 * </pre>
 *
 * CLI flags (all optional, with sensible defaults):
 *   -Dcalib.lhsPoints=64
 *   -Dcalib.refineTop=4
 *   -Dcalib.nmIterations=40
 *   -Dcalib.replicates=3
 *   -Dcalib.initialBears=2000
 *   -Dcalib.mapLength=200
 *   -Dcalib.mapSource=reference-data/romania-map-clc2018.txt
 *   -Dcalib.burnInYears=15
 *   -Dcalib.evalEndYears=50
 *   -Dcalib.kTarget=6000
 *   -Dcalib.seed=20260603
 *   -Dcalib.verbose=false
 *   -Dcalib.output=calibration-output
 */
public final class CalibrationRunner {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void main(String[] args) throws IOException {
        int lhsPoints       = intProp("calib.lhsPoints", 64);
        int refineTop       = intProp("calib.refineTop", 4);
        int nmIterations    = intProp("calib.nmIterations", 40);
        int replicates      = intProp("calib.replicates", 3);
        int initialBears    = intProp("calib.initialBears", 2000);
        int mapLength       = intProp("calib.mapLength", 200);
        String mapSource    = strProp("calib.mapSource", "");
        double burnIn       = dblProp("calib.burnInYears", 15.0);
        double evalEnd      = dblProp("calib.evalEndYears", 50.0);
        double kTarget      = dblProp("calib.kTarget", 6000.0);
        long seed           = longProp("calib.seed", 20260603L);
        boolean verbose     = boolProp("calib.verbose", false);
        Path outputRoot     = Paths.get(strProp("calib.output", args.length > 0 ? args[0] : "calibration-output"));

        String stamp = LocalDateTime.now().format(TS);
        Path outDir = outputRoot.resolve("sweep-" + stamp);
        Path pointsCsv = outDir.resolve("points.csv");
        Path bestCsv = outDir.resolve("best.csv");
        Path convergenceCsv = outDir.resolve("convergence.csv");
        Path manifest = outDir.resolve("manifest.txt");

        PointEvaluator.Config evalCfg = new PointEvaluator.Config(
                replicates, initialBears, mapLength, mapSource,
                burnIn, evalEnd, seed, kTarget, verbose);
        PointEvaluator evaluator = new PointEvaluator(evalCfg);

        writeManifest(manifest,
                "lhsPoints=" + lhsPoints,
                "refineTop=" + refineTop,
                "nmIterations=" + nmIterations,
                "replicates=" + replicates,
                "initialBears=" + initialBears,
                "mapLength=" + mapLength,
                "mapSource=" + mapSource,
                "burnInYears=" + burnIn,
                "evalEndYears=" + evalEnd,
                "kTarget=" + kTarget,
                "seed=" + seed,
                "ONE_TICK_IN_YEARS=" + Settings.ONE_TICK_IN_YEARS,
                "params=" + CalibrationSpace.PARAMS);

        List<String> pointsHeader = pointsHeader();
        List<EvalRow> allRows = new ArrayList<>();

        // ---- Phase A: Latin Hypercube survey ----
        System.out.println("=== LHS survey: " + lhsPoints + " points, "
                + replicates + " replicates each ===");
        List<CandidatePoint> lhs = LatinHypercube.sample(lhsPoints, seed);
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < lhs.size(); i++) {
            CandidatePoint p = lhs.get(i);
            EvaluationResult r = evaluator.evaluate(p);
            EvalRow row = new EvalRow("LHS", i, -1, r);
            allRows.add(row);
            CsvWriter.appendRow(pointsCsv, pointsHeader, row.toCsv());
            System.out.printf(Locale.ROOT, "[LHS %3d/%d] loss=%.4f %s%n",
                    i + 1, lhs.size(), r.meanLoss, summariseAvg(r));
        }
        long lhsMillis = System.currentTimeMillis() - t0;
        System.out.printf(Locale.ROOT, "=== LHS done in %.1fs ===%n", lhsMillis / 1000.0);

        // ---- Phase B: Nelder-Mead refinement of the top-K LHS points ----
        allRows.sort(Comparator.comparingDouble(r -> r.evaluation.meanLoss));
        List<EvalRow> seeds = allRows.subList(0, Math.min(refineTop, allRows.size()));
        List<String> convergenceHeader = Arrays.asList(
                "refineRound", "iteration", "operation",
                "bestLoss", "worstLoss", "simplexExtent",
                "bestParamsJson");

        NelderMead.Config nmCfg = new NelderMead.Config(nmIterations, 0.02, 0.10);
        for (int r = 0; r < seeds.size(); r++) {
            CandidatePoint seedPoint = seeds.get(r).evaluation.point;
            System.out.printf(Locale.ROOT, "%n=== Nelder-Mead refine #%d (seed loss=%.4f) ===%n",
                    r + 1, seeds.get(r).evaluation.meanLoss);

            final int round = r;
            NelderMead.Result nmResult = NelderMead.optimise(seedPoint, p -> {
                EvaluationResult er = evaluator.evaluate(p);
                EvalRow row = new EvalRow("NM" + round, -1, allRows.size(), er);
                allRows.add(row);
                try {
                    CsvWriter.appendRow(pointsCsv, pointsHeader, row.toCsv());
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
                System.out.printf(Locale.ROOT, "  [NM%d eval] loss=%.4f %s%n",
                        round, er.meanLoss, summariseAvg(er));
                return er;
            }, nmCfg);

            for (NelderMead.Trace t : nmResult.traces) {
                CsvWriter.appendRow(convergenceCsv, convergenceHeader, Arrays.asList(
                        String.valueOf(round),
                        String.valueOf(t.iteration),
                        t.operation,
                        formatDouble(t.bestLoss),
                        formatDouble(t.worstLoss),
                        formatDouble(t.simplexExtent),
                        pointToJson(t.bestPoint)
                ));
            }
            System.out.printf(Locale.ROOT, "=== NM #%d best loss=%.4f after %d evals ===%n",
                    r + 1, nmResult.best.value(), nmResult.evaluations);
        }

        // ---- Phase C: Write top-K best.csv ----
        allRows.sort(Comparator.comparingDouble(row -> row.evaluation.meanLoss));
        List<String> bestHeader = pointsHeader();
        int topK = Math.max(10, refineTop * 3);
        for (int i = 0; i < Math.min(topK, allRows.size()); i++) {
            CsvWriter.appendRow(bestCsv, bestHeader, allRows.get(i).toCsv());
        }

        // ---- Final summary ----
        EvalRow winner = allRows.get(0);
        System.out.println();
        System.out.println("================ Phase-1 calibration done ================");
        System.out.printf(Locale.ROOT, "Best loss: %.4f%n", winner.evaluation.meanLoss);
        System.out.println("Best point: " + winner.evaluation.point);
        System.out.println("Total evaluations: " + allRows.size());
        System.out.println("Output: " + outDir.toAbsolutePath());
    }

    // ----------------------------------------------------------------------

    private static List<String> pointsHeader() {
        List<String> h = new ArrayList<>();
        h.add("source");
        h.add("orderIndex");
        h.add("globalIndex");
        h.add("meanLoss");
        for (CalibrationParam p : CalibrationSpace.PARAMS) h.add(p.settingsField());
        h.add("replicateCount");
        h.add("meanPopulation");
        h.add("populationCV");
        h.add("extinctRate");
        h.add("perCapitaBirthRate");
        h.add("meanSatiety");
        h.add("starvationShare");
        h.add("oldAgeShare");
        h.add("elapsedMillis");
        return h;
    }

    private static final class EvalRow {
        final String source;
        final int orderIndex;
        final int globalIndex;
        final EvaluationResult evaluation;
        EvalRow(String source, int orderIndex, int globalIndex, EvaluationResult evaluation) {
            this.source = source;
            this.orderIndex = orderIndex;
            this.globalIndex = globalIndex;
            this.evaluation = evaluation;
        }

        List<String> toCsv() {
            List<String> row = new ArrayList<>();
            row.add(source);
            row.add(String.valueOf(orderIndex));
            row.add(String.valueOf(globalIndex));
            row.add(formatDouble(evaluation.meanLoss));
            for (CalibrationParam p : CalibrationSpace.PARAMS) {
                row.add(formatDouble(evaluation.point.get(p.settingsField())));
            }
            int n = evaluation.replicateSummaries.size();
            double meanPop = 0, cv = 0, ext = 0, birth = 0, sat = 0, starv = 0, old = 0;
            for (StationarityTargets.ReplicateSummary s : evaluation.replicateSummaries) {
                meanPop += s.meanPopulation;
                cv      += s.populationCV;
                ext     += s.extinct ? 1.0 : 0.0;
                birth   += s.perCapitaBirthRate;
                sat     += s.meanSatiety;
                starv   += s.starvationShare;
                old     += s.oldAgeShare;
            }
            double inv = n > 0 ? 1.0 / n : 0;
            row.add(String.valueOf(n));
            row.add(formatDouble(meanPop * inv));
            row.add(formatDouble(cv * inv));
            row.add(formatDouble(ext * inv));
            row.add(formatDouble(birth * inv));
            row.add(formatDouble(sat * inv));
            row.add(formatDouble(starv * inv));
            row.add(formatDouble(old * inv));
            row.add(String.valueOf(evaluation.elapsedMillis));
            return row;
        }
    }

    private static String summariseAvg(EvaluationResult r) {
        double pop = 0, sat = 0, birth = 0; int ext = 0;
        int n = r.replicateSummaries.size();
        for (StationarityTargets.ReplicateSummary s : r.replicateSummaries) {
            pop += s.meanPopulation; sat += s.meanSatiety;
            birth += s.perCapitaBirthRate; if (s.extinct) ext++;
        }
        return String.format(Locale.ROOT, "meanPop=%.0f sat=%.2f birth=%.3f ext=%d/%d",
                n > 0 ? pop / n : 0, n > 0 ? sat / n : 0, n > 0 ? birth / n : 0, ext, n);
    }

    private static String pointToJson(CandidatePoint p) {
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Double> e : p.values().entrySet()) {
            if (i++ > 0) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":")
              .append(formatDouble(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private static void writeManifest(Path path, String... lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Phase-1 calibration manifest\n");
        sb.append("============================\n");
        sb.append("timestamp=").append(LocalDateTime.now()).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.writeString(path, sb.toString());
    }

    private static String formatDouble(double d) {
        if (Double.isNaN(d)) return "NaN";
        if (Double.isInfinite(d)) return d > 0 ? "Inf" : "-Inf";
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
    private static boolean boolProp(String name, boolean def) {
        String s = System.getProperty(name);
        return (s == null || s.isBlank()) ? def : Boolean.parseBoolean(s.trim());
    }
    private static String strProp(String name, String def) {
        String s = System.getProperty(name);
        return (s == null) ? def : s;
    }
}
