package Bears.Experiments.Calibration;

import java.util.List;

/**
 * Phase-1 calibration search space: the six intrinsic-demography parameters
 * that determine whether the bear agent reaches a stationary, biologically
 * plausible steady state on a representative landscape.
 *
 * Bounds were chosen as defensible {@code +/-} envelopes around current Step-2
 * defaults; widen or narrow them as preliminary sweeps reveal the relevant
 * region of the search space.
 *
 * Movement, hibernation timing, dispersal, sex multipliers etc. stay fixed
 * at their Step-2 defaults during Phase-1 calibration.
 */
public final class CalibrationSpace {

    public static final List<CalibrationParam> PARAMS = List.of(
            // Baseline metabolism. 0.00075 = current default (~55-day fasting tolerance);
            // 0.0003 = slower (very fat-storing); 0.0015 = faster (rapid starvation).
            new CalibrationParam("BEAR_SATIETY_DECAY_PER_TICK", 0.0003, 0.0015),
            // Hibernation metabolism multiplier. <1 = sheltered burn rate.
            new CalibrationParam("BEAR_HIBERNATION_SATIETY_DECAY_MULTIPLIER", 0.15, 0.55),
            // Litter size mean (cubs per litter). Brown bear field range 1.8-2.6.
            new CalibrationParam("BEAR_LITTER_SIZE_MEAN", 1.6, 2.8),
            // Per-cub perinatal mortality. Field range 0.15-0.45.
            new CalibrationParam("BEAR_INFANT_MORTALITY_AT_BIRTH", 0.15, 0.45),
            // Background tile-danger mortality scale. Default 1.5e-5 ~= 3-5%/yr adult.
            new CalibrationParam("BEAR_DANGER_DEATH_RATE_PER_TICK", 5e-6, 4e-5),
            // Years between successive litters for the same female.
            new CalibrationParam("BEAR_REPRODUCTION_COOLDOWN_YEARS", 2.0, 4.0)
    );

    public static int dimension() {
        return PARAMS.size();
    }

    private CalibrationSpace() {}
}
