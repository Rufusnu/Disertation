# Phase-1 Calibration Implementation Note

Date: 2026-06-03

## What Was Implemented

1. New calibration module (Java)
   - Location: `SingleAgentDesignProposal/Bears/Experiments/Calibration/`
   - Added classes:
     - `CalibrationParam`
     - `CalibrationSpace`
     - `CandidatePoint`
     - `StationarityTargets`
     - `EvaluationResult`
     - `PointEvaluator`
     - `LatinHypercube`
     - `NelderMead`
     - `CalibrationRunner`

2. Calibration objective and method
   - Pattern-oriented stationarity loss (population band, CV, extinction, birth rate, satiety, starvation share, old-age share).
   - Search flow: Latin Hypercube sampling -> optional Nelder-Mead local refinement.
   - Reproducible seeds via SplitMix64-style derivation.

3. Runtime safety for calibration
   - Added `Settings.POPULATION_CAP` to bound runaway simulations.
   - `BearSimulation` tick loops now stop early if live population exceeds `POPULATION_CAP`.
   - `PointEvaluator` sets/restores `POPULATION_CAP` during candidate evaluation.

4. Applied calibrated defaults in `Settings.java`
   - File: `SingleAgentDesignProposal/MASInterface/Settings.java`
   - Updated fields:
     - `BEAR_SATIETY_DECAY_PER_TICK = 0.000962273`
     - `BEAR_HIBERNATION_SATIETY_DECAY_MULTIPLIER = 0.170415`
     - `BEAR_LITTER_SIZE_MEAN = 2.36278`
     - `BEAR_INFANT_MORTALITY_AT_BIRTH = 0.437224`
     - `BEAR_DANGER_DEATH_RATE_PER_TICK = 0.0000229317`
     - `BEAR_REPRODUCTION_COOLDOWN_YEARS = 2.14126`

5. Output artifacts
   - Calibration sweeps written under `calibration-output/sweep-*/`
   - Includes `manifest.txt`, `points.csv`, and best/convergence exports (when completed)

## Notes

- The CLI issue encountered was due to `BatchRunner` main not using `--scenarios/--replicates` style parsing in this code path; it uses positional output directory plus `-D` properties.
- Validation run uses:

```powershell
java --% -Xmx4g -DinitialBears=2000 -Dyears=30 -Dmap.source=reference-data/romania-map-clc2018.txt Bears.Experiments.BatchRunner ..\..\experiment-output
```
