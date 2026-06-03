package Bears.Experiments.Calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Latin Hypercube sampler over {@link CalibrationSpace#PARAMS}.
 *
 * For each dimension, [0,1] is partitioned into N equal strata and each
 * stratum is sampled once with a uniform jitter; columns are then
 * independently permuted so the joint sample is balanced.
 */
public final class LatinHypercube {

    private LatinHypercube() {}

    public static List<CandidatePoint> sample(int n, long seed) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        SplittableRandom rng = new SplittableRandom(seed);
        int d = CalibrationSpace.dimension();

        // unit[k][i] = i-th sample for parameter k, in [0,1]
        double[][] unit = new double[d][n];
        for (int k = 0; k < d; k++) {
            // Stratified samples in (k/n, (k+1)/n)
            double[] u = new double[n];
            for (int i = 0; i < n; i++) {
                u[i] = (i + rng.nextDouble()) / n;
            }
            // Shuffle (Fisher-Yates) to decorrelate columns.
            for (int i = n - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                double tmp = u[i];
                u[i] = u[j];
                u[j] = tmp;
            }
            unit[k] = u;
        }

        List<CandidatePoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Double> values = new LinkedHashMap<>();
            for (int k = 0; k < d; k++) {
                CalibrationParam p = CalibrationSpace.PARAMS.get(k);
                values.put(p.settingsField(), p.fromUnit(unit[k][i]));
            }
            points.add(new CandidatePoint(values));
        }
        return Collections.unmodifiableList(points);
    }
}
