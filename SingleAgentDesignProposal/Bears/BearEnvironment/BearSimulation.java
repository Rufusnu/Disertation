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
import java.util.concurrent.atomic.LongAdder;

/** A simulator for the vacuum cleaning world environment. */
public class BearSimulation extends Simulation {

	private Map<Integer, BearAgent> agentsById;
	private Map<DeathCause, Integer> deathCauseCounts;
	private int lastAgentId;

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
		agentsById = new HashMap<Integer, BearAgent>();
		deathCauseCounts = new HashMap<DeathCause, Integer>();
		lastAgentId = -1;
		BearSimulationBenchmark.Counters benchmark = Settings.BENCHMARK ? new BearSimulationBenchmark.Counters() : null;

		for (int i = 0; i < Settings.AGENTS_NUMBER_SINGLE_EXECUTION; i++) {
            BearAgent bearAgent = new BearAgent(i);
            lastAgentId = bearAgent.getId();
            ((BearEnvironment) this.environment).setAgentRandomCoords(bearAgent.getId()); // put agent on a random location on map
            if (Settings.VERBOSE) {
                System.out.println(bearAgent + " created.");
            }
			agentsById.put(bearAgent.getId(), bearAgent);
		}
		int initialBearCount = agentsById.size();
        ((BearState)initState).displayNoPrompt();

		System.out.println("Bears created.");

