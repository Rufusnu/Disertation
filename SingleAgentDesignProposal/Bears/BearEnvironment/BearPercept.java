package Bears.BearEnvironment;

import MASInterface.Agent.Agent;
import MASInterface.Agent.Percept;
import MASInterface.Environment.Coords;

import java.util.ArrayList;
import java.util.List;

/** A percept in the vacuum cleaning world. */
public class BearPercept extends Percept {

	protected boolean blocked;
	private NeighborCellInfo currentCell;
	private List<NeighborCellInfo> neighbors;

	public static class NeighborCellInfo {
		private final Coords coords;
		private final double food;
		private final double danger;
		private final boolean blocked;

		public NeighborCellInfo(Coords coords, double food, double danger, boolean blocked) {
			this.coords = coords;
			this.food = food;
			this.danger = danger;
			this.blocked = blocked;
		}

		public Coords coords() {
			return coords;
		}

		public double food() {
			return food;
		}

		public double danger() {
			return danger;
		}

		public boolean blocked() {
			return blocked;
		}
	}

	/**
	 * Constructs a vacuum world percept. If the agent is in a square that has
	 * dirt, then creates a percept that sees dirt.
	 */
	public BearPercept(BearState state, Agent agent) throws Exception {

		super(state, agent);

		Coords agentCoords = state.getAgentCoords(agent.getId());
		int x, y;

		x = agentCoords.x;
		y = agentCoords.y;

		if (agentCoords.x == -1 || agentCoords.y == -1) {
			throw new Exception("Coordinates are invalid for " + agent);
		}

		BearCell currentBearCell = state.getBearCell(x, y);
		double currentFood = currentBearCell == null ? -1 : currentBearCell.food();
		double currentDanger = currentBearCell == null ? -1 : currentBearCell.danger();
		currentCell = new NeighborCellInfo(new Coords(x, y), currentFood, currentDanger, false);

		blocked = false;

		neighbors = new ArrayList<NeighborCellInfo>();
		for (Coords neighbor : state.getNeighborCoords8(agent.getId())) {
			BearCell cell = state.getBearCell(neighbor.x, neighbor.y);
			boolean isBlocked = state.isBlocked(neighbor.x, neighbor.y);
			double food = cell == null ? -1 : cell.food();
			double danger = cell == null ? -1 : cell.danger();
			neighbors.add(new NeighborCellInfo(neighbor, food, danger, isBlocked));
		}

	}

	/**
	 * Returns true if the percept reflects that the square immediately in front
	 * of the agent contains an obstacle.
	 */
	public boolean seeBlocked() {

		return blocked;
	}

	public List<NeighborCellInfo> neighborCells() {
		return neighbors;
	}

	public NeighborCellInfo currentCell() {
		return currentCell;
	}

	public String toString() {

		StringBuffer pstring;

		pstring = new StringBuffer(5);
		if (blocked)
			pstring.append("   (BLOCKED)");
		else
			pstring.append("     (CLEAR)");
		return pstring.toString();
	}
}
