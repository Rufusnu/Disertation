package Bears.BearEnvironment;

import MASInterface.Agent.Action;

import java.util.Map;

final class BearSimulationBenchmark {
	private BearSimulationBenchmark() {
	}

	static final class PlanBreakdown {
		long taskBuildNs;
		long invokeAllNs;
		long collectFuturesNs;
		long perceptNs;
		long decideActionNs;
	}

	static final class PlanResult {
		Map<Integer, Action> plannedActions;
		PlanBreakdown breakdown;
	}

	static final class CommitBreakdown {
		long actionContributeNs;
		long applyDeathsNs;
		long applyReproductionsNs;
		long applyFoodReductionsNs;
		long applyMoveIntentsNs;
		long restoreRandomFoodNs;
		long recordPerformedActionsNs;
	}

	static final class Counters {
		long ticks;
		long totalLoopNs;
		long totalPlanActionsNs;
		long totalPlanTaskBuildNs;
		long totalPlanInvokeAllNs;
		long totalPlanCollectFuturesNs;
		long totalPlanPerceptNs;
		long totalPlanDecideActionNs;
		long totalCommitActionsNs;
		long totalActionContributeNs;
		long totalApplyDeathsNs;
		long totalApplyReproductionsNs;
		long totalApplyFoodReductionsNs;
		long totalApplyMoveIntentsNs;
		long totalRestoreRandomFoodNs;
		long totalRecordPerformedActionsNs;

		void recordPlan(long planActionsNs, PlanBreakdown breakdown) {
			totalPlanActionsNs += planActionsNs;
			totalPlanTaskBuildNs += breakdown.taskBuildNs;
			totalPlanInvokeAllNs += breakdown.invokeAllNs;
			totalPlanCollectFuturesNs += breakdown.collectFuturesNs;
			totalPlanPerceptNs += breakdown.perceptNs;
			totalPlanDecideActionNs += breakdown.decideActionNs;
		}

		void recordCommit(long commitActionsNs, CommitBreakdown breakdown) {
			totalCommitActionsNs += commitActionsNs;
			totalActionContributeNs += breakdown.actionContributeNs;
			totalApplyDeathsNs += breakdown.applyDeathsNs;
			totalApplyReproductionsNs += breakdown.applyReproductionsNs;
			totalApplyFoodReductionsNs += breakdown.applyFoodReductionsNs;
			totalApplyMoveIntentsNs += breakdown.applyMoveIntentsNs;
			totalRestoreRandomFoodNs += breakdown.restoreRandomFoodNs;
			totalRecordPerformedActionsNs += breakdown.recordPerformedActionsNs;
		}

		void recordLoop(long loopNs) {
			totalLoopNs += loopNs;
			ticks++;
		}

		void printSummary() {
			long totalPlanAccountedNs = totalPlanTaskBuildNs + totalPlanInvokeAllNs + totalPlanCollectFuturesNs;
			long totalPlanUnaccountedNs = Math.max(0L, totalPlanActionsNs - totalPlanAccountedNs);
			long totalCommitAccountedNs = totalActionContributeNs
					+ totalApplyDeathsNs
					+ totalApplyReproductionsNs
					+ totalApplyFoodReductionsNs
					+ totalApplyMoveIntentsNs
					+ totalRestoreRandomFoodNs
					+ totalRecordPerformedActionsNs;
			long totalCommitUnaccountedNs = Math.max(0L, totalCommitActionsNs - totalCommitAccountedNs);

			System.out.println("\n=== Benchmark timing summary ===");
			System.out.println("Ticks: " + ticks);
			System.out.println("Loop total: " + toMillis(totalLoopNs) + " ms");
			System.out.println("Plan actions: " + toMillis(totalPlanActionsNs) + " ms (" + percentage(totalPlanActionsNs, totalLoopNs) + "%)");
			System.out.println("  - Build planning tasks: " + toMillis(totalPlanTaskBuildNs) + " ms");
			System.out.println("  - Invoke all plan tasks: " + toMillis(totalPlanInvokeAllNs) + " ms");
			System.out.println("  - Collect plan futures: " + toMillis(totalPlanCollectFuturesNs) + " ms");
			System.out.println("  - Plan unaccounted overhead: " + toMillis(totalPlanUnaccountedNs) + " ms");
			System.out.println("  - Percept generation (workers, aggregate CPU time - non-additive): " + toMillis(totalPlanPerceptNs) + " ms");
			System.out.println("  - Action decision (workers, aggregate CPU time - non-additive): " + toMillis(totalPlanDecideActionNs) + " ms");
			System.out.println("Commit actions: " + toMillis(totalCommitActionsNs) + " ms (" + percentage(totalCommitActionsNs, totalLoopNs) + "%)");
			System.out.println("  - Action contribute: " + toMillis(totalActionContributeNs) + " ms");
			System.out.println("  - Apply deaths: " + toMillis(totalApplyDeathsNs) + " ms");
			System.out.println("  - Apply reproductions: " + toMillis(totalApplyReproductionsNs) + " ms");
			System.out.println("  - Apply food reductions: " + toMillis(totalApplyFoodReductionsNs) + " ms");
			System.out.println("  - Apply move intents: " + toMillis(totalApplyMoveIntentsNs) + " ms");
			System.out.println("  - Restore random food: " + toMillis(totalRestoreRandomFoodNs) + " ms");
			System.out.println("  - Record performed actions: " + toMillis(totalRecordPerformedActionsNs) + " ms");
			System.out.println("  - Commit unaccounted overhead: " + toMillis(totalCommitUnaccountedNs) + " ms");
			if (ticks > 0) {
				System.out.println("Avg loop/tick: " + toMillis(totalLoopNs / ticks) + " ms");
			}
			System.out.println("=== End benchmark timing summary ===\n");
		}

		private double toMillis(long nanos) {
			return nanos / 1_000_000.0;
		}

		private double percentage(long part, long whole) {
			if (whole == 0) {
				return 0.0;
			}
			return (part * 100.0) / whole;
		}
	}
}
