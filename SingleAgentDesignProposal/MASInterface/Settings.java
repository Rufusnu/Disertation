package MASInterface;

public final class Settings {
    public static int AGENTS_NUMBER_SINGLE_EXECUTION = 1000;
    public static int TEST_EXECUTIONS_PER_AGENT_NUMBER = 50;
    public static int MAP_LENGTH = 100;
    public static int THREAD_COUNT = 16;
    public static int SIMULATION_LENGTH = 100; // in seconds (wall-clock budget when SIMULATION_MAX_TICKS <= 0)
    /** If > 0, simulation runs for exactly this many ticks regardless of wall time. Used by the experiment pipeline. */
    public static long SIMULATION_MAX_TICKS = 0;
    public static double ONE_TICK_IN_YEARS = 0.0001140771; // currently == 1 hour

    public static boolean VERBOSE = false;
    public static boolean VERBOSE_AGENTS = false;
    public static boolean VERBOSE_PERCEPT = false;
    public static boolean BENCHMARK = true;

    public static double FOREST_PERCENTAGE = 0.25;
    public static double FIELD_PERCENTAGE = 0.55;
    public static double VILLAGE_PERCENTAGE = 0.02;
    public static double ROAD_PERCENTAGE = 0.08;
    public static double MOUNTAIN_PERCENTAGE = 0.1;

    public static double FOOD_THRESHOLD = 0.2;
    public static double DANGER_THRESHOLD = 0.5;

    public static double FOOD_EATEN_PER_TICK = 0.01;
    public static double FOOD_GROWN_PER_TICK = 0.005;

    public static double FOOD_MAX_VARIANCE = 0.2;
    public static double FOREST_AVG_FOOD = 0.6;
    public static double FIELD_AVG_FOOD = 0.1;
    public static double VILLAGE_AVG_FOOD = 0.8;
    public static double ROAD_AVG_FOOD = 0.05;
    public static double MOUNTAIN_AVG_FOOD = 0.1;

    public static double DANGER_MAX_VARIANCE = 0.2;
    public static double FOREST_AVG_DANGER = 0.1;
    public static double FIELD_AVG_DANGER = 0.4;
    public static double VILLAGE_AVG_DANGER = 0.9;
    public static double ROAD_AVG_DANGER = 0.9;
    public static double MOUNTAIN_AVG_DANGER = 0.3;

