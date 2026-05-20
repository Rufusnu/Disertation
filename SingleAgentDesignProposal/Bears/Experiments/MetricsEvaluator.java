package Bears.Experiments;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/** Computes MAE and RMSE of simulated annual population vs a reference series. */
public final class MetricsEvaluator {

    /** Pairing of a year with the simulated and reference population. */
    public static final class YearSample {
        public final int year;
        public final double simulated;
        public final double reference;
        public YearSample(int year, double simulated, double reference) {
            this.year = year;
            this.simulated = simulated;
            this.reference = reference;
        }
    }

    public static final class Result {
        public final double mae;
        public final double rmse;
        public final int sampleCount;
        public Result(double mae, double rmse, int sampleCount) {
            this.mae = mae;
            this.rmse = rmse;
            this.sampleCount = sampleCount;
        }
    }

    private MetricsEvaluator() {}

    /**
     * For each year that exists in BOTH the simulated and the reference series
     * a pairing is created. Years that are missing on either side are skipped.
     */
    public static NavigableMap<Integer, YearSample> pair(
            Map<Integer, Double> simulatedAnnual,
            Map<Integer, Double> referenceAnnual
    ) {
        TreeMap<Integer, YearSample> out = new TreeMap<>();
        for (Map.Entry<Integer, Double> entry : referenceAnnual.entrySet()) {
            Integer year = entry.getKey();
            Double sim = simulatedAnnual.get(year);
            if (sim == null) {
                continue;
            }
            out.put(year, new YearSample(year, sim, entry.getValue()));
        }
        return out;
    }

    public static Result evaluate(Map<Integer, YearSample> paired) {
        if (paired.isEmpty()) {
            return new Result(Double.NaN, Double.NaN, 0);
        }
        double absSum = 0;
        double sqSum = 0;
        int n = 0;
        for (YearSample sample : paired.values()) {
            double err = sample.simulated - sample.reference;
            absSum += Math.abs(err);
            sqSum += err * err;
            n++;
        }
        return new Result(absSum / n, Math.sqrt(sqSum / n), n);
    }
}
