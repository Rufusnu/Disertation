package Bears.BearEnvironment;

import Bears.BearAgent.BearAgent;
import MASInterface.Environment.State;
import MASInterface.Environment.Coords;
import MASInterface.Settings;
import Bears.Experiments.RngSupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** Represents a state in the vacuum world. */
public class BearState extends State {
    @FunctionalInterface
	private interface CellRenderer {
		String render(int x, int y);
	}

	private static final String EMPTY_CELL_SYMBOL = " ";
	private static final String AGENT_CELL_SYMBOL = "A";

	/* Constants for the initial state of the agent. */
	// protected static int INIT_X = 1;
	// protected static int INIT_Y = 1;

    // topology
    //
	private static final int BLOCKED = 2;

	/* Variables for the state of the agent. */
	protected Map<Integer, Coords> agentsCoords;
	private Map<Integer, BearAgent.Gender> agentGenders;
	private int[][] agentCounts;
	private int[][] maleAgentCounts;

	/** An array that contains the locations of objects in the world. */
	protected int[][] map;
    protected BearCell[][] cellGrid;

	protected int mapLength;
	private volatile int agentsActions = 0;

	/** Returns the default initial state for the vacuum world. */
	public static BearState getInitState(int mapLength) {

		BearState state;

		state = new BearState();
		state.mapLength = mapLength;
		state.generateMap(state);
		state.agentsCoords = new HashMap<Integer, Coords>();
		state.agentGenders = new HashMap<Integer, BearAgent.Gender>();
		state.agentCounts = new int[mapLength][mapLength];
		state.maleAgentCounts = new int[mapLength][mapLength];
		state.agentsActions = 0;
		return state;
	}

	/** Constructs a new vacuum state. */
	public BearState() {
	}

	public static BearState requireBearState(State state, String context) {
		if (state instanceof BearState) {
			return (BearState) state;
		}
		throw new IllegalArgumentException("ERROR - Argument to " + context + " is not of type BearState");
	}



	public void setAgentRandomCoords(int agentId) {
		putAgent(agentId, getFreeTile());
	}

	public void setAgentGender(int agentId, BearAgent.Gender gender) {
		BearAgent.Gender previousGender = agentGenders.put(agentId, gender);
		Coords coords = agentsCoords.get(agentId);
		if (coords == null) {
			return;
		}

		if (previousGender == BearAgent.Gender.MALE && gender != BearAgent.Gender.MALE) {
			decrementMaleOccupancy(coords);
		}
		if (previousGender != BearAgent.Gender.MALE && gender == BearAgent.Gender.MALE) {
			incrementMaleOccupancy(coords);
		}
	}

    public void setMotherCoords(Integer childAgentId, Integer motherAgentId) {
        putAgent(childAgentId, getAgentCoords(motherAgentId));
    }

    private Coords getRandomTile() {
        int upperbound = mapLength() - 2; // 0->(nIndex-2)
		int random_numberX = 1 + RngSupport.environment().nextInt(upperbound); // 1->(nIndex-1)
		int random_numberY = 1 + RngSupport.environment().nextInt(upperbound); // 1->(nIndex-1)

        return new Coords(random_numberX, random_numberY);
    }

    public void restoreRandomFood() {
        Coords coords = getRandomTile();

        this.cellGrid[coords.x][coords.y].restoreFood();
    }

	private Coords getFreeTile() {
		Coords coords = getRandomTile();

		while (isBlocked(coords.x, coords.y)) {
			coords = getRandomTile();
		}
		return coords;
	}

	public void generateMap(BearState state) {
		state.map = new int[state.mapLength][state.mapLength];
        generateRandomCells(state);
		padMap(state);
	}

    private void generateRandomCells(BearState state) {
		state.cellGrid = new BearCell[state.mapLength][state.mapLength];
        for (int i = 0; i < state.mapLength; i++) {
            for (int j = 0; j < state.mapLength; j++) {
                state.cellGrid[i][j] = new BearCell();
            }
        }
    }

