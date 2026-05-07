package Bears.BearEnvironment;

import MASInterface.Environment.Cell;

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
}
