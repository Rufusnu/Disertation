# Bear simulation parameter tuning — ecological realism pass

This note records a tuning round applied to `MASInterface.Settings` in the
single-agent bear simulation, the reasoning behind each change, and the
biological sources used to justify the new values.

## Context

The simulation tracks individual brown-bear-like agents on a 100×100 grid.
Time is measured in ticks of `ONE_TICK_IN_YEARS = 0.0001140771`
(≈ 1 hour). A typical benchmark run covers ~150 000 ticks ≈ 17 simulated
years.

Before tuning, a representative run produced:

- Initial bears: 1 000 → End bears: 3 129 (≈ 6.8 %/yr growth)
- Deaths over ~17 yr: 227 *old age* + 748 *danger* + **0 starvation**
- Implied annual mortality on a mean population of ~2 000: ≈ 1.7 %/yr

This is biologically off in three ways:

1. No starvation deaths at all — food was effectively free.
2. Total mortality (~1.7 %/yr) is well below real adult bear mortality.
3. Population grows at near the theoretical maximum every year, with no
   sign of being food/habitat limited (Malthusian rather than logistic).

Real wild brown-bear populations sit close to demographic equilibrium:
births and deaths roughly balance, density is regulated by habitat
quality and human-caused mortality, and cub mortality is several times
adult mortality.

## Changes applied

All edits are in
[SingleAgentDesignProposal/MASInterface/Settings.java](../../SingleAgentDesignProposal/MASInterface/Settings.java).

| Setting | Old value | New value | Effect |
|---|---|---|---|
| `BEAR_SATIETY_GAIN_PER_EAT` | `0.35` | `0.12` | One feeding action no longer tops a bear up for thousands of ticks; bears must forage repeatedly and starvation becomes a real risk in food-poor regions. |
| `BEAR_DANGER_DEATH_RATE_PER_TICK` | `0.0000085` | `0.00003` | Lifts background adult mortality to roughly 5–10 %/yr depending on tile type, instead of ≈ 1–2 %/yr. |
| `BEAR_DANGER_CHILD_MULTIPLIER` | `1.3` | `5.0` | Cubs die at ~5× the adult rate, matching observed cub mortality in wild brown bears. |
| `BEAR_REPRODUCTION_COOLDOWN_YEARS` | `4` | `3` | Brings the inter-birth interval to ~3–4 yr (gestation 1 yr + 3 yr cooldown ≈ 3 yr cycle when conditions allow), which matches the lower end of the literature. |

### Why these specific numbers

#### `BEAR_SATIETY_GAIN_PER_EAT`: 0.35 → 0.12

In `BearAgent.applySatietyFromEating`, satiety gain per `Eat` action is
`cell.food() * BEAR_SATIETY_GAIN_PER_EAT`. With satiety decay of
`0.0003` per tick (≈ 0.0003/h), even a half-stocked tile (`food = 0.5`)
returned `0.5 × 0.35 = 0.175` per eat — about 580 ticks (~24 days) of
metabolism for one bite. That made starvation impossible.

A value of `0.12` makes one eat on the same tile worth ~200 ticks
(~8 days), so a bear typically needs to eat several times per simulated
month and is genuinely vulnerable when food density drops or competition
on a tile is high.

#### `BEAR_DANGER_DEATH_RATE_PER_TICK`: 8.5e-6 → 3.0e-5

There are ≈ 8 766 ticks in a simulated year. The per-year death
probability on a tile of danger `d` is approximately
$1 - (1 - d \cdot r)^{8766}$ where $r$ is `BEAR_DANGER_DEATH_RATE_PER_TICK`.

| Tile (avg danger) | Old (r=8.5e-6) yearly death prob | New (r=3.0e-5) yearly death prob |
|---|---|---|
| Forest (0.10) | ≈ 0.74 % | ≈ 2.6 % |
| Mountain (0.30) | ≈ 2.2 % | ≈ 7.7 % |
| Field (0.40) | ≈ 2.9 % | ≈ 10.2 % |
| Village (0.90) | ≈ 6.5 % | ≈ 21.4 % |

Bears in this simulation actively avoid danger via `scoreNeighbor`, so
the realised average mortality sits well below the per-tile value. The
new rates put realised adult mortality in the 5–10 %/yr band, which is
where most field studies land for brown bears (see sources below).

#### `BEAR_DANGER_CHILD_MULTIPLIER`: 1.3 → 5.0

Field studies consistently report cub-of-the-year mortality of
**25–45 %** in the first year, against adult female mortality of
**2–10 %** — a 4–10× difference. `1.3×` essentially gave cubs an adult
risk profile, which is one of the main reasons the population grew
unchecked. A `5.0×` multiplier reproduces the cub-mortality bottleneck
that keeps real populations near equilibrium.

#### `BEAR_REPRODUCTION_COOLDOWN_YEARS`: 4 → 3

Combined with `BEAR_GESTATION_PERIOD_YEARS = 1`, this gives an
inter-birth interval near 3 yr in good conditions, stretching to 4+ yr
when satiety stays low (the satiety gate in `canReproduceNow`). That
matches the typical 2.5–4 yr inter-birth interval observed in wild
brown bears.

### Things intentionally left unchanged

- **Litter size = 1.** Real brown-bear litters are usually 2–3. This is
  a known simplification in the model. If you want to restore it, the
  cleanest place is `Bears.BearEnvironment.BearActionEffects` (births
  application) and the `GiveBirth` action — produce N cubs instead of 1
  with N drawn from a small distribution.
- **`BEAR_HOME_RANGE_RADIUS = 8` cells.** Real home ranges (50–1 000 km²)
  imply a much larger spatial scale than the current 100×100 grid. This
  is a model-scale choice, not an ecology bug.
