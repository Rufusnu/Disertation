package Bears.BearEnvironment;

import MASInterface.Settings;
import MASInterface.Agent.Action;
import MASInterface.Agent.Percept;
import MASInterface.Environment.Coords;
import MASInterface.Environment.Simulation;
import MASInterface.Environment.State;
import Bears.BearAgent.BearAgent;
import Bears.Experiments.RngSupport;
import Bears.Experiments.RunMetricsRecorder;
import Bears.Experiments.ScheduleApplier;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/** A simulator for the vacuum cleaning world environment. */
public class BearSimulation extends Simulation {

	private Map<Integer, BearAgent> agentsById;
	private Map<DeathCause, Integer> deathCauseCounts;
	private int lastAgentId;
	private BearState activeState;
	private RunMetricsRecorder metricsRecorder;
	private ScheduleApplier scheduleApplier;

	public BearSimulation(BearEnvironment bearEnvironment) {
		super(bearEnvironment);
	}

	/** Attaches a metrics recorder; sampled once per committed tick. */
	public void setMetricsRecorder(RunMetricsRecorder recorder) {
		this.metricsRecorder = recorder;
	}

	/**
	 * Attaches a {@link ScheduleApplier} that mutates {@link Settings} as the
	 * simulation crosses scheduled simulation years. Pass {@code null} (or an
	 * empty schedule) to disable.
	 */
	public void setScheduleApplier(ScheduleApplier scheduleApplier) {
		this.scheduleApplier = scheduleApplier;
	}

	public Map<Integer, BearAgent> agentsById() {
		return agentsById;
	}

	public void start(State initState) {
		this.environment.setInitialState(initState);
		startNoPrompt(initState);
	}