	private void padMap(BearState state) {
		for (int i = 1; i < state.mapLength; i++) {
			state.map[i][0] = BLOCKED;
			state.map[i][mapLength - 1] = BLOCKED;
            state.cellGrid[i][0].setBearCellType(BearCellType.NONE);
            state.cellGrid[i][mapLength - 1].setBearCellType(BearCellType.NONE);
		}
		for (int j = 0; j < state.mapLength; j++) {
			state.map[0][j] = BLOCKED;
			state.map[mapLength - 1][j] = BLOCKED;
            state.cellGrid[0][j].setBearCellType(BearCellType.NONE);
            state.cellGrid[mapLength - 1][j].setBearCellType(BearCellType.NONE);
		}
	}

    /** Reduces food from the specified location. */
    public void reduceFood(int x, int y) {
        synchronized (this) {
            cellGrid[x][y].reduceFood();
        }
    }

	/** Returns true if the specified location is a wall. */
	public boolean isBlocked(int x, int y) {
		return map[x][y] == BLOCKED || agentCount(x, y) > 0;
	}

	/** Returns true if the specified location is a wall cell, ignoring agent occupancy. */
	public boolean isWall(int x, int y) {
		return !inBounds(x, y) || map[x][y] == BLOCKED;
	}



	/** Returns true if in the specified location is an agent. */
	public boolean isAgentOnTile(Coords coords) {
		return agentCount(coords.x, coords.y) > 0;
	}

	/** Returns true if the location is within bounds of the state's map. */
	public boolean inBounds(int x, int y) {
		return x >= 0 && x < mapLength && y >= 0 && y < mapLength;
	}

	/** Returns the bear cell at coordinates, or null when unavailable. */
	public BearCell getBearCell(int x, int y) {
		if (!inBounds(x, y)) {
			return null;
		}
		return cellGrid[x][y];
	}

	/** Returns the map length. */
	public int mapLength() {
		return mapLength;
	}

	/** Returns the coordinates of an agent using its Id. */
	public Coords getAgentCoords(int agentId) {
		if (agentsCoords.containsKey(agentId)) {
			return agentsCoords.get(agentId);
		}
		return new Coords(-1,-1);
	}

