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
	private boolean nearbyMaleBear;

	public static class NeighborCellInfo {
		private final Coords coords;
		private final double food;
		private final double danger;
		private final boolean blocked;
		private final int nearbyBearCount;

		public NeighborCellInfo(Coords coords, double food, double danger, boolean blocked, int nearbyBearCount) {
			this.coords = coords;
			this.food = food;
			this.danger = danger;
			this.blocked = blocked;
			this.nearbyBearCount = nearbyBearCount;
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

		public int nearbyBearCount() {
			return nearbyBearCount;
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
		Coords currentCoords = new Coords(x, y);
		currentCell = new NeighborCellInfo(currentCoords, currentFood, currentDanger, false, state.nearbyBearCount(agent.getId(), currentCoords));
		nearbyMaleBear = state.hasNearbyMaleBear(agent.getId());

		blocked = false;

		neighbors = new ArrayList<NeighborCellInfo>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) {
					continue;
				}

				int neighborX = x + dx;
				int neighborY = y + dy;
				if (!state.inBounds(neighborX, neighborY)) {
					continue;
				}

				Coords neighbor = new Coords(neighborX, neighborY);
				BearCell cell = state.getBearCell(neighborX, neighborY);
				boolean isBlocked = state.isWall(neighborX, neighborY);
				double food = cell == null ? -1 : cell.food();
				double danger = cell == null ? -1 : cell.danger();
				neighbors.add(new NeighborCellInfo(neighbor, food, danger, isBlocked, state.nearbyBearCount(agent.getId(), neighbor)));
			}
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

	public boolean nearbyMaleBear() {
		return nearbyMaleBear;
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
