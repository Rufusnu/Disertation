# Reference data — Romanian brown bear (*Ursus arctos*)

This directory holds the empirical reference series the agent-based bear
simulation is validated against. Each file is loaded by
`Bears.Experiments.ReferenceData.loadAnnualPopulation(Path)` and paired with
the simulated annual end-of-year population by
`Bears.Experiments.MetricsEvaluator`.

## Files

| File | Contents | Use |
|------|----------|-----|
| `romania_brown_bear_population.csv` | Annual national population estimates, 2005–2021, derived from the Ministry of Environment / Romanian Hunters' and Anglers' Association (AGVPS) game census. | Default calibration / validation target. |
| `romania_brown_bear_population_genetic.csv` | Independent abundance estimates from non-invasive genetic capture-recapture studies. | Cross-check anchor points; not for direct annual MAE. |

## Units and conventions

- `year` is a calendar year (integer).
- `population` is the point estimate of the total number of individuals
  (all age classes) on Romanian territory. Confidence intervals are
  documented per study below but are not encoded in the CSV.
- Lines beginning with `#` and blank lines are ignored by the loader.

## Sources

1. **Romanian Ministry of Environment, Waters and Forests** – annual
   "Evaluarea efectivelor de urs brun (*Ursus arctos*)" reports submitted to
   Parliament and used to set the annual harvest / intervention quota.
   These are the values that feed the annual series in
   `romania_brown_bear_population.csv` for the period 2005–2021.
2. **Pop, I. M., Bereczky, L., Chiriac, S., Iosif, R., Nita, A., Popescu, V. D.,
   Rozylowicz, L. (2018)** – "Movement ecology of brown bears (*Ursus arctos*)
   in the Romanian Eastern Carpathians", *Nature Conservation*, 26, 15–31.
   Used to corroborate the order of magnitude of the AGVPS counts and to
   sanity-check the genetic-study anchors.
3. **Romanian National Institute for Research and Development in Forestry
   "Marin Drăcea" (INCDS)** – 2022 national genetic capture-recapture study
   ("Studiul genetic al populației de urs brun din România"), point estimate
   ≈ 8,093 individuals (95 % CI roughly 7,500 – 8,600). Source row in
   `romania_brown_bear_population_genetic.csv`.
4. **LIFE FOR BEAR (LIFE13 NAT/RO/001154)** – project deliverables and
   technical reports on brown-bear population trends in the Carpathians.
   Used as a secondary cross-check for the 2014–2019 window.

## Known limitations and caveats

- The AGVPS / Ministry annual figures are derived from track counts and
  den-emergence observations reported by hunting management units. Several
  peer-reviewed studies (e.g. Popescu et al., 2016, *Wildlife Biology*) have
  shown these counts systematically **overestimate** true abundance relative
  to genetic estimates, in part because the same individual may be reported
  by neighbouring units. The 2022 INCDS genetic study (≈ 8,093) is the most
  methodologically rigorous single-year anchor available at the time of
  writing.
- The series is national. The simulation maps a single rectangular landscape,
  so the reference should be interpreted as a target *order of magnitude and
  trend*, not a spatially explicit ground truth.
- 2020–2021 figures show an upward step partly attributable to a change in
  reporting methodology (introduction of bear-conflict counts into the
  national database) rather than purely biological growth.

## Using the data in experiments

The Java pipeline loads a reference CSV via:

```java
NavigableMap<Integer, Double> reference =
    ReferenceData.loadAnnualPopulation(Paths.get("reference-data/romania_brown_bear_population.csv"));
```

The simulation reports years starting at 0. To align simulated year 0 with a
calendar start year (e.g. 2005), the `BatchRunner` smoke-test `main` accepts
a `-Dreference.startYear=<calendarYear>` system property and shifts the
loaded series accordingly before pairing.

Example:

```
java -Dreference=reference-data/romania_brown_bear_population.csv \
     -Dreference.startYear=2005 \
     Bears.Experiments.BatchRunner experiment-output
```

Without `-Dreference.startYear`, the calendar years in the CSV are used
verbatim, which only makes sense if the simulation itself is configured to
emit calendar-year-keyed samples (it currently does not).
