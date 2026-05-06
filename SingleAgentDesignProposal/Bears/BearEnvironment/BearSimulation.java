package Bears.BearEnvironment;

import MASInterface.Settings;
import MASInterface.Agent.Action;
import MASInterface.Agent.Percept;
import MASInterface.Environment.Coords;
import MASInterface.Environment.Simulation;
import MASInterface.Environment.State;
import Bears.BearAgent.BearAgent;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** A simulator for the vacuum cleaning world environment. */
public class BearSimulation extends Simulation {

	public BearSimulation(BearEnvironment bearEnvironment) {
		super(bearEnvironment);
	}

	public void start(State initState) {
		this.environment.setInitialState(initState);
		startNoPrompt(initState);
	}

	public void startNoPrompt(State initState) {
        this.environment.setInitialState(initState);
		System.out.println("Creating bears...");
		List<BearAgent> agentList = new ArrayList<BearAgent>();

		for (int i = 0; i < Settings.AGENTS_NUMBER_SINGLE_EXECUTION; i++) {
            BearAgent bearAgent = new BearAgent(i, this.environment);
            ((BearEnvironment) this.environment).setAgentRandomCoords(bearAgent.getId()); // put agent on a random location on map
            if (Settings.VERBOSE) {
                System.out.println(bearAgent + " created.");
            }
			agentList.add(bearAgent);
		}
        ((BearState)initState).displayNoPrompt();

		System.out.println("Bears created.");

		System.out.println("Starting Bears...");
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) { // try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(THREAD_COUNT, agentList.size()))) {
			System.out.println("Bears started.");

			long endTimeMillis = System.currentTimeMillis() + (Settings.SIMULATION_LENGTH * 1000L);
			while (System.currentTimeMillis() < endTimeMillis) {
				Map<Integer, Action> plannedActions = planActions(agentList, executor);
				commitActions(agentList, plannedActions);
			}

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Exception during execution of Executor Service:");
            System.out.println(e);
        }
        System.out.println("END of setup.");
	}

	private Map<Integer, Action> planActions(List<BearAgent> agentList, ExecutorService executor) throws Exception {
		List<Callable<Map.Entry<Integer, Action>>> tasks = new ArrayList<Callable<Map.Entry<Integer, Action>>>();

		// deferred unit of work for each agent - get percept and decide action
		// these will be executed in parallel by the executor service later on with executor.invokeAll(tasks)
		for (BearAgent bearAgent : agentList) {
			tasks.add(() -> {
				Percept percept = this.environment.getPercept(bearAgent);
				Action action = bearAgent.decideNextAction(percept);
				return Map.entry(bearAgent.getId(), action);
			});
		}

		// use the deferred unit of work in order to retrieve the planned action for each agent in parallel
		Map<Integer, Action> plannedActions = new HashMap<Integer, Action>();
		List<Future<Map.Entry<Integer, Action>>> futures = executor.invokeAll(tasks); // run the deferred units of work
		for (Future<Map.Entry<Integer, Action>> future : futures) { // retrieve the planned action for each agent
			Map.Entry<Integer, Action> plannedAction = future.get();  // the result for the futures is bearAgentId, plannedAction
			plannedActions.put(plannedAction.getKey(), plannedAction.getValue());
		}
		return plannedActions;
	}

	private void commitActions(List<BearAgent> agentList, Map<Integer, Action> plannedActions) {
		BearState bearState = (BearState) this.environment.currentState();
		BearActionEffects effects = new BearActionEffects();

		for (BearAgent bearAgent : agentList) {
			Action action = plannedActions.get(bearAgent.getId());
			if (action == null) {
				continue;
			}

			try {
				action.contributeToStep(bearAgent, bearState, effects);
			} catch (Exception e) {
				System.out.println(e);
			}
			if (Settings.VERBOSE_AGENTS) {
				System.out.println(" Action: " + action + ";  (" + bearAgent + ")");
			}
		}

		applyFoodReductions(bearState, effects);
		applyMoveIntents(bearState, effects);
        restoreRandomFood(); // todo move in another place

		for (int i = 0; i < effects.performedActions(); i++) {
			bearState.agentPerformedAnAction();
		}
	}

    private void restoreRandomFood() {
		int randomNumberOfFoodRestored = ThreadLocalRandom.current().nextInt(Settings.MAP_LENGTH * 2 + 1);

        while (randomNumberOfFoodRestored-- > 0) {
            ((BearEnvironment) this.environment).restoreRandomFood();
        }
    }

    private void applyFoodReductions(BearState bearState, BearActionEffects effects) {
        for (Coords coords : effects.foodReductions()) {
            bearState.reduceFood(coords.x, coords.y);
        }
    }



	private void applyMoveIntents(BearState bearState, BearActionEffects effects) {
		for (Map.Entry<Coords, List<Integer>> moveClaim : effects.moveClaimsByTarget().entrySet()) {
			if (moveClaim.getValue().size() == 1) {
				int agentId = moveClaim.getValue().get(0);
				bearState.updateAgentCoords(agentId, moveClaim.getKey());
			}
		}
	}

	public static void SingleExecution() {
		System.out.println("The Bears World Bears.BearAgent Test");
		System.out.println("-----------------------------------");
		System.out.println();

		BearEnvironment bearEnvironment = new BearEnvironment();
		BearSimulation bearSimulation = new BearSimulation(bearEnvironment);
		BearState initState = BearState.getInitState(Settings.MAP_LENGTH);

		/** starts simulation */
		System.out.println("- - - - - - START - - - - - -");
        bearSimulation.start(initState);

        System.out.println("\nAgents finished the task after " + initState.agentActionsNumber() + " actions.");
        initState.displayNoPrompt();
        System.out.println("- - - - - -  END  - - - - - -");
	}

	// public static void PerformanceExecution() {
	// 	System.out.println("The Vacuum Cleaner World MASInterface.Agent Test");
	// 	System.out.println("-----------------------------------");
	// 	System.out.println();

	// 	int[] actionsPerformed = new int[10];

	// 	BearEnvironment bearEnvironment;
	// 	BearSimulation bearSimulation;
	// 	BearState initState;

	// 	for (int nbrBears = 1; nbrBears <= 8; nbrBears++) {
	// 		actionsPerformed[nbrBears] = 0;

	// 		for (int i = 0; i < TEST_EXECUTIONS_PER_AGENT_NUMBER; i++) {
    //             bearEnvironment = new BearEnvironment();
    //             bearSimulation = new BearSimulation(bearEnvironment);
	// 			initState = BearState.getInitState(MAP_LENGTH);

	// 			/** starts simulation */
    //             bearSimulation.startNoPrompt(initState);

	// 			actionsPerformed[nbrBears] += initState.agentActionsNumber();
	// 		}
	// 	}

    //     if (Settings.VERBOSE) {
    //         for (int nbrBears = 1; nbrBears <= 8; nbrBears++) {
    //             actionsPerformed[nbrBears] = actionsPerformed[nbrBears] / nbrBears / TEST_EXECUTIONS_PER_AGENT_NUMBER;
    //             System.out.println("Number of Bears= (" + nbrBears + ");  1000 Each Runs Average Performed Actions= (" + actionsPerformed[nbrBears] + ")");
    //         }
    //     }
	// }

	/**
	 * Starts the program.
	 */
	public static void main(String[] args) {
		// boolean test = false;
		// if (test) {
		// 	PerformanceExecution();
		// } else {
		SingleExecution();
		// }
	}

}
