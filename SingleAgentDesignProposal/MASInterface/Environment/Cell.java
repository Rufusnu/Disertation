package MASInterface.Environment;

import MASInterface.Settings;

import java.nio.channels.SeekableByteChannel;
import java.util.Random;
import java.util.Set;

public abstract class Cell {
    public interface CellType {}

    protected double food;
    protected double danger;
    protected CellType cellType;

    // Food
    public double food() {
        return this.food;
    }
    public void setFood(double newFood) {
        this.food = newFood;
    }
    public boolean hasFood() {
        return this.food > 0;
    }
    public boolean hasEnoughFood() {
        return this.food >= Settings.FOOD_EATEN_PER_TICK;
    }
    public void reduceFood() {
        if (hasEnoughFood()) {
            this.food -= Settings.FOOD_EATEN_PER_TICK;
        } else {
            this.food = 0;
        }

    }
    public void restoreFood() {
        this.food += Settings.FOOD_GROWN_PER_TICK;
    }

    // Danger
    public double danger() {
        return this.danger;
    }
    public void setDanger(double newDanger) {
        this.danger = newDanger;
    }
    public boolean hasDanger() {
        return this.danger > 0;
    }
    public boolean isTooDangerous() {
        return false;
    }

    // Cell Type
    public CellType cellType() {
        return this.cellType;
    }
    public void setCellType(CellType newCellType) {
        this.cellType = newCellType;
    }
}
