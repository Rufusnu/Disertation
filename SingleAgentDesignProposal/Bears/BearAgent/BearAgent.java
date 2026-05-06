package Bears.BearAgent;

import Bears.BearAgent.Actions.Eat;
import Bears.BearAgent.Actions.Move;
import Bears.BearEnvironment.BearPercept;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Environment.Coords;
import MASInterface.Environment.Environment;
import MASInterface.Agent.Percept;

import java.util.List;

/** The Vacuum Cleaning MASInterface.Agent - it is a simple reactive agent*/
public class BearAgent extends Agent {

    /** a deliberative agent stores its internal state - it may keep a history in the environment*/
    private final int id;
    private BearPercept currentPercept;
    private final Environment bearEnvironment; // reference to the environment (blackboard approach)

    public BearAgent(int id, Environment environment) {
        this.id = id;
        this.bearEnvironment = environment;
    }

    public int getId() {
        return id;
    }

    public void see(Percept percept) {
        currentPercept = (BearPercept) percept;
    }

    public Action selectAction() {
        BearPercept.NeighborCellInfo bestCell = chooseBestNeighbor(currentPercept.neighborCells(), currentPercept.currentCell());
        if (bestCell == null || bestCell.coords().equals(currentPercept.currentCell().coords())) {
            return new Eat();
        }
        return new Move(bestCell.coords());
    }

    private BearPercept.NeighborCellInfo chooseBestNeighbor(
            List<BearPercept.NeighborCellInfo> neighbors,
            BearPercept.NeighborCellInfo currentCell
    ) {
        BearPercept.NeighborCellInfo bestCell = currentCell;
        double bestScore = Double.NEGATIVE_INFINITY;

        if (currentCell != null) {
            bestScore = scoreNeighbor(currentCell);
        }

        for (BearPercept.NeighborCellInfo neighbor : neighbors) {
            if (neighbor.blocked()) {
                continue;
            }

            double score = scoreNeighbor(neighbor);
            if (score > bestScore) {
                bestScore = score;
                bestCell = neighbor;
            }
        }

        return bestCell;
    }

    private double scoreNeighbor(BearPercept.NeighborCellInfo neighbor) {
        double foodWeight = 1.0;
        double dangerWeight = 1.25;
        return foodWeight * neighbor.food() - dangerWeight * neighbor.danger();
    }

    public String toString() {
        return "Robot#" + id;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof BearAgent)) {
            return false;
        }
        BearAgent bearAgent = (BearAgent) object;

        return bearAgent.id == this.id;
    }

    public Action decideNextAction(Percept percept) {
        this.see(percept);
        return this.selectAction();
    }
}