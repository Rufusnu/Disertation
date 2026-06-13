package Bears.BearEnvironment;

import MASInterface.Environment.Cell;
import MASInterface.Settings;

public class BearCell extends Cell {
    public BearCell() {
        setBearCellType(BearCellType.randomTerrainType());
    }

    public BearCellType bearCellType() {
        return (BearCellType) cellType();
    }

    public void setBearCellType(BearCellType newCellType) {
        setCellType(newCellType);
        setFood(newCellType.randomFoodValue());
        setDanger(newCellType.randomDangerValue());
    }

    /**
     * Regrows food but clamps it to the habitat's natural maximum
     * ({@link BearCellType#maxFood()}). Without this clamp, food added by the
     * per-tick restoration accumulates without bound on lightly-grazed cells,
     * letting the map's effective carrying capacity drift upward over time and
     * preventing the population from ever reaching a stationary state. NONE
     * cells hold no food.
     */
    @Override
    public void restoreFood() {
        double max = bearCellType().maxFood();
        if (max < 0) { // NONE / unusable cell
            this.food = -1;
            return;
        }
        this.food = Math.min(this.food + Settings.FOOD_GROWN_PER_TICK, max);
    }
}
