package MASInterface;

public final class Settings {
    public static int AGENTS_NUMBER_SINGLE_EXECUTION = 300;
    public static int TEST_EXECUTIONS_PER_AGENT_NUMBER = 50;
    public static int MAP_LENGTH = 100;
    public static int THREAD_COUNT = 16;
    public static int SIMULATION_LENGTH = 100; // in seconds

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

    public static double FOOD_EATEN_PER_TICK = 0.05;
    public static double FOOD_GROWN_PER_TICK = 0.001;

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

    private Settings() {}
}