	public boolean hasNearbyMaleBear(int agentId) {
		Coords agentCoords = getAgentCoords(agentId);
		if (agentCoords.x == -1 || agentCoords.y == -1) {
			return false;
		}
		BearAgent.Gender gender = agentGenders.get(agentId);

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				int x = agentCoords.x + dx;
				int y = agentCoords.y + dy;
				int nearbyMales = maleCount(x, y);
				if (dx == 0 && dy == 0 && gender == BearAgent.Gender.MALE) {
					nearbyMales--;
				}
				if (nearbyMales > 0) {
					return true;
				}
			}
		}

		return false;
	}

	public int nearbyBearCount(int agentId, Coords center) {
		Coords agentCoords = getAgentCoords(agentId);
		int nearbyBears = 0;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				nearbyBears += agentCount(center.x + dx, center.y + dy);
			}
		}

		if (Math.abs(agentCoords.x - center.x) <= 1 && Math.abs(agentCoords.y - center.y) <= 1) {
			nearbyBears--;
		}

		return Math.max(0, nearbyBears);
	}

	/** Updates the coordinates value of an agents using its Id as identification. */
	public void updateAgentCoords(int agentId, Coords coords) {
//		agentsCoords.replace(agentId, coords);
		Coords oldCoords = agentsCoords.get(agentId);
		BearAgent.Gender gender = agentGenders.get(agentId);
		if (oldCoords != null) {
			decrementOccupancy(oldCoords);
			if (gender == BearAgent.Gender.MALE) {
				decrementMaleOccupancy(oldCoords);
			}
		}
		agentsCoords.replace(agentId, coords);
		incrementOccupancy(coords);
		if (gender == BearAgent.Gender.MALE) {
			incrementMaleOccupancy(coords);
		}
	}

	/** Insert the agent for the first time in the hashmap. */
	public void putAgent(int agentId, Coords coords) {
		if (!agentsCoords.containsKey(agentId)) {
			agentsCoords.put(agentId, coords);
			incrementOccupancy(coords);
			if (agentGenders.get(agentId) == BearAgent.Gender.MALE) {
				incrementMaleOccupancy(coords);
			}
		} else {
			System.out.println("There is already an MASInterface.Agent with id: " + agentId);
		}
	}

	/** Removes an agent from the state by id. */
	public void removeAgent(int agentId) {
		Coords oldCoords = agentsCoords.remove(agentId);
		BearAgent.Gender gender = agentGenders.remove(agentId);
		if (oldCoords != null) {
			decrementOccupancy(oldCoords);
			if (gender == BearAgent.Gender.MALE) {
				decrementMaleOccupancy(oldCoords);
			}
		}
	}

	private void incrementMaleOccupancy(Coords coords) {
		if (inBounds(coords.x, coords.y)) {
			maleAgentCounts[coords.x][coords.y]++;
		}
	}

	private void decrementMaleOccupancy(Coords coords) {
		if (inBounds(coords.x, coords.y) && maleAgentCounts[coords.x][coords.y] > 0) {
			maleAgentCounts[coords.x][coords.y]--;
		}
	}

	private void incrementOccupancy(Coords coords) {
		if (inBounds(coords.x, coords.y)) {
			agentCounts[coords.x][coords.y]++;
		}
	}

	private void decrementOccupancy(Coords coords) {
		if (inBounds(coords.x, coords.y) && agentCounts[coords.x][coords.y] > 0) {
			agentCounts[coords.x][coords.y]--;
		}
	}

	private int agentCount(int x, int y) {
		if (!inBounds(x, y)) {
			return 0;
		}
		return agentCounts[x][y];
	}

	private int maleCount(int x, int y) {
		if (!inBounds(x, y)) {
			return 0;
		}
		return maleAgentCounts[x][y];
	}

	public void agentPerformedAnAction() {
		synchronized (this) {
            agentsActions++;
        }
	}

	public int agentActionsNumber() {
		return agentsActions;
	}

	/**
	 * Prints an output of the state to the screen. This output includes a map as
	 * well as information about the agent's location and the direction it is
	 * facing. On the map, "A" denotes the agent.
	 */
	public void display() {
		displayNoPrompt();
		System.out.println("Press RETURN to continue.");

		BufferedReader console = new BufferedReader(new InputStreamReader(
				System.in));
		try {
			console.readLine();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return;
		}
	}

	public void displayNoPrompt() {
         if (Settings.VERBOSE) {
			printGrid(
					1,
					mapLength - 1,
					"   ",
					"+---",
					"| ",
					" | ",
					" +",
					"---+",
					(x, y) -> {
						if (agentCount(x, y) > 0) {
							return AGENT_CELL_SYMBOL;
						}
						return EMPTY_CELL_SYMBOL;
					}
			);

            if (Settings.VERBOSE_AGENTS) {
                for (Map.Entry<Integer, Coords> set :
                        agentsCoords.entrySet()) {
                    System.out.println("Location: (" + set.getValue().x + "," + set.getValue().y + ")");
                }
                System.out.println();
            }

            System.out.println("Printing CellTypes...");

			printGrid(
					0,
					mapLength,
					"   ",
					"+---",
					"| ",
					" | ",
					" +",
					"---+",
					(x, y) -> {
						BearCellType cellType = (BearCellType) cellGrid[x][y].cellType();
						return String.valueOf(cellType.mapSymbol());
					}
			);

			System.out.println();
        }
    }

	private void printGrid(
			int start,
			int end,
			String topIndexPrefix,
			String horizontalUnit,
			String rowPrefix,
			String cellSuffix,
			String rowSeparatorPrefix,
			String rowSeparatorUnit,
			CellRenderer cellRenderer
	) {
		for (int j = start; j < end; j++) {
			System.out.print(topIndexPrefix + j);
		}
		System.out.println();

		System.out.print(" ");
		for (int j = start; j < end; j++) {
			System.out.print(horizontalUnit);
		}
		System.out.println("+");

		for (int y = start; y < end; y++) {
			System.out.print(y + rowPrefix);
			for (int x = start; x < end; x++) {
				System.out.print(cellRenderer.render(x, y));
				System.out.print(cellSuffix);
			}
			System.out.println();

			System.out.print(rowSeparatorPrefix);
			for (int j = start; j < end - 1; j++) {
				System.out.print(rowSeparatorUnit);
			}
			System.out.println(rowSeparatorUnit);
		}
	}
}
