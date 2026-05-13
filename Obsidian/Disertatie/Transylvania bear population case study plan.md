# Transylvania bear population case study — thesis plan

Framing the dissertation around a real, documented ecological event:
**why has the brown bear population in Transylvania (Romania) risen so
sharply over the last two decades?** The existing
[BearSimulation](../../SingleAgentDesignProposal/Bears/BearEnvironment/BearSimulation.java)
becomes the instrument used to test competing causal hypotheses for that
rise, with the multithreaded/distributed engine as the technical
contribution that makes the required experimental volume feasible.

## 1. The phenomenon

Romania holds the largest brown bear (*Ursus arctos*) population in the
European Union. Reported counts:

- Early 2000s: **≈ 5,500–6,000** bears nationally.
- 2016: **≈ 8,000**.
- 2023–2024 ICAS / INCDS Brașov genetic census: **≈ 13,000**.
- Romanian Ministry of Environment "optimal" carrying capacity:
  **≈ 4,000**.

So the observed population sits roughly **3× the official optimum** and
has grown at an apparent **≈ 5–7 %/yr** for two decades. This is the
target curve the thesis tries to reproduce and explain.

## 2. Competing hypotheses for the rise

These are the drivers most often cited in the literature and in the
Romanian press. Each maps cleanly to a parameter or rule in the current
ABM, which is what makes ABM the right tool here.

1. **Hunting moratorium / quota collapse** after 2016 (trophy hunting
   banned, then partially reinstated as "intervention quotas").
2. **Supplemental feeding** at game-management feeding stations
   ("hrănitori") — a direct food subsidy.
3. **Rural depopulation and land abandonment** in Transylvanian
   villages → forest/scrub expansion → more habitat.
4. **Anthropogenic food** at village edges (garbage, orchards,
   sheepfolds) → habituation and lower *effective* danger.
5. **Climate / shorter hibernation** → longer active season → more
   foraging time and better cub survival.
6. **Reduced competition / low poaching** inside protected areas.
7. **Detection bias**: better census methodology (genetic vs
   track-based) inflates the *apparent* rise. Must be addressed even if
   only to rule out.

## 3. Mapping hypotheses to existing model levers

The current model already exposes most of the required knobs in
[Settings.java](../../SingleAgentDesignProposal/MASInterface/Settings.java)
and [BearAgent.java](../../SingleAgentDesignProposal/Bears/BearAgent/BearAgent.java).

| Hypothesis | Lever in current model | What to add |
|---|---|---|
| Hunting ban | `BEAR_DANGER_DEATH_RATE_PER_TICK`, `VILLAGE_AVG_DANGER`, `ROAD_AVG_DANGER` | Time-varying danger rate; or a separate `HUNTING_QUOTA` removing N adults/yr. |
| Feeding stations | `FOREST_AVG_FOOD`, `FOOD_GROWN_PER_TICK` | New `FEEDING_STATION` cell type with high food, low danger, periodic refill. |
| Land abandonment | `FIELD_PERCENTAGE`, `FOREST_PERCENTAGE`, `VILLAGE_PERCENTAGE` | Time-varying terrain: gradual conversion of FIELD/VILLAGE → FOREST over decades. |
| Anthropogenic food | `VILLAGE_AVG_FOOD`, `VILLAGE_AVG_DANGER` | Raise village food, lower village danger over time (habituation). |
| Climate / longer active season | satiety decay, reproduction window | Seasonal cycle with hibernation months (no decay, no actions); shrink hibernation length over time. |
| Better census | n/a (post-processing) | Apply a noisy "observer" function to the true population to compare against the *reported* curve. |
| Cub mortality drop | `BEAR_DANGER_CHILD_MULTIPLIER` | Sweep this and report effect on $r$. |

Most of these are **parameter schedules**, not new architecture.

## 4. Proposed thesis structure

1. **Phenomenon chapter** — documented Transylvanian bear increase, with
   ICAS/INCDS census numbers and the Ministry's carrying-capacity
   estimate as the *target curve* and *target equilibrium*.
2. **Model chapter** — the existing ABM, recast as a model of a
   Carpathian forest patch (≈ 100×100 grid representing a few hundred
   km², roughly one game-management unit / "fond cinegetic").
