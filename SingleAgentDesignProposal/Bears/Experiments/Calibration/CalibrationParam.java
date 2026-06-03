package Bears.Experiments.Calibration;

/**
 * One bounded free parameter in the Phase-1 calibration search space.
 *
 * The {@code settingsField} is the exact name of the public static field on
 * {@link MASInterface.Settings} that this parameter overrides. Bounds are
 * inclusive and used by both the LHS sampler and the Nelder-Mead refinement
 * to project candidate points back into the feasible region.
 */
public record CalibrationParam(
        String settingsField,
        double min,
        double max
) {
    public CalibrationParam {
        if (!(max > min)) {
            throw new IllegalArgumentException(
                    "max must be > min for " + settingsField + " (got [" + min + ", " + max + "])");
        }
    }

    /** Project {@code v} into [{@link #min}, {@link #max}]. */
    public double clamp(double v) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    /** Map {@code u} in [0,1] to a value in [min, max]. */
    public double fromUnit(double u) {
        if (u < 0) u = 0;
        if (u > 1) u = 1;
        return min + u * (max - min);
    }

    /** Map a value in [min, max] back to a unit-cube coordinate in [0,1]. */
    public double toUnit(double v) {
        if (max == min) return 0.5;
        return clamp(v) - min;
    }
}
