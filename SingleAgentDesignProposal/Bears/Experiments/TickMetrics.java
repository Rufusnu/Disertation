package Bears.Experiments;

import Bears.BearEnvironment.DeathCause;

import java.util.EnumMap;
import java.util.Map;

/**
 * Snapshot of one sampled simulation tick.
 *
 * Death/birth fields are deltas accumulated since the previous sample (so the
 * sum across all samples equals the total over the run). Population and
 * meanSatiety are instantaneous values after the tick was committed.
 */
public final class TickMetrics {

    public final long tick;
    public final double simulationYear;
    public final int population;
    public final int females;
    public final int males;
    public final int births;
    public final Map<DeathCause, Integer> deathsByCause;
    public final int deathsTotal;
    public final double meanSatiety;
    public final int pregnantFemales;
    public final double meanAge;

    public TickMetrics(
            long tick,
            double simulationYear,
            int population,
            int females,
            int males,
            int births,
            Map<DeathCause, Integer> deathsByCause,
            double meanSatiety,
            int pregnantFemales,
            double meanAge
    ) {
        this.tick = tick;
        this.simulationYear = simulationYear;
        this.population = population;
        this.females = females;
        this.males = males;
        this.births = births;
        this.deathsByCause = new EnumMap<>(DeathCause.class);
        if (deathsByCause != null) {
            this.deathsByCause.putAll(deathsByCause);
        }
        int total = 0;
        for (Integer v : this.deathsByCause.values()) {
            total += v;
        }
        this.deathsTotal = total;
        this.meanSatiety = meanSatiety;
        this.pregnantFemales = pregnantFemales;
        this.meanAge = meanAge;
    }
}
