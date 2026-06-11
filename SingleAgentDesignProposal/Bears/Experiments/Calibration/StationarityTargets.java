package Bears.Experiments.Calibration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stationarity targets and the scalar loss function used by Phase-1
 * calibration. Each target has a {@code band} (acceptable interval) and a
 * {@code weight}. The loss for one replicate is the sum of weighted
 * squared "outside-the-band" deviations, normalised so that a value sitting
 * at the band's centre contributes zero.
 *
 * The loss is composed of:
 *   1. populationDrift           - mean post-burn-in pop relative to target K
 *   2. populationCV              - tick-to-tick variability (instability)
 *   3. extinctionPenalty         - hinge: huge if any replicate collapsed
 *   4. perCapitaBirthRate        - births/(meanPop * years)
 *   5. meanSatiety               - mean satiety in steady state
 *   6. starvationShare           - starvation deaths / total deaths
 *   7. oldAgeShare               - old-age deaths / total deaths
 *   8. endToMeanRatio            - end-population should stay close to mean-population
 *   9. endToStartRatio           - end-population should be near/above start-population
 *  10. tailSlopeRelPerYear       - late-window population trend should be ~flat/slightly positive
 *  11. maxDrawdown               - collapse from post-burn-in peak to end should be small
 */
public final class StationarityTargets {

    /** Target carrying capacity for the calibration landscape. Configurable
     *  via -Dcalib.kTarget. Default is sized for a 200x200 stub/CLC patch. */
    public final double kTarget;
    /** First year of the evaluation window (years before this are burn-in). */
    public final double burnInYears;
    /** Last year of the evaluation window (inclusive). */
    public final double evalEndYears;

    public StationarityTargets(double kTarget, double burnInYears, double evalEndYears) {
        if (!(evalEndYears > burnInYears)) {
            throw new IllegalArgumentException("evalEndYears must be > burnInYears");
        }
        this.kTarget = kTarget;
        this.burnInYears = burnInYears;
        this.evalEndYears = evalEndYears;
    }

    // ------------------------------------------------------------------
    // Target bands. Each tuple is (lower, upper, weight).
    // ------------------------------------------------------------------

    /** Mean post-burn-in population should sit near K; band is +/- 25% of K. */
    public static final double KBAND_REL = 0.25;
    public static final double W_POP = 3.0;

    /** Stochastic variability allowed but not chaotic. */
    public static final double CV_MIN = 0.02, CV_MAX = 0.25, W_CV = 1.0;

    /** Any replicate hitting 0 bears is unacceptable. */
    public static final double W_EXTINCTION = 50.0;

    /** Brown-bear annual per-capita birth rate; field estimates ~0.20-0.35. */
    public static final double BIRTH_MIN = 0.18, BIRTH_MAX = 0.38, W_BIRTH = 2.0;

    /** Steady-state mean satiety; bears not starving, not maxed out. */
    public static final double SAT_MIN = 0.35, SAT_MAX = 0.75, W_SAT = 1.0;

    /** Starvation should not dominate (<= ~50% of all deaths). */
    public static final double STARV_MIN = 0.05, STARV_MAX = 0.50, W_STARV = 1.5;

    /** Some bears must reach old age (>= ~5% of deaths). */
    public static final double OLDAGE_MIN = 0.05, OLDAGE_MAX = 0.60, W_OLDAGE = 1.5;

    /** End population should be close to evaluation-window mean (avoid boom-then-bust). */
    public static final double END_MEAN_MIN = 0.97, END_MEAN_MAX = 1.06, W_END_MEAN = 3.0;

    /** End population should be at least stable/slightly higher than window start. */
    public static final double END_START_MIN = 1.00, END_START_MAX = 1.10, W_END_START = 2.0;

    /** Late-window trend (relative/year): allow near-flat to slightly positive. */
    public static final double TAIL_SLOPE_MIN = -0.005, TAIL_SLOPE_MAX = 0.03, W_TAIL_SLOPE = 3.0;

    /** Max drawdown from post-burn-in peak to end should stay limited. */
    public static final double DRAWDOWN_MIN = 0.0, DRAWDOWN_MAX = 0.10, W_DRAWDOWN = 3.0;

    /** Hinge penalty: ((v - upper)/scale)^2 if v > upper, ((lower - v)/scale)^2 if v < lower, else 0. */
    public static double bandPenalty(double v, double lower, double upper) {
        double mid = 0.5 * (lower + upper);
        double half = 0.5 * (upper - lower);
        if (half <= 0) return 0;
        if (v >= lower && v <= upper) return 0;
        double over = v < lower ? (lower - v) : (v - upper);
        double n = over / half; // 1.0 = one half-width outside the band
        return n * n;
    }

