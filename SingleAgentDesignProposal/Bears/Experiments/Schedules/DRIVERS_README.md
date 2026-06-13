# Driver scenarios — Romanian brown bear, 2005–2021

Each `driver_*.csv` is a time-varying parameter schedule (`year,parameter,value`)
that perturbs the calibrated baseline to represent a real management or
environmental change. The simulation clock maps **simulation year 0 = calendar
2005**, so the reference window (2005–2021) is **years 0–16**.

Run each driver as a scenario against the **same baseline** (same snapshot /
balanced init, same seed) and compare the resulting population trajectory and
MAE/RMSE vs the census in `reference-data/romania_brown_bear_population.csv`. The
difference from the no-driver baseline isolates that driver's effect.

## The drivers

| File | Real event | Calendar (sim yr) | Parameter change |
|------|-----------|-------------------|------------------|
| `driver_2007_eu_protection.csv` | EU accession; bear strictly protected (Habitats Directive) | 2007 (yr 2) | danger mortality −15% |
| `driver_2016_hunting_ban.csv` | Trophy-hunting ban (Oct 2016) | 2016 (yr 11) | danger mortality −30% |
| `driver_2021_intervention_cull.csv` | Law permitting culling of "problem" bears | 2021 (yr 16) | danger mortality +30% |
| `driver_anthropogenic_food.csv` | Rising access to garbage/crops/tourist & hunting feed | 2010/15/20 (yr 5/10/15) | food regrowth +7→+20% |
| `driver_habitat_loss.csv` | Carpathian logging degrading habitat/wild food | 2008/14/19 (yr 3/9/14) | forest food −5→−15% |
| `driver_combined_realistic.csv` | All of the above layered together | — | combined |

## Baseline values these perturb

- `BEAR_DANGER_DEATH_RATE_PER_TICK = 0.0000187596` (human-caused mortality: hunting, poaching, roads, conflict)
- `FOOD_GROWN_PER_TICK = 0.0045` (food regrowth rate ≈ carrying capacity)
- `FOREST_AVG_FOOD = 0.6` (natural forest food ceiling)

## Notes

- The change **magnitudes are hypotheses to test**, not measured values. They are
  defensible first guesses; sweep them to see how sensitive the fit is (that
  sensitivity is itself a result).
- Values are **absolute**. If you change the baseline calibration, re-anchor the
  driver values (the percentages above tell you how).
- The 2021 driver mostly affects projections **past** the comparison window —
  run a few years beyond year 16 to see it.
- These schedules change *parameters only*; they do not alter agent behaviour.