    public static double BEAR_MAX_GENERATE_AGE = 25;
    public static double BEAR_MIN_REPRODUCTION_AGE = 4;
    public static double BEAR_REPRODUCTION_COOLDOWN_YEARS = 3;
    public static double BEAR_GESTATION_PERIOD_YEARS = 1;
    /** Fraction of founding reproductive females that start the simulation already pregnant
     *  (with a random remaining gestation in [0, BEAR_GESTATION_PERIOD_YEARS)) so the first
     *  birth wave is spread out instead of synchronised. */
    public static double BEAR_INITIAL_PREGNANT_FRACTION = 0.25;
    // Satiety in [0, 1] is an abstract "energy reserve" of a bear. With 1 tick ~= 1 hour
    // (8766 ticks / year) the calibration below targets ecologically plausible behaviour:
    //   - a bear that finds no food at all loses condition over ~40-60 days (active season,
    //     not hibernation), which matches reported fasting tolerance for brown bears outside
    //     the den. 0.00075 / tick * 24 ~= 0.018 / day -> ~55 days from full to empty.
    //   - one eat event is an incremental meal, not a full top-off; on a forest tile
    //     (avg food 0.6) it returns ~0.036 satiety, i.e. ~2 days of metabolism, so bears
    //     have to forage repeatedly instead of saturating in one bite.
    //   - the eat preference threshold and initial range are pulled down so that bears do
    //     not start (and do not stabilise) near the satiety ceiling, making starvation a
    //     real cause of death when food is scarce or competition is high.
    public static double BEAR_INITIAL_SATIETY_MIN = 0.35;
    public static double BEAR_INITIAL_SATIETY_MAX = 0.75;
    public static double BEAR_SATIETY_MAX = 1.0;
    public static double BEAR_SATIETY_DECAY_PER_TICK = 0.00075;
    public static double BEAR_SATIETY_DECAY_PER_TICK_PREGNANT_DEBUFF = 1.4;
    /** Metabolism multiplier during hibernation; <1 means bears burn less satiety per tick. */
    public static double BEAR_HIBERNATION_SATIETY_DECAY_MULTIPLIER = 0.35;
    /** Fraction of simulation year at which hibernation starts (0.83 ~ early November). */
    public static double BEAR_HIBERNATION_START_YEAR_FRACTION = 0.83;
    /** Fraction of simulation year at which hibernation ends (0.17 ~ early March). */
    public static double BEAR_HIBERNATION_END_YEAR_FRACTION = 0.17;
    public static double BEAR_SATIETY_GAIN_PER_EAT = 0.06;
    public static double BEAR_SATIETY_EAT_PREFERENCE_THRESHOLD = 0.55; // bear prefers to eat instead of moving when satiety is below this
    public static double BEAR_MIN_SATIETY_TO_REPRODUCE = 0.6;
    public static double BEAR_MOVEMENT_FOOD_WEIGHT_WHEN_FULL = 0.25;
    public static double BEAR_MOVEMENT_FOOD_WEIGHT_WHEN_HUNGRY = 2.0;
    public static double BEAR_MOVEMENT_DANGER_WEIGHT_WHEN_FULL = 1.6;
    public static double BEAR_MOVEMENT_DANGER_WEIGHT_WHEN_HUNGRY = 1.0;
    public static double BEAR_MOVEMENT_CROWDING_WEIGHT_WHEN_FULL = 0.35;
    public static double BEAR_MOVEMENT_CROWDING_WEIGHT_WHEN_HUNGRY = 0.12;
    public static double BEAR_MOVEMENT_RANDOM_NOISE_WHEN_FULL = 0.15;
    public static double BEAR_MOVEMENT_RANDOM_NOISE_WHEN_HUNGRY = 0.04;
    public static int BEAR_HOME_RANGE_RADIUS = 8;
    public static double BEAR_MOVEMENT_HOME_RANGE_WEIGHT_WHEN_FULL = 0.08;
    public static double BEAR_MOVEMENT_HOME_RANGE_WEIGHT_WHEN_HUNGRY = 0.02;
    // Subadult male dispersal: in this age window males emigrate from the natal
    // home range, biased away from crowded cells and rewarded for moving outside
    // their home-range radius. This is the dominant dispersal mechanism in
    // brown bears (Swenson et al., 1998; McLellan & Hovey, 2001).
    public static double BEAR_MALE_DISPERSAL_MIN_AGE = 2.0;
    public static double BEAR_MALE_DISPERSAL_MAX_AGE = 6.0;
    public static double BEAR_MALE_DISPERSAL_CROWDING_WEIGHT_MULTIPLIER = 2.0;
    public static double BEAR_MALE_DISPERSAL_HOME_RANGE_WEIGHT_MULTIPLIER = 0.20;
    public static double BEAR_MALE_DISPERSAL_DISTANCE_BONUS_PER_CELL = 0.04;
    public static double BEAR_MAX_AGE = 30;
    public static double DEATH_RATE_AFTER_MAX_AGE = 0.000020; // ~20% per year
    /** Sex-specific old-age mortality multipliers (females are longer-lived). */
    public static double DEATH_RATE_AFTER_MAX_AGE_FEMALE_MULTIPLIER = 0.9;
    public static double DEATH_RATE_AFTER_MAX_AGE_MALE_MULTIPLIER = 1.1;
    public static double BEAR_DANGER_DEATH_RATE_PER_TICK = 0.000015; // ~3-5%/yr adult mortality on average tile, much higher on village/road
    public static double BEAR_DANGER_CHILD_MULTIPLIER = 3.5;
    /** Sex-specific danger mortality multipliers (males roam more and have higher exposure). */
    public static double BEAR_DANGER_DEATH_RATE_FEMALE_MULTIPLIER = 0.9;
    public static double BEAR_DANGER_DEATH_RATE_MALE_MULTIPLIER = 1.15;
    /** During hibernation bears are sheltered and danger mortality is greatly reduced. */
    public static double BEAR_DANGER_DEATH_RATE_HIBERNATION_MULTIPLIER = 0.35;

    // Reproduction: brown bear litters average 2.0-2.5 cubs (Swenson et al. 2001).
    // BEAR_LITTER_SIZE_STD controls between-mother variance; per-cub mortality is
    // applied at birth time and represents perinatal losses.
    public static double BEAR_LITTER_SIZE_MEAN = 2.2;
    public static double BEAR_LITTER_SIZE_STD = 0.6;
    public static double BEAR_INFANT_MORTALITY_AT_BIRTH = 0.30;

    private Settings() {}
}
