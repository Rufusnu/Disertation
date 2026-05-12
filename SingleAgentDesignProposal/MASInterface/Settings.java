package MASInterface;

public final class Settings {
    public static int AGENTS_NUMBER_SINGLE_EXECUTION = 1000;
    public static int TEST_EXECUTIONS_PER_AGENT_NUMBER = 50;
    public static int MAP_LENGTH = 100;
    public static int THREAD_COUNT = 16;
    public static int SIMULATION_LENGTH = 100; // in seconds
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
    public static double BEAR_REPRODUCTION_COOLDOWN_YEARS = 4;
    public static double BEAR_GESTATION_PERIOD_YEARS = 1;
    public static double BEAR_INITIAL_SATIETY_MIN = 0.55;
    public static double BEAR_INITIAL_SATIETY_MAX = 0.95;
    public static double BEAR_SATIETY_MAX = 1.0;
    public static double BEAR_SATIETY_DECAY_PER_TICK = 0.0003;
    public static double BEAR_SATIETY_DECAY_PER_TICK_PREGNANT_DEBUFF = 1.3;
    public static double BEAR_SATIETY_GAIN_PER_EAT = 0.35;
    public static double BEAR_SATIETY_EAT_PREFERENCE_THRESHOLD = 0.75; // bear prefers to eat instead of moving when satiety is above this
    public static double BEAR_MIN_SATIETY_TO_REPRODUCE = 0.6;
    public static double BEAR_MAX_AGE = 30;
    public static double DEATH_RATE_AFTER_MAX_AGE = 0.000020; // ~20% per year
    public static double BEAR_DANGER_DEATH_RATE_PER_TICK = 0.0000085; // ~20% lifetime risk on forest, ~50% on village
    public static double BEAR_DANGER_CHILD_MULTIPLIER = 1.3;

    private Settings() {}
}