3. **Calibration chapter** — tune the model to reproduce the **pre-2007**
   quasi-equilibrium (≈ 6,000 bears nationally, slow growth). Baseline /
   null: with hunting on, no feeding stations, current land use, the
   model should be approximately stable.
4. **Counterfactual experiments** — the core contribution. For each
   hypothesis run:
   - Baseline (all drivers off) → expected stable.
   - Single driver on (e.g. hunting ban only) → measure $r$.
   - All drivers on → measure $r$ and compare to observed ≈ +5–7 %/yr.
   - Drivers in combination (factorial design or Sobol indices) → which
     combinations best reproduce the curve, and which single driver
     explains the most variance.
5. **Sensitivity / robustness** — replicate runs with different seeds,
   parameter perturbations, alternative grid sizes; show the qualitative
   result is stable.
6. **Policy chapter** — use the calibrated model to test interventions:
   reinstating quotas of N bears/yr, removing feeding stations, garbage
   management at village edges. Report predicted equilibrium under each.
7. **Multithreaded / distributed engine chapter** — the technical
   contribution that *enables* the hundreds of replicate scenarios
   needed for chapters 4–6 in reasonable time. Reframes the concurrency
   work as a means to an ecological end, not an end in itself.

## 5. Minimal model additions required

Ranked by effort vs payoff for the case study:

1. **Time-varying parameters** — a `Schedule` reading
   `(year, parameter, value)` from CSV. Small class; unlocks all
   longitudinal scenarios.
2. **Seasons + hibernation** — touches `decideNextAction` and satiety
   decay only. Major realism gain and directly relevant to the climate
   hypothesis.
3. **Feeding stations** as a cell type — add `FEEDING_STATION` to
   [BearCellType](../../SingleAgentDesignProposal/Bears/BearEnvironment/BearCellType.java)
   and tune food/danger.
4. **Hunter agent / quota removal** — random or targeted removal of N
   adults per year, optionally biased by sex/age as Romanian quotas were.
5. **Litter size 2–3** (already in the todo list) — without this the
   model cannot reach realistic $r_{max}$.
6. **Reporting layer** — per-year CSV with population, deaths-by-cause,
   mean age, density. Required for comparison against ICAS curves.

## 6. Why this framing strengthens the thesis

- Provides a **falsifiable target** (the published population curve)
  instead of "looks ecologically plausible".
- Justifies every modelling choice by reference to a real driver — what
  the coordinator is asking for.
- Makes the **multithreaded / distributed work load-bearing**: hundreds
  of factorial × replicate × sweep simulations are needed, which only a
  fast parallel engine can deliver. Much stronger motivation than
  "more bears go faster".
- Opens a clean **policy-relevance** angle for the conclusion.

## 7. Data and literature sources

- **ICAS / INCDS Brașov 2023** national bear genetic census (the
  ≈ 13,000 figure).
- **Romanian Ministry of Environment** bear management plan
  ("Planul național de acțiune pentru conservarea populației de urs
  brun") — source for the ≈ 4,000 optimal-population figure.
- **Pop, Mihai I.** et al. (2018, 2023) — Romanian brown bear density
  and human–bear conflict; peer-reviewed, ideal for calibration.
- **Popescu, Viorel D.** et al. — quantitative work on Carpathian
  carnivore populations.
- **LIFE EuroLargeCarnivores / LIFE FOR BEAR** project reports.
- **Chapron, G.** et al. (2014) *Science* — large-carnivore recovery in
  Europe; continental context.
- Brown-bear demography sources already cited in
  [Bear simulation parameter tuning.md](Bear%20simulation%20parameter%20tuning.md)
  (Swenson, Schwartz, Sæther) for adult/cub mortality and inter-birth
  interval.

## 8. Suggested implementation order

1. Time-varying parameter `Schedule` + CSV loader.
2. `FEEDING_STATION` cell type and map generator support.
3. Seasonal cycle + hibernation in `BearAgent`.
4. Hunter / quota-removal mechanism.
5. Litter size distribution (2–3 cubs).
6. Per-year CSV reporter and a small Python notebook for plots.
7. Calibration runs to reproduce pre-2007 equilibrium.
8. Factorial counterfactual experiments (steps 4 of the thesis
   structure).
9. Sensitivity analysis (Sobol / OAT) over the schedule parameters.
10. Policy intervention runs.

Steps 1–3 unlock essentially all of the case-study scenarios with the
smallest code change and should be done first.
