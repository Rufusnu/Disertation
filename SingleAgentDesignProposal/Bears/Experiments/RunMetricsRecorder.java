package Bears.Experiments;

import Bears.BearAgent.BearAgent;
import Bears.BearEnvironment.DeathCause;
import MASInterface.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-run metrics collector. {@code BearSimulation} invokes the recorder
 * after every committed tick. Births/deaths are deltas since the last sample;
 * other fields are instantaneous.
 */
public final class RunMetricsRecorder {

    private final RunConfig runConfig;
    private final List<TickMetrics> samples = new ArrayList<>();
    private int birthsSinceLastSample = 0;
    private final EnumMap<DeathCause, Integer> deathsSinceLastSample = new EnumMap<>(DeathCause.class);

    public RunMetricsRecorder(RunConfig runConfig) {
        this.runConfig = runConfig;
        for (DeathCause cause : DeathCause.values()) {
            deathsSinceLastSample.put(cause, 0);
        }
    }

    public RunConfig runConfig() { return runConfig; }

    /** Called by the simulation each time a birth occurs (one per child). */
    public void noteBirth() {
        birthsSinceLastSample++;
    }

    /** Called by the simulation each time a death occurs. */
    public void noteDeath(DeathCause cause) {
        deathsSinceLastSample.merge(cause, 1, Integer::sum);
    }

    /**
     * Records a tick sample if this tick lies on the configured sample stride.
     * Always called once per tick by {@code BearSimulation}; the stride filter
     * is applied here. Pass {@code tick == 0} (with {@code force=true} via
     * {@link #sampleInitialState}) to record the founding cohort before any
     * tick has run.
     */
    public void sampleTick(long tick, Collection<BearAgent> agents) {
        sampleTickInternal(tick, agents, false);
    }

    /** Records the initial (pre-tick-1) state regardless of sample stride. */
    public void sampleInitialState(Collection<BearAgent> agents) {
        sampleTickInternal(0L, agents, true);
    }

    private void sampleTickInternal(long tick, Collection<BearAgent> agents, boolean force) {
        if (!force && (tick % runConfig.metricsSampleEveryTicks()) != 0) {
            return;
        }

        double simulationYear = tick * Settings.ONE_TICK_IN_YEARS;
        int population = agents.size();
        double satietySum = 0;
        double ageSum = 0;
        int pregnant = 0;
        int females = 0;
        int males = 0;
        for (BearAgent agent : agents) {
            satietySum += agent.getSatiety();
            ageSum += agent.getAge();
            if (agent.isPregnant()) {
                pregnant++;
            }
            if (agent.getGender() == BearAgent.Gender.FEMALE) {
                females++;
            } else {
                males++;
            }
        }
        double meanSatiety = population > 0 ? satietySum / population : 0.0;
        double meanAge = population > 0 ? ageSum / population : 0.0;

        EnumMap<DeathCause, Integer> deathsSnapshot = new EnumMap<>(deathsSinceLastSample);

        samples.add(new TickMetrics(
                tick,
                simulationYear,
                population,
                females,
                males,
                birthsSinceLastSample,
                deathsSnapshot,
                meanSatiety,
                pregnant,
                meanAge
        ));

        // Reset deltas for next sampling interval.
        birthsSinceLastSample = 0;
        for (DeathCause cause : DeathCause.values()) {
            deathsSinceLastSample.put(cause, 0);
        }
    }

    public List<TickMetrics> samples() {
        return samples;
    }

    /**
     * Returns end-of-year (final sample whose floor(year) == y) population per
     * year, suitable for comparing against an annual reference series.
     */
    public Map<Integer, Double> annualEndPopulation() {
        Map<Integer, Double> result = new java.util.TreeMap<>();
        for (TickMetrics sample : samples) {
            int year = (int) Math.floor(sample.simulationYear);
            result.put(year, (double) sample.population);
        }
        return result;
    }
}
