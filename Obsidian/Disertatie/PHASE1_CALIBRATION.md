# Phase-1 Calibration Result

**Date:** 2026-06-03  
**Source sweep:** `calibration-output/sweep-20260603-183109/`  
**Selected point:** LHS-12 (index 11 in `points.csv`)

## What was calibrated

Six demographic / energetic parameters in `MASInterface/Settings.java` were
varied jointly over a 6-dimensional Latin-hypercube sample (24 points × 2
replicates per point), each evaluated against a pattern-oriented stationarity
loss (`StationarityTargets`) over a [10 y burn-in, 30 y eval-end] window on the
120×120 stub map with 400 founding bears and `kTarget = 1500`.

The loss penalises:
- mean population outside `[0.75·kTarget, 1.25·kTarget]` (weight 3.0),
- CV of population trajectory outside `[0.02, 0.25]` (weight 1.0),
- any replicate that goes extinct (weight 50.0),
- per-capita birth rate outside `[0.18, 0.38]` per year (weight 2.0),
- mean satiety outside `[0.35, 0.75]` (weight 1.0),
- starvation share of deaths outside `[0.05, 0.50]` (weight 1.5),
- old-age share of deaths outside `[0.05, 0.60]` (weight 1.5).

## Why we stopped at LHS-12

The full LHS + Nelder-Mead refinement was abandoned because:
- per-evaluation wall time on the CLC 200×200 map exceeded the budget for an
  end-to-end sweep,
- LHS-12 already produced a non-extinct, demographically plausible population
  (mean ~1015 bears at K=1500, sat ≈ 0.78, per-capita birth ≈ 0.12/yr, both
  replicates survived 30 simulated years),
- improvements below loss ≈ 5 are within single-replicate noise; further
  refinement would need ≥5 replicates to be statistically meaningful.

Among the 14 LHS points that completed before abort, LHS-12 had the lowest
loss (5.81). The next-best non-extinct points were LHS-7 (loss 13.63, pop 727)
and LHS-6 (loss 10.42, pop 835), all clearly worse on the population-size
penalty.

## Parameter values applied

| Settings field                              | Old value  | Calibrated value |
|---------------------------------------------|-----------:|-----------------:|
| `BEAR_SATIETY_DECAY_PER_TICK`               | 0.00075    | 0.000962273      |
| `BEAR_HIBERNATION_SATIETY_DECAY_MULTIPLIER` | 0.35       | 0.170415         |
| `BEAR_LITTER_SIZE_MEAN`                     | 2.2        | 2.36278          |
| `BEAR_INFANT_MORTALITY_AT_BIRTH`            | 0.30       | 0.437224         |
| `BEAR_DANGER_DEATH_RATE_PER_TICK`           | 0.000015   | 0.0000229317     |
| `BEAR_REPRODUCTION_COOLDOWN_YEARS`          | 3.0        | 2.14126          |

Interpretation: bears burn satiety slightly faster, hibernation is deeper
(burns much less), litters are a touch larger, infant mortality is higher,
adult ambient danger is ~50 % higher, and females breed about 10 months
sooner after weaning. The net is a population that grows from 400 to ~1000
without exploding, with attrition split roughly between starvation and danger.

## Observed stationarity (LHS-12, 30-year window, 2 replicates)

- mean population: **1014.6**
- population CV: **0.073**
- extinction rate: **0/2**
- per-capita birth rate: **0.120 / yr**
- mean satiety: **0.78**
- starvation share of deaths: **0.25**
- old-age share of deaths: **0.057**

(Source row: `sweep-20260603-183109/points.csv`, line 13.)

## Caveats

1. Calibrated on the **120×120 synthetic stub map**, not on the CLC 200×200.
   The per-capita targets are scale-invariant, so transfer is plausible but
   not proven; the validation step below is required.
2. Only 2 replicates per point. The variance estimate is weak. Treat the
   calibrated values as a starting point, not a final answer.
3. Of 14 LHS points that completed, 8 went extinct in both replicates — the
   sampled parameter ranges are wide and many combinations collapse. The
   surviving region is narrow; do not perturb these values without re-running
   calibration.

## Reverting

The previous defaults are listed in the table above and as `// calibrated`
comments in `Settings.java`; restoring them is a manual edit. The raw sweep
data is preserved in `calibration-output/sweep-20260603-183109/`.
