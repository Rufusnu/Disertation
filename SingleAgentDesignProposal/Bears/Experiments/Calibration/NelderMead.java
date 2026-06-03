package Bears.Experiments.Calibration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Bounded Nelder-Mead simplex search in unit-cube coordinates over
 * {@link CalibrationSpace#PARAMS}.
 *
 * The simplex lives in [0,1]^d and is mapped to actual parameter values
 * via {@link CalibrationParam#fromUnit(double)}. Reflection / expansion /
 * contraction / shrink steps are projected back into the cube by clamping,
 * which is a defensible bounded-NM variant when bounds are simple boxes.
 *
 * Function evaluations are *not* cached; the caller must ensure the
 * objective is cheap relative to the bear simulation or pass a thin
 * memoising wrapper.
 */
public final class NelderMead {

    /** One vertex of the simplex: unit-cube coords + cached objective value. */
    public static final class Vertex {
        public final double[] u;
        public final EvaluationResult evaluation;
        public Vertex(double[] u, EvaluationResult evaluation) {
            this.u = u;
            this.evaluation = evaluation;
        }
        public double value() { return evaluation.meanLoss; }
    }

    public static final class Config {
        public final int maxIterations;
        public final double tolUnit;       // stop when simplex extent in unit cube < tol
        public final double initialStep;   // initial unit-cube step for non-seed vertices
        public Config(int maxIterations, double tolUnit, double initialStep) {
            this.maxIterations = maxIterations;
            this.tolUnit = tolUnit;
            this.initialStep = initialStep;
        }
    }

    public static final class Trace {
        public final int iteration;
        public final String operation;
        public final double bestLoss;
        public final double worstLoss;
        public final double simplexExtent;
        public final CandidatePoint bestPoint;
        public Trace(int iteration, String operation, double bestLoss, double worstLoss,
                     double simplexExtent, CandidatePoint bestPoint) {
            this.iteration = iteration;
            this.operation = operation;
            this.bestLoss = bestLoss;
            this.worstLoss = worstLoss;
            this.simplexExtent = simplexExtent;
            this.bestPoint = bestPoint;
        }
    }

    public static final class Result {
        public final Vertex best;
        public final List<Trace> traces;
        public final int evaluations;
        public Result(Vertex best, List<Trace> traces, int evaluations) {
            this.best = best;
            this.traces = traces;
            this.evaluations = evaluations;
        }
    }

    /**
     * @param seed     starting candidate point
     * @param evaluate objective (typically wraps a {@link PointEvaluator})
     * @param cfg      stopping criteria + step size
     */
    public static Result optimise(CandidatePoint seed,
                                  Function<CandidatePoint, EvaluationResult> evaluate,
                                  Config cfg) {
        int d = CalibrationSpace.dimension();
        Vertex[] simplex = new Vertex[d + 1];
        double[] seedU = toUnit(seed);

        // Vertex 0 = seed.
        simplex[0] = new Vertex(seedU.clone(), evaluate.apply(fromUnit(seedU)));
        int evaluations = 1;

        // Vertices 1..d: perturb seed by +initialStep along each axis (clamped).
        for (int k = 0; k < d; k++) {
            double[] u = seedU.clone();
            u[k] = clamp01(u[k] + cfg.initialStep);
            if (u[k] == seedU[k]) {
                // already at upper bound; try the other direction
                u[k] = clamp01(seedU[k] - cfg.initialStep);
            }
            simplex[k + 1] = new Vertex(u, evaluate.apply(fromUnit(u)));
            evaluations++;
        }

        List<Trace> traces = new ArrayList<>();
        for (int iter = 0; iter < cfg.maxIterations; iter++) {
            Arrays.sort(simplex, (a, b) -> Double.compare(a.value(), b.value()));
            double bestVal = simplex[0].value();
            double worstVal = simplex[d].value();
            double extent = simplexExtent(simplex);

            traces.add(new Trace(iter, "iter", bestVal, worstVal, extent, fromUnit(simplex[0].u)));
            if (extent < cfg.tolUnit) break;

            // Centroid of all but the worst vertex.
            double[] c = new double[d];
            for (int v = 0; v < d; v++) {
                for (int k = 0; k < d; k++) c[k] += simplex[v].u[k];
            }
            for (int k = 0; k < d; k++) c[k] /= d;

            double[] worst = simplex[d].u;

            // Reflect.
            double[] xr = step(c, worst, 1.0);
            Vertex vr = new Vertex(xr, evaluate.apply(fromUnit(xr)));
            evaluations++;

            if (vr.value() < simplex[0].value()) {
                // Expand.
                double[] xe = step(c, worst, 2.0);
                Vertex ve = new Vertex(xe, evaluate.apply(fromUnit(xe)));
                evaluations++;
                simplex[d] = (ve.value() < vr.value()) ? ve : vr;
                traces.add(new Trace(iter, "expand", simplex[0].value(), simplex[d].value(), extent, fromUnit(simplex[0].u)));
                continue;
            }
            if (vr.value() < simplex[d - 1].value()) {
                simplex[d] = vr;
                traces.add(new Trace(iter, "reflect", simplex[0].value(), simplex[d].value(), extent, fromUnit(simplex[0].u)));
                continue;
            }
            // Contract.
            double[] xc = step(c, worst, -0.5);
            Vertex vc = new Vertex(xc, evaluate.apply(fromUnit(xc)));
            evaluations++;
            if (vc.value() < simplex[d].value()) {
                simplex[d] = vc;
                traces.add(new Trace(iter, "contract", simplex[0].value(), simplex[d].value(), extent, fromUnit(simplex[0].u)));
                continue;
            }
            // Shrink toward best.
            for (int v = 1; v <= d; v++) {
                double[] u = new double[d];
                for (int k = 0; k < d; k++) {
                    u[k] = clamp01(simplex[0].u[k] + 0.5 * (simplex[v].u[k] - simplex[0].u[k]));
                }
                simplex[v] = new Vertex(u, evaluate.apply(fromUnit(u)));
                evaluations++;
            }
            traces.add(new Trace(iter, "shrink", simplex[0].value(), simplex[d].value(), extent, fromUnit(simplex[0].u)));
        }

        Arrays.sort(simplex, (a, b) -> Double.compare(a.value(), b.value()));
        return new Result(simplex[0], traces, evaluations);
    }

    // ------------------------------------------------------------------

    private static double[] toUnit(CandidatePoint p) {
        int d = CalibrationSpace.dimension();
        double[] u = new double[d];
        for (int k = 0; k < d; k++) {
            CalibrationParam param = CalibrationSpace.PARAMS.get(k);
            double v = p.get(param.settingsField());
            double range = param.max() - param.min();
            u[k] = range > 0 ? (v - param.min()) / range : 0.5;
        }
        return u;
    }

    private static CandidatePoint fromUnit(double[] u) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int k = 0; k < CalibrationSpace.dimension(); k++) {
            CalibrationParam p = CalibrationSpace.PARAMS.get(k);
            values.put(p.settingsField(), p.fromUnit(u[k]));
        }
        return new CandidatePoint(values);
    }

    /** Compute c + alpha*(c - worst), clamped to [0,1]^d. */
    private static double[] step(double[] c, double[] worst, double alpha) {
        double[] out = new double[c.length];
        for (int k = 0; k < c.length; k++) {
            out[k] = clamp01(c[k] + alpha * (c[k] - worst[k]));
        }
        return out;
    }

    private static double clamp01(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /** Maximum L-infinity distance between any two vertices in the simplex. */
    private static double simplexExtent(Vertex[] simplex) {
        double maxD = 0;
        int d = simplex[0].u.length;
        for (int a = 0; a < simplex.length; a++) {
            for (int b = a + 1; b < simplex.length; b++) {
                for (int k = 0; k < d; k++) {
                    double dist = Math.abs(simplex[a].u[k] - simplex[b].u[k]);
                    if (dist > maxD) maxD = dist;
                }
            }
        }
        return maxD;
    }
}
