package Bears.BearEnvironment;

import MASInterface.Environment.Cell;
import MASInterface.Settings;
import Bears.Experiments.RngSupport;

public enum BearCellType implements Cell.CellType {
    NONE(-1, -1, '~'),
    FOREST(Settings.FOREST_AVG_FOOD, Settings.FOREST_AVG_DANGER, 'F'),
    FIELD(Settings.FIELD_AVG_FOOD, Settings.FIELD_AVG_DANGER, 'I'),
    VILLAGE(Settings.VILLAGE_AVG_FOOD, Settings.VILLAGE_AVG_DANGER, 'V'),
    ROAD(Settings.ROAD_AVG_FOOD, Settings.ROAD_AVG_DANGER, 'R'),
    MOUNTAIN(Settings.MOUNTAIN_AVG_FOOD, Settings.MOUNTAIN_AVG_DANGER, 'M');

    private final double averageFood;
    private final double averageDanger;
    private final char mapSymbol;

    BearCellType(double averageFood, double averageDanger, char mapSymbol) {
        this.averageFood = averageFood;
        this.averageDanger = averageDanger;
        this.mapSymbol = mapSymbol;
    }

    public static BearCellType randomTerrainType() {
        double randomNumber = RngSupport.environment().nextDouble();

        if (randomNumber < Settings.FOREST_PERCENTAGE) return FOREST;
        if (randomNumber < Settings.FOREST_PERCENTAGE + Settings.FIELD_PERCENTAGE) return FIELD;
        if (randomNumber < Settings.FOREST_PERCENTAGE + Settings.FIELD_PERCENTAGE + Settings.VILLAGE_PERCENTAGE) return VILLAGE;
        if (randomNumber < Settings.FOREST_PERCENTAGE + Settings.FIELD_PERCENTAGE + Settings.VILLAGE_PERCENTAGE + Settings.ROAD_PERCENTAGE) return ROAD;
        return MOUNTAIN;
    }

    public double randomFoodValue() {
        if (this == NONE) {
            return -1;
        }
        return averageFood + RngSupport.environment().nextDouble(-Settings.FOOD_MAX_VARIANCE, Settings.FOOD_MAX_VARIANCE);
    }

    public double randomDangerValue() {
        if (this == NONE) {
            return -1;
        }
        return averageDanger + RngSupport.environment().nextDouble(-Settings.DANGER_MAX_VARIANCE, Settings.DANGER_MAX_VARIANCE);
    }

    public boolean hasEnoughFood(double value) {
        return this != NONE && value >= Settings.FOOD_THRESHOLD;
    }

    public boolean isTooDangerous(double value) {
        return this != NONE && value >= Settings.DANGER_THRESHOLD;
    }

    public char mapSymbol() {
        return mapSymbol;
    }
}