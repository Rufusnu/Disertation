# Important things to mention in the dissertation (derived from code)

This note is extracted directly from the current Java implementation and should be treated as the technical backbone of the written thesis.

## 1. Core simulation design

- The project uses a spatial agent-based model where each bear is an individual autonomous agent.
- Agent state includes: age, gender, satiety, pregnancy status, reproduction cooldown, and home-range anchor.
- Environment state includes: terrain cell type, food availability, danger level, occupancy counts, and male occupancy counts.
- Time is discrete and tick-based; each tick is mapped to a fraction of a year.

Relevant implementation:
- `SingleAgentDesignProposal/Bears/BearAgent/BearAgent.java`
- `SingleAgentDesignProposal/Bears/BearEnvironment/BearState.java`
- `SingleAgentDesignProposal/MASInterface/Settings.java`

## 2. Parallel architecture and why it matters

- Per tick, planning is parallelized: each agent computes percept + next action in worker tasks.
- Commit is centralized/serial: move intents, deaths, births, food updates, and conflict counting are applied consistently.
- This split gives deterministic world-state transitions and avoids race conditions in shared state.

Relevant implementation:
- `SingleAgentDesignProposal/Bears/BearEnvironment/BearSimulation.java`
- `SingleAgentDesignProposal/Bears/BearEnvironment/BearActionEffects.java`

## 3. Multithreading vs distributed execution (must be explicit in thesis)

- The project supports multithreaded execution and has benchmark timing instrumentation.
- For this fine-grained tick-synchronized model, distributed execution inside one run can be slower because synchronization and communication overhead can dominate per-agent work.
- Distributed execution is still useful for running many independent replicates/scenarios in batch (embarrassingly parallel workload).

Relevant implementation:
- `SingleAgentDesignProposal/Bears/BearEnvironment/BearSimulationBenchmark.java`
- `SingleAgentDesignProposal/Bears/Experiments/BatchRunner.java`

## 4. Ecological realism already present in code

- Sex-specific mortality multipliers.
- Child mortality multiplier and infant mortality at birth.
- Gestation, reproduction cooldown, minimum reproductive age.
- Seasonal hibernation window with altered satiety decay and danger multipliers.
- Habitat-aware initial placement and movement preferences with crowding and home-range effects.

Relevant implementation:
- `SingleAgentDesignProposal/Bears/BearAgent/BearAgent.java`
- `SingleAgentDesignProposal/MASInterface/Settings.java`
- `SingleAgentDesignProposal/Bears/BearEnvironment/BearState.java`

## 5. Reproducibility and scientific workflow

- Seeded RNG support for controlled replicates.
- Run configuration object encapsulating scenario, replicate index, seed, max ticks, and overrides.
- Per-tick metrics recorder with births/deaths/conflicts/population/satiety/age outputs.
- Time-varying parameter schedules applied during simulation years.
- Calibration tooling (Latin hypercube, Nelder-Mead, evaluators).

Relevant implementation:
- `SingleAgentDesignProposal/Bears/Experiments/RngSupport.java`
- `SingleAgentDesignProposal/Bears/Experiments/RunConfig.java`
- `SingleAgentDesignProposal/Bears/Experiments/RunMetricsRecorder.java`
- `SingleAgentDesignProposal/Bears/Experiments/ScheduleApplier.java`
- `SingleAgentDesignProposal/Bears/Experiments/Calibration/*.java`

## 6. Data integration and map realism

- The simulator can load external preprocessed map grids (not only random map generation).
- This enables controlled studies for real landscapes and structured habitat scenarios.

Relevant implementation:
- `SingleAgentDesignProposal/Bears/BearEnvironment/MapGridLoader.java`

## 7. Experimental claims you can defend

- The model can test competing hypotheses by scenario toggles and schedules.
- The model can compare baseline vs intervention/counterfactual trajectories.
- The project supports quantitative outputs for ecological validity and computational performance.

## 8. Limitations to acknowledge

- Static global settings can reduce flexibility and can complicate strict isolation between runs if not reset.
- Tick barrier design introduces synchronization overhead that limits distributed intra-run gains.
- Real-world uncertainty in census and policy data can affect calibration confidence.

These limitations should be written as methodological constraints, not as project weaknesses.