    /** Symmetric relative-deviation penalty (squared relative error vs target). */
    public static double relativePenalty(double v, double target, double scale) {
        if (scale <= 0) return 0;
        double n = (v - target) / scale;
        return n * n;
    }

    /** Compute the scalar loss for one replicate summary. */
    public Score score(ReplicateSummary s) {
        Map<String, Double> components = new LinkedHashMap<>();

        double popDev = relativePenalty(s.meanPopulation, kTarget, kTarget * KBAND_REL);
        components.put("populationDrift", W_POP * popDev);

        double cvPen = bandPenalty(s.populationCV, CV_MIN, CV_MAX);
        components.put("populationCV", W_CV * cvPen);

        double extPen = s.extinct ? 1.0 : 0.0;
        components.put("extinctionPenalty", W_EXTINCTION * extPen);

        double birthPen = bandPenalty(s.perCapitaBirthRate, BIRTH_MIN, BIRTH_MAX);
        components.put("perCapitaBirthRate", W_BIRTH * birthPen);

        double satPen = bandPenalty(s.meanSatiety, SAT_MIN, SAT_MAX);
        components.put("meanSatiety", W_SAT * satPen);

        double starvPen = bandPenalty(s.starvationShare, STARV_MIN, STARV_MAX);
        components.put("starvationShare", W_STARV * starvPen);

        double oldPen = bandPenalty(s.oldAgeShare, OLDAGE_MIN, OLDAGE_MAX);
        components.put("oldAgeShare", W_OLDAGE * oldPen);

        double endMeanPen = bandPenalty(s.endToMeanRatio, END_MEAN_MIN, END_MEAN_MAX);
        components.put("endToMeanRatio", W_END_MEAN * endMeanPen);

        double endStartPen = bandPenalty(s.endToStartRatio, END_START_MIN, END_START_MAX);
        components.put("endToStartRatio", W_END_START * endStartPen);

        double tailSlopePen = bandPenalty(s.tailSlopeRelPerYear, TAIL_SLOPE_MIN, TAIL_SLOPE_MAX);
        components.put("tailSlopeRelPerYear", W_TAIL_SLOPE * tailSlopePen);

        double drawdownPen = bandPenalty(s.maxDrawdown, DRAWDOWN_MIN, DRAWDOWN_MAX);
        components.put("maxDrawdown", W_DRAWDOWN * drawdownPen);

        double total = 0;
        for (double v : components.values()) total += v;
        return new Score(total, components);
    }

    /** Per-replicate inputs to {@link #score}. */
    public static final class ReplicateSummary {
        public final double meanPopulation;
        public final double populationCV;
        public final boolean extinct;
        public final double perCapitaBirthRate;
        public final double meanSatiety;
        public final double starvationShare;
        public final double oldAgeShare;
        public final double startPopulation;
        public final double endPopulation;
        public final double endToMeanRatio;
        public final double endToStartRatio;
        public final double tailSlopeRelPerYear;
        public final double maxDrawdown;

        public ReplicateSummary(double meanPopulation, double populationCV, boolean extinct,
                                double perCapitaBirthRate, double meanSatiety,
                                double starvationShare, double oldAgeShare,
                                double startPopulation, double endPopulation,
                                double endToMeanRatio, double endToStartRatio,
                                double tailSlopeRelPerYear, double maxDrawdown) {
            this.meanPopulation = meanPopulation;
            this.populationCV = populationCV;
            this.extinct = extinct;
            this.perCapitaBirthRate = perCapitaBirthRate;
            this.meanSatiety = meanSatiety;
            this.starvationShare = starvationShare;
            this.oldAgeShare = oldAgeShare;
            this.startPopulation = startPopulation;
            this.endPopulation = endPopulation;
            this.endToMeanRatio = endToMeanRatio;
            this.endToStartRatio = endToStartRatio;
            this.tailSlopeRelPerYear = tailSlopeRelPerYear;
            this.maxDrawdown = maxDrawdown;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT,
                    "[meanPop=%.0f cv=%.3f ext=%s birth=%.3f sat=%.3f starv=%.2f old=%.2f end/mean=%.3f end/start=%.3f tailSlope=%.4f drawdown=%.3f]",
                    meanPopulation, populationCV, extinct,
                    perCapitaBirthRate, meanSatiety, starvationShare, oldAgeShare,
                    endToMeanRatio, endToStartRatio,
                    tailSlopeRelPerYear, maxDrawdown);
        }
    }

    /** Output of {@link #score}: scalar loss plus per-component breakdown. */
    public static final class Score {
        public final double total;
        public final Map<String, Double> components;
        public Score(double total, Map<String, Double> components) {
            this.total = total;
            this.components = components;
        }
    }
}