- **Food regeneration constants.** They were left alone in this pass;
  with reduced `BEAR_SATIETY_GAIN_PER_EAT` the food economy is already
  noticeably tighter. They are the next knob to revisit if the
  population now collapses instead of stabilising.

## Expected effect on a re-run

After these changes you should see:

- A non-zero **starvation** entry under death causes (previously absent).
- Total annual mortality climbing into the **5–10 %** range.
- Cub-stage deaths becoming visible (most `danger` deaths happening to
  young agents).
- Population growth slowing from ~6.8 %/yr toward **0–2 %/yr**, ideally
  with the curve bending into a logistic shape rather than continuing
  to compound. Over long runs the population should fluctuate around a
  carrying capacity instead of growing monotonically.

If the population now *crashes*, the next levers are
`BEAR_SATIETY_GAIN_PER_EAT` (raise slightly), `FOOD_GROWN_PER_TICK`
(raise), or `BEAR_DANGER_DEATH_RATE_PER_TICK` (lower).

## Round 2 — correcting the over-correction

The first pass over-shot in the opposite direction. A 34-year run gave:

- 1 000 → 255 bears (≈ −4 %/yr decline)
- Deaths: 3 504 *danger* + 331 *old age* + **still 0 starvation**
- Realised mortality ≈ 22 %/yr (target was 5–10 %)
- Births ≈ 91/yr — reproduction rate is fine

Two issues:

1. Total danger mortality was ~3× too high; the population could not
   sustain replacement.
2. With cub mortality at 5× the (already too high) adult rate, almost
   no cohort reached reproductive age (`BEAR_MIN_REPRODUCTION_AGE = 4`).
3. Starvation still never triggered — bears die to danger before they
   get hungry — so the satiety knob can be relaxed a bit without
   undoing the "food matters" property we wanted.

Adjusted values:

| Setting | Round 1 | Round 2 | Rationale |
|---|---|---|---|
| `BEAR_DANGER_DEATH_RATE_PER_TICK` | `0.00003` | `0.000015` | Halve it; lands realised adult mortality near 4–7 %/yr after danger-avoidance behaviour. |
| `BEAR_DANGER_CHILD_MULTIPLIER` | `5.0` | `3.5` | Still a clear cub bottleneck (~3–4× adult risk), but enough cubs survive to age 4 to keep the population stable. |
| `BEAR_SATIETY_GAIN_PER_EAT` | `0.12` | `0.18` | Slight relaxation; still much lower than the original `0.35`. Lets recovered bears actually replenish after a feeding without re-introducing the "free food" pathology. |

The aim of round 2 is a roughly **stable population** (≈ 0 ± 1 %/yr
over a few decades) with a **non-trivial mix of death causes**: danger
dominant, a visible cub bottleneck, and ideally at least a few
starvation events when bears get pushed out of good foraging tiles.

## Sources

The numeric ranges above are drawn from standard references on brown
bear (*Ursus arctos*) population ecology. They are reported as
ranges because real values vary strongly with population, study area
and human-caused mortality regime.

- Schwartz, C. C., Miller, S. D., & Haroldson, M. A. (2003).
  *Grizzly bear*, in *Wild Mammals of North America: Biology,
  Management, and Conservation* (2nd ed.), eds. Feldhamer, Thompson &
  Chapman. Johns Hopkins University Press. — Reference chapter for
  brown/grizzly demography: adult mortality, cub mortality, inter-birth
  interval, age at first reproduction.
- Sæther, B.-E., Engen, S., Swenson, J. E., Bakke, Ø., & Sandegren, F.
  (1998). *Assessing the viability of Scandinavian brown bear*,
  Ursus arctos, *populations: the effects of uncertain parameter
  estimates*. Oikos 83: 403–416. — Source for adult mortality
  (≈ 5–10 %/yr) and population growth ceiling (~5–8 %/yr).
- Swenson, J. E., Sandegren, F., Söderberg, A., Bjärvall, A., Franzén, R.,
  & Wabakken, P. (1997). *Infanticide caused by hunting of male
  bears*. Nature 386: 450–451. — Documents elevated cub mortality
  driven by infanticide and human pressure.
- McLellan, B. N. (1989). *Dynamics of a grizzly bear population during
  a period of industrial resource extraction*. Canadian Journal of
  Zoology 67: 1856–1873. — Field estimates of cub mortality (~30–40 %)
  and adult survivorship.
- Bunnell, F. L., & Tait, D. E. N. (1981). *Population dynamics of bears
  — implications*, in *Dynamics of Large Mammal Populations*, eds.
  Fowler & Smith. Wiley. — Classic synthesis of bear life-history
  parameters used to bound $r_{max}$ at ~6 %/yr.
- IUCN SSC Bear Specialist Group, *Ursus arctos* species account
  (https://www.iucn-bsg.org/ursus-arctos.html) — Densities (typically
  0.01–0.05 bears/km² in most populations, up to ~0.3/km² in a few very
  productive coastal areas), reproduction and mortality summary.

These sources back the qualitative claims (cub mortality much higher
than adult, growth ceiling around 5–8 %/yr, inter-birth interval of
~3 yr, density rarely exceeding 0.05/km²) used to set the new values.
The exact numbers in [Settings.java](../../SingleAgentDesignProposal/MASInterface/Settings.java)
are picked to land *inside* those ranges given how the agent rules
in [BearAgent.java](../../SingleAgentDesignProposal/Bears/BearAgent/BearAgent.java)
already moderate exposure (danger avoidance, hunger-weighted movement).