	public void startNoPrompt(State initState) {
		deathCauseCounts = new HashMap<DeathCause, Integer>();
		BearSimulationBenchmark.Counters benchmark = Settings.BENCHMARK ? new BearSimulationBenchmark.Counters() : null;

		if (Settings.SNAPSHOT_SOURCE != null && !Settings.SNAPSHOT_SOURCE.isBlank()) {
			// Restore a spun-up population + habitat from a snapshot instead of
			// generating random founders. This replaces both the map and the
			// founding cohort; the simulation clock still starts at tick 0.
			BearSnapshot.Loaded loaded;
			try {
				loaded = BearSnapshot.load(java.nio.file.Path.of(Settings.SNAPSHOT_SOURCE));
			} catch (java.io.IOException e) {
				throw new RuntimeException("Failed to load snapshot " + Settings.SNAPSHOT_SOURCE + ": " + e.getMessage(), e);
			}
			this.activeState = loaded.state;
			this.environment.setInitialState(loaded.state);
			this.agentsById = loaded.agentsById;
			this.lastAgentId = loaded.lastAgentId;
			Settings.MAP_LENGTH = loaded.state.mapLength();
			System.out.println("Restored snapshot: " + agentsById.size() + " bears from " + Settings.SNAPSHOT_SOURCE);
		} else if (Settings.BALANCED_INIT_SOURCE != null && !Settings.BALANCED_INIT_SOURCE.isBlank()) {
			// Generate founders from measured stable-state distributions and set
			// the map food to its drawn-down level, instead of forest-only
			// placement on a full-larder map.
			this.environment.setInitialState(initState);
			this.activeState = (BearState) initState;
			agentsById = new HashMap<Integer, BearAgent>();
			try {
				BalancedInitializer init = new BalancedInitializer(
						BalancedInitializer.load(java.nio.file.Path.of(Settings.BALANCED_INIT_SOURCE)));
				lastAgentId = init.apply((BearState) initState, agentsById,
						Settings.AGENTS_NUMBER_SINGLE_EXECUTION,
						Bears.Experiments.RngSupport.environment());
			} catch (java.io.IOException e) {
				throw new RuntimeException("Failed to load characterization "
						+ Settings.BALANCED_INIT_SOURCE + ": " + e.getMessage(), e);
			}
			System.out.println("Balanced init: " + agentsById.size() + " bears from "
					+ Settings.BALANCED_INIT_SOURCE);
		} else {
			this.environment.setInitialState(initState);
			this.activeState = (BearState) initState;
			System.out.println("Creating bears...");
			agentsById = new HashMap<Integer, BearAgent>();
			lastAgentId = -1;

			for (int i = 0; i < Settings.AGENTS_NUMBER_SINGLE_EXECUTION; i++) {
				BearAgent bearAgent = new BearAgent(i);
				lastAgentId = bearAgent.getId();
				((BearEnvironment) this.environment).setAgentRandomCoords(bearAgent.getId()); // put agent on a random location on map
				((BearEnvironment) this.environment).setAgentGender(bearAgent.getId(), bearAgent.getGender());
				if (Settings.VERBOSE) {
					System.out.println(bearAgent + " created.");
				}
				agentsById.put(bearAgent.getId(), bearAgent);
			}
			((BearState) initState).displayNoPrompt();
			System.out.println("Bears created.");
		}
		int initialBearCount = agentsById.size();

		// Record the founding cohort BEFORE any tick runs so the CSV exposes
		// the initial gender split, founding-pregnancy count, etc.
		if (metricsRecorder != null) {
			metricsRecorder.sampleInitialState(agentsById.values());
		}

		System.out.println("Starting Bears...");
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) { // try (ExecutorService executor = Executors.newFixedThreadPool(Math.min(THREAD_COUNT, agentList.size()))) {
			System.out.println("Bears started.");

			long endTimeMillis = System.currentTimeMillis() + (Settings.SIMULATION_LENGTH * 1000L);
			long maxTicks = Settings.SIMULATION_MAX_TICKS;
			long tick = 0;
			if (Settings.BENCHMARK) {
				while (shouldContinue(endTimeMillis, maxTicks, tick)) {
					List<BearAgent> tickAgents = snapshotAgents();
					if (tickAgents.isEmpty()) {
						System.out.println("Bears went extinct after " + benchmark.ticks + " ticks.");
						break;
					}
					if (Settings.POPULATION_CAP > 0 && tickAgents.size() > Settings.POPULATION_CAP) {
						System.out.println("Population cap " + Settings.POPULATION_CAP + " exceeded at tick " + tick + " (size=" + tickAgents.size() + "); aborting.");
						break;
					}

					applyScheduleForTick(tick);

					long loopStartNs = System.nanoTime();
					long planStartNs = System.nanoTime();
					double simulationYearForTick = tick * Settings.ONE_TICK_IN_YEARS;
					BearSimulationBenchmark.PlanResult planResult = planActions(tickAgents, executor, simulationYearForTick);
					benchmark.recordPlan(System.nanoTime() - planStartNs, planResult.breakdown);

					long commitStartNs = System.nanoTime();
					BearSimulationBenchmark.CommitBreakdown commitBreakdown = commitActions(planResult.plannedActions);
					benchmark.recordCommit(System.nanoTime() - commitStartNs, commitBreakdown);
					benchmark.recordLoop(System.nanoTime() - loopStartNs);
					tick++;
					sampleMetricsForTick(tick);
					if (maybeSaveSnapshot(tick)) break;
				}
			} else {
				while (shouldContinue(endTimeMillis, maxTicks, tick)) {
					List<BearAgent> tickAgents = snapshotAgents();
					if (tickAgents.isEmpty()) {
						break;
					}
					if (Settings.POPULATION_CAP > 0 && tickAgents.size() > Settings.POPULATION_CAP) {
						break;
					}

					applyScheduleForTick(tick);

					double simulationYearForTick = tick * Settings.ONE_TICK_IN_YEARS;
					Map<Integer, Action> plannedActions = planActionsNoBenchmark(tickAgents, executor, simulationYearForTick);
					commitActionsNoBenchmark(plannedActions);
					tick++;
					sampleMetricsForTick(tick);
					if (maybeSaveSnapshot(tick)) break;
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

	/**
	 * If a snapshot save is configured and the clock has reached the target
	 * year, writes the snapshot and signals the loop to stop. Returns true when
	 * the run should end (snapshot written).
	 */
	private boolean maybeSaveSnapshot(long tick) {
		if (Settings.SNAPSHOT_SAVE_PATH == null || Settings.SNAPSHOT_SAVE_PATH.isBlank()) {
			return false;
		}
		double simYear = tick * Settings.ONE_TICK_IN_YEARS;
		if (simYear < Settings.SNAPSHOT_SAVE_AT_YEAR) {
			return false;
		}
		try {
			BearSnapshot.save(java.nio.file.Path.of(Settings.SNAPSHOT_SAVE_PATH),
					activeState, agentsById, lastAgentId, tick);
			System.out.printf(java.util.Locale.ROOT,
					"Saved snapshot at year %.2f (%d bears) to %s%n",
					simYear, agentsById.size(), Settings.SNAPSHOT_SAVE_PATH);
		} catch (java.io.IOException e) {
			throw new RuntimeException("Failed to save snapshot to " + Settings.SNAPSHOT_SAVE_PATH + ": " + e.getMessage(), e);
		}
		return true;
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

	private boolean shouldContinue(long endTimeMillis, long maxTicks, long currentTick) {
		if (Settings.STOP_REQUESTED) {
			return false;
		}
		if (maxTicks > 0) {
			return currentTick < maxTicks;
		}
		return System.currentTimeMillis() < endTimeMillis;
	}

	private void sampleMetricsForTick(long tick) {
		if (metricsRecorder == null) {
			return;
		}
		metricsRecorder.sampleTick(tick, agentsById.values());
	}

	/**
	 * Applies any time-varying parameter changes whose simulation-year is
	 * &le; the year about to be simulated by tick {@code tick+1}. Called at
	 * the top of each tick loop iteration before planning, so the new values
	 * are visible during the upcoming tick. Year=0 entries therefore apply
	 * before tick 1 runs.
	 */
	private void applyScheduleForTick(long tick) {
		if (scheduleApplier == null || scheduleApplier.isEmpty()) {
			return;
		}
		double simulationYear = tick * Settings.ONE_TICK_IN_YEARS;
		scheduleApplier.applyDue(simulationYear, tick);
	}

	private Map<Integer, Action> planActionsNoBenchmark(List<BearAgent> agentList, ExecutorService executor, double simulationYear) throws Exception {
		List<Callable<Map.Entry<Integer, Action>>> tasks = new ArrayList<Callable<Map.Entry<Integer, Action>>>();

		for (BearAgent bearAgent : agentList) {
			tasks.add(() -> {
				Percept percept = this.environment.getPercept(bearAgent);
				Action action = bearAgent.decideNextAction(percept, simulationYear);
				return Map.entry(bearAgent.getId(), action);
			});
		}

		Map<Integer, Action> plannedActions = new java.util.TreeMap<Integer, Action>();
		List<Future<Map.Entry<Integer, Action>>> futures = executor.invokeAll(tasks);
		for (Future<Map.Entry<Integer, Action>> future : futures) {
			Map.Entry<Integer, Action> plannedAction = future.get();
			plannedActions.put(plannedAction.getKey(), plannedAction.getValue());
		}
		return plannedActions;
	}

	private BearSimulationBenchmark.PlanResult planActions(List<BearAgent> agentList, ExecutorService executor, double simulationYear) throws Exception {
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
				Action action = bearAgent.decideNextAction(percept, simulationYear);
				decideActionNsAdder.add(System.nanoTime() - decideStartNs);
				return Map.entry(bearAgent.getId(), action);
			});
		}
		breakdown.taskBuildNs = System.nanoTime() - taskBuildStartNs;

		// use the deferred unit of work in order to retrieve the planned action for each agent in parallel
		Map<Integer, Action> plannedActions = new java.util.TreeMap<Integer, Action>();
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

		long applyBirthsStartNs = System.nanoTime();
		applyBirths(effects);
		breakdown.applyBirthsNs = System.nanoTime() - applyBirthsStartNs;

		long applyFoodReductionsStartNs = System.nanoTime();
		applyFoodReductions(bearState, effects);
		breakdown.applyFoodReductionsNs = System.nanoTime() - applyFoodReductionsStartNs;

		long applyMoveIntentsStartNs = System.nanoTime();
		applyMoveIntents(bearState, effects);
		breakdown.applyMoveIntentsNs = System.nanoTime() - applyMoveIntentsStartNs;

		recordHumanConflictEvents(bearState, effects);

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
		applyBirths(effects);
		applyFoodReductions(bearState, effects);
		applyMoveIntents(bearState, effects);
		recordHumanConflictEvents(bearState, effects);
		restoreRandomFood();

		for (int i = 0; i < effects.performedActions(); i++) {
			bearState.agentPerformedAnAction();
		}
	}

    private void restoreRandomFood() {
		int randomNumberOfFoodRestored = Bears.Experiments.RngSupport.environment().nextInt(Settings.MAP_LENGTH * 2 + 1);

        while (randomNumberOfFoodRestored-- > 0) {
            ((BearEnvironment) this.environment).restoreRandomFood();
        }
    }

    private void applyFoodReductions(BearState bearState, BearActionEffects effects) {
        for (Coords coords : effects.eatIntents()) {
            bearState.reduceFood(coords.x, coords.y);
        }
    }

	private void applyBirths(BearActionEffects effects) {
        for (Integer motherAgentId : effects.birthIntents()) {
            int litterSize = sampleLitterSize();
            for (int cubIndex = 0; cubIndex < litterSize; cubIndex++) {
                if (RngSupport.environment().nextDouble() < Settings.BEAR_INFANT_MORTALITY_AT_BIRTH) {
                    // Perinatal loss: cub is born but does not survive to be added to the population.
                    continue;
                }
                BearAgent childAgent = new BearAgent(++lastAgentId, 0);
                ((BearEnvironment) this.environment).setMotherCoords(childAgent.getId(), motherAgentId);
                ((BearEnvironment) this.environment).setAgentGender(childAgent.getId(), childAgent.getGender());
                if (Settings.VERBOSE) {
                    System.out.println(childAgent + " created.");
                }
                agentsById.put(childAgent.getId(), childAgent);
                if (metricsRecorder != null) {
                    metricsRecorder.noteBirth();
                }
            }
        }
    }

    private int sampleLitterSize() {
        double sample = Settings.BEAR_LITTER_SIZE_MEAN
                + RngSupport.environment().nextGaussian() * Settings.BEAR_LITTER_SIZE_STD;
        int rounded = (int) Math.round(sample);
        if (rounded < 1) {
            return 1;
        }
        return rounded;
    }

    /**
     * Counts how many bears currently occupy a VILLAGE or ROAD cell. This is a
     * proxy for human-bear conflict exposure (cf. Pop et al. 2018 on Romanian
     * brown bear conflict patterns) and is reported per tick in run CSVs.
     */
    private void recordHumanConflictEvents(BearState bearState, BearActionEffects effects) {
        if (metricsRecorder == null) {
            return;
        }
        for (Integer agentId : agentsById.keySet()) {
            Coords coords = bearState.getAgentCoords(agentId);
            if (coords.x < 0 || coords.y < 0) {
                continue;
            }
            BearCell cell = bearState.getBearCell(coords.x, coords.y);
            if (cell == null) {
                continue;
            }
            Object type = cell.cellType();
            if (type == BearCellType.VILLAGE || type == BearCellType.ROAD) {
                effects.recordConflictEvent();
            }
        }
        metricsRecorder.noteConflictEvents(effects.conflictEvents());
    }

	private void applyDeaths(BearActionEffects effects) {
		for (Map.Entry<Integer, DeathCause> dieIntent : effects.dieIntents().entrySet()) {
			Integer agentId = dieIntent.getKey();
			DeathCause deathCause = dieIntent.getValue();

            ((BearEnvironment) this.environment).removeAgent(agentId); // remove agent from simulation
			BearAgent removedAgent = agentsById.remove(agentId);
			deathCauseCounts.merge(deathCause, 1, Integer::sum);
			if (metricsRecorder != null) {
				metricsRecorder.noteDeath(deathCause);
			}
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