		System.out.println("Starting Bears...");
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) { // try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(THREAD_COUNT, agentList.size()))) {
			System.out.println("Bears started.");

			long endTimeMillis = System.currentTimeMillis() + (Settings.SIMULATION_LENGTH * 1000L);
			if (Settings.BENCHMARK) {
				while (System.currentTimeMillis() < endTimeMillis) {
					List<BearAgent> tickAgents = snapshotAgents();
					if (tickAgents.isEmpty()) {
						System.out.println("Bears went extinct after " + benchmark.ticks + " ticks.");
						break;
					}

					long loopStartNs = System.nanoTime();
					long planStartNs = System.nanoTime();
					BearSimulationBenchmark.PlanResult planResult = planActions(tickAgents, executor);
					benchmark.recordPlan(System.nanoTime() - planStartNs, planResult.breakdown);

					long commitStartNs = System.nanoTime();
					BearSimulationBenchmark.CommitBreakdown commitBreakdown = commitActions(planResult.plannedActions);
					benchmark.recordCommit(System.nanoTime() - commitStartNs, commitBreakdown);
					benchmark.recordLoop(System.nanoTime() - loopStartNs);
				}
			} else {
				while (System.currentTimeMillis() < endTimeMillis) {
					List<BearAgent> tickAgents = snapshotAgents();
					if (tickAgents.isEmpty()) {
						break;
					}

					Map<Integer, Action> plannedActions = planActionsNoBenchmark(tickAgents, executor);
					commitActionsNoBenchmark(plannedActions);
				}
			}

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Exception during execution of Executor Service:");
            System.out.println(e);
        }
		if (Settings.BENCHMARK && benchmark != null) {
			benchmark.printSummary();
		}

		System.out.println("Initial bear count: " + initialBearCount);
		System.out.println("End bear count: " + agentsById.size());
		printDeathCauseSummary();
        System.out.println("END of setup.");
	}

	private void printDeathCauseSummary() {
		System.out.println("Death causes:");
		if (deathCauseCounts.isEmpty()) {
			System.out.println(" - none");
			return;
		}

		for (Map.Entry<DeathCause, Integer> deathCauseCount : deathCauseCounts.entrySet()) {
			System.out.println(" - " + deathCauseCount.getKey() + ": " + deathCauseCount.getValue());
		}
	}

	private List<BearAgent> snapshotAgents() {
		return new ArrayList<BearAgent>(agentsById.values());
	}

	private Map<Integer, Action> planActionsNoBenchmark(List<BearAgent> agentList, ExecutorService executor) throws Exception {
		List<Callable<Map.Entry<Integer, Action>>> tasks = new ArrayList<Callable<Map.Entry<Integer, Action>>>();

		for (BearAgent bearAgent : agentList) {
			tasks.add(() -> {
				Percept percept = this.environment.getPercept(bearAgent);
				Action action = bearAgent.decideNextAction(percept);
				return Map.entry(bearAgent.getId(), action);
			});
		}

		Map<Integer, Action> plannedActions = new HashMap<Integer, Action>();
		List<Future<Map.Entry<Integer, Action>>> futures = executor.invokeAll(tasks);
		for (Future<Map.Entry<Integer, Action>> future : futures) {
			Map.Entry<Integer, Action> plannedAction = future.get();
			plannedActions.put(plannedAction.getKey(), plannedAction.getValue());
		}
		return plannedActions;
	}

	private BearSimulationBenchmark.PlanResult planActions(List<BearAgent> agentList, ExecutorService executor) throws Exception {
		BearSimulationBenchmark.PlanBreakdown breakdown = new BearSimulationBenchmark.PlanBreakdown();
		LongAdder perceptNsAdder = new LongAdder();
		LongAdder decideActionNsAdder = new LongAdder();

		long taskBuildStartNs = System.nanoTime();
		List<Callable<Map.Entry<Integer, Action>>> tasks = new ArrayList<Callable<Map.Entry<Integer, Action>>>();

		// deferred unit of work for each agent - get percept and decide action
		// these will be executed in parallel by the executor service later on with executor.invokeAll(tasks)
		for (BearAgent bearAgent : agentList) {
			tasks.add(() -> {
				long perceptStartNs = System.nanoTime();
				Percept percept = this.environment.getPercept(bearAgent);
				perceptNsAdder.add(System.nanoTime() - perceptStartNs);

				long decideStartNs = System.nanoTime();
				Action action = bearAgent.decideNextAction(percept);
				decideActionNsAdder.add(System.nanoTime() - decideStartNs);
				return Map.entry(bearAgent.getId(), action);
			});
		}
		breakdown.taskBuildNs = System.nanoTime() - taskBuildStartNs;

		// use the deferred unit of work in order to retrieve the planned action for each agent in parallel
		Map<Integer, Action> plannedActions = new HashMap<Integer, Action>();
		long invokeAllStartNs = System.nanoTime();
		List<Future<Map.Entry<Integer, Action>>> futures = executor.invokeAll(tasks); // run the deferred units of work
		breakdown.invokeAllNs = System.nanoTime() - invokeAllStartNs;

		long collectFuturesStartNs = System.nanoTime();
		for (Future<Map.Entry<Integer, Action>> future : futures) { // retrieve the planned action for each agent
			Map.Entry<Integer, Action> plannedAction = future.get();  // the result for the futures is bearAgentId, plannedAction
			plannedActions.put(plannedAction.getKey(), plannedAction.getValue());
		}
		breakdown.collectFuturesNs = System.nanoTime() - collectFuturesStartNs;
		breakdown.perceptNs = perceptNsAdder.sum();
		breakdown.decideActionNs = decideActionNsAdder.sum();

		BearSimulationBenchmark.PlanResult result = new BearSimulationBenchmark.PlanResult();
		result.plannedActions = plannedActions;
		result.breakdown = breakdown;
		return result;
	}

	private BearSimulationBenchmark.CommitBreakdown commitActions(Map<Integer, Action> plannedActions) {
		BearSimulationBenchmark.CommitBreakdown breakdown = new BearSimulationBenchmark.CommitBreakdown();
		BearState bearState = (BearState) this.environment.currentState();
		BearActionEffects effects = new BearActionEffects();

		long actionContributeStartNs = System.nanoTime();
		for (Map.Entry<Integer, Action> plannedAction : plannedActions.entrySet()) {
			BearAgent bearAgent = agentsById.get(plannedAction.getKey());
			Action action = plannedAction.getValue();
			if (action == null) {
				continue;
			}
			if (bearAgent == null) {
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
		breakdown.actionContributeNs = System.nanoTime() - actionContributeStartNs;

		long applyDeathsStartNs = System.nanoTime();
		applyDeaths(effects);
		breakdown.applyDeathsNs = System.nanoTime() - applyDeathsStartNs;

		long applyReproductionsStartNs = System.nanoTime();
		applyReproductions(effects);
		breakdown.applyReproductionsNs = System.nanoTime() - applyReproductionsStartNs;

		long applyFoodReductionsStartNs = System.nanoTime();
		applyFoodReductions(bearState, effects);
		breakdown.applyFoodReductionsNs = System.nanoTime() - applyFoodReductionsStartNs;

		long applyMoveIntentsStartNs = System.nanoTime();
		applyMoveIntents(bearState, effects);
		breakdown.applyMoveIntentsNs = System.nanoTime() - applyMoveIntentsStartNs;

		long restoreRandomFoodStartNs = System.nanoTime();
        restoreRandomFood(); // todo move in another place
		breakdown.restoreRandomFoodNs = System.nanoTime() - restoreRandomFoodStartNs;

		long recordActionsStartNs = System.nanoTime();
		for (int i = 0; i < effects.performedActions(); i++) {
			bearState.agentPerformedAnAction();
		}
		breakdown.recordPerformedActionsNs = System.nanoTime() - recordActionsStartNs;
		return breakdown;
	}

	private void commitActionsNoBenchmark(Map<Integer, Action> plannedActions) {
		BearState bearState = (BearState) this.environment.currentState();
		BearActionEffects effects = new BearActionEffects();

		for (Map.Entry<Integer, Action> plannedAction : plannedActions.entrySet()) {
			BearAgent bearAgent = agentsById.get(plannedAction.getKey());
			Action action = plannedAction.getValue();
			if (action == null) {
				continue;
			}
			if (bearAgent == null) {
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

		applyDeaths(effects);
		applyReproductions(effects);
		applyFoodReductions(bearState, effects);
		applyMoveIntents(bearState, effects);
		restoreRandomFood();

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
        for (Coords coords : effects.eatIntents()) {
            bearState.reduceFood(coords.x, coords.y);
        }
    }

	private void applyReproductions(BearActionEffects effects) {
        for (Integer motherAgentId : effects.reproduceIntents()) {
            BearAgent childAgent = new BearAgent(++lastAgentId, 0);
            ((BearEnvironment) this.environment).setMotherCoords(childAgent.getId(), motherAgentId); // put agent on the moother location on map
            if (Settings.VERBOSE) {
                System.out.println(childAgent + " created.");
            }
			agentsById.put(childAgent.getId(), childAgent);
        }
    }

	private void applyDeaths(BearActionEffects effects) {
		for (Map.Entry<Integer, DeathCause> dieIntent : effects.dieIntents().entrySet()) {
			Integer agentId = dieIntent.getKey();
			DeathCause deathCause = dieIntent.getValue();

            ((BearEnvironment) this.environment).removeAgent(agentId); // remove agent from simulation
			BearAgent removedAgent = agentsById.remove(agentId);
			deathCauseCounts.merge(deathCause, 1, Integer::sum);
            if (Settings.VERBOSE) {
				System.out.println((removedAgent != null ? removedAgent : ("Robot#" + agentId)) + " removed. Cause: " + deathCause);
            }
        }
    }

	private void applyMoveIntents(BearState bearState, BearActionEffects effects) {
		for (Map.Entry<Coords, List<Integer>> moveClaim : effects.moveClaimsByTarget().entrySet()) {
			for (Integer agentId : moveClaim.getValue()) {
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
