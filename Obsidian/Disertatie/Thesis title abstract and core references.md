# Thesis front matter — title, abstract, core references

Working draft of the cover-page material for the dissertation. Keep in sync with [Transylvania bear population case study plan.md](Transylvania%20bear%20population%20case%20study%20plan.md) and [Bear simulation parameter tuning.md](Bear%20simulation%20parameter%20tuning.md).

## Title

**An Agent-Based Simulation of Brown Bear Population Dynamics in the Romanian Carpathians: Disentangling the Drivers of the Post-2000 Increase**

## Abstract

Romania has the largest brown bear population in the European Union. Official counts have grown from about 6,000 bears in the early 2000s to around 13,000 in the 2023 national genetic census — more than three times the 4,000 bears the Romanian Ministry of Environment considers optimal. Many reasons have been proposed for this rise: the 2016 ban on trophy hunting, the spread of supplemental feeding stations, the abandonment of farmland in Transylvania, easy food at the edges of villages, milder winters that shorten hibernation, and better counting methods. These explanations are usually discussed one at a time, and no one has yet tested them together in a single model.

This thesis builds an agent-based simulation of a Carpathian bear population. Every bear in the simulation is an individual agent with its own hunger, age, reproduction, and movement, following rules tuned to match what is known about real brown bears. The simulator is written in Java and runs in a multithreaded and distributed way, so it can handle tens of thousands of bears over several decades of simulated time. This speed is what makes it possible to run the many experiments the case study needs.

The model is first calibrated so that, with the conditions of the early 2000s, the simulated population stays roughly stable, matching the real situation at that time. Each proposed cause of the rise is then turned on and off in the simulation — alone and in combination — and the resulting population curves are compared with the official Romanian counts. A sensitivity analysis shows how much each cause contributes to the growth. Finally, the calibrated model is used to test possible management actions, such as bringing back hunting quotas, removing feeding stations, or limiting access to garbage near villages. The aim is to give a clear, reproducible, numerical basis for the ongoing public debate about how Romania should manage its brown bear population.

**Keywords:** agent-based model; brown bear; *Ursus arctos*; Romanian Carpathians; population dynamics; multithreaded simulation; distributed simulation; counterfactual analysis; wildlife management.

## Core references

A minimum core of fifteen references covering each chapter of the planned thesis: phenomenon, European context, demography, drivers, ABM methodology, calibration / sensitivity analysis, parallel ABM, and platform comparison.

### Romanian brown bear — case study

1. Pop, I. M., Bereczky, L., Chiriac, S., Iosif, R., Niță, A., Popescu, V. D., & Rozylowicz, L. (2018). *Movement ecology of brown bears (Ursus arctos) in the Romanian Eastern Carpathians*. **Nature Conservation**, 26, 15–31.
2. Institutul Național de Cercetare-Dezvoltare în Silvicultură "Marin Drăcea" (INCDS Brașov) (2023). *National brown bear genetic census report*. Romania.
3. Ministerul Mediului, Apelor și Pădurilor (Romania). *Planul Național de Acțiune pentru Conservarea Populației de Urs Brun (Ursus arctos) din România* (most recent version).

### European large-carnivore context

4. Chapron, G., Kaczensky, P., Linnell, J. D. C., et al. (2014). *Recovery of large carnivores in Europe's modern human-dominated landscapes*. **Science**, 346(6216), 1517–1519.

### Brown bear demography (parameter calibration)

5. Schwartz, C. C., Miller, S. D., & Haroldson, M. A. (2003). *Grizzly bear*. In **Wild Mammals of North America: Biology, Management, and Conservation** (2nd ed.), eds. Feldhamer, Thompson & Chapman. Johns Hopkins University Press.
6. Sæther, B.-E., Engen, S., Swenson, J. E., Bakke, Ø., & Sandegren, F. (1998). *Assessing the viability of Scandinavian brown bear, Ursus arctos, populations: the effects of uncertain parameter estimates*. **Oikos**, 83, 403–416.
7. Swenson, J. E., Sandegren, F., Söderberg, A., Bjärvall, A., Franzén, R., & Wabakken, P. (1997). *Infanticide caused by hunting of male bears*. **Nature**, 386, 450–451.

### Drivers of the increase

8. Bischof, R., Swenson, J. E., Yoccoz, N. G., Mysterud, A., & Gimenez, O. (2009). *The magnitude and selectivity of natural and multiple anthropogenic mortality causes in hunted brown bears*. **Journal of Animal Ecology**, 78(3), 656–665.
9. Munteanu, C., Kuemmerle, T., Boltiziar, M., et al. (2014). *Forest and agricultural land change in the Carpathian region — A meta-analysis of long-term patterns and drivers of change*. **Land Use Policy**, 38, 685–697.

### Agent-based modelling — methodology

10. Grimm, V., & Railsback, S. F. (2005). **Individual-based Modeling and Ecology**. Princeton University Press.
11. Grimm, V., Railsback, S. F., Vincenot, C. E., et al. (2020). *The ODD protocol for describing agent-based and other simulation models: A second update to improve clarity, replication, and structural realism*. **Journal of Artificial Societies and Social Simulation**, 23(2), 7.

### Existing ABMs of large carnivores

12. Carter, N. H., Levin, S., Barlow, A., & Grimm, V. (2015). *Modeling tiger population and territory dynamics using an agent-based approach*. **Ecological Modelling**, 312, 347–362.

### Calibration and sensitivity analysis

13. Saltelli, A., Ratto, M., Andres, T., et al. (2008). **Global Sensitivity Analysis: The Primer**. Wiley.
14. Thiele, J. C., Kurth, W., & Grimm, V. (2014). *Facilitating parameter estimation and sensitivity analysis of agent-based models: A cookbook using NetLogo and R*. **Journal of Artificial Societies and Social Simulation**, 17(3), 11.

### Parallel and distributed agent-based simulation

15. Collier, N., & North, M. (2013). *Parallel agent-based simulation with Repast for High Performance Computing*. **Simulation**, 89(10), 1215–1235.

### ABM platforms (for framework comparison)

16. Luke, S., Cioffi-Revilla, C., Panait, L., Sullivan, K., & Balan, G. (2005). *MASON: A Multiagent Simulation Environment*. **Simulation**, 81(7), 517–527.

## Report sketch (requested structure)

### Table of content (draft)

1. Introduction
2. Background and Problem Definition
3. Related Work / State of the Art
4. Proposed Approach (Original Contribution)
5. Evaluation and Validation
6. Discussion
7. Conclusion and Future Work
8. References

### Title of research project

**An Agent-Based Simulation of Brown Bear Population Dynamics in the Romanian Carpathians: Disentangling the Drivers of the Post-2000 Increase**

---

### State of the art

In the last years, multiple studies showed that large carnivores are recovering in Europe, even in areas with strong human presence [4]. Romania is one of the most important cases, because it currently hosts the largest brown bear population in the European Union. Existing Romanian-focused studies discuss movement behaviour, habitat use and conflict with people in the Carpathian area [1], while national reports and action plans [2], [3] indicate that the estimated population increased significantly in the last two decades.

From a modelling perspective, agent-based simulations are already a known and validated direction for ecological systems with many interacting individuals. Frameworks such as MASON [16] and Repast HPC [15] show that ABM can represent heterogeneous agents in spatial environments. Also, the ODD protocol and related ABM literature [10], [11] provide a good methodological base for describing and validating this type of model. For biological calibration, brown bear demographic studies [5], [6], [7] provide realistic intervals for mortality, reproduction and growth, and land-use / anthropogenic impact papers [8], [9] suggest concrete candidate drivers that can be implemented in the simulation.

Even so, most existing work analyses these drivers separately (for example hunting pressure alone, or food availability alone), and less often as interacting causes in one unified computational experiment. Also, many studies focus more on descriptive analysis than on causal testing with counterfactual scenarios. Therefore, this project proposes an ABM that keeps ecological detail, but also evaluates computational strategies, including multithreading and distribution, in order to support a larger number of experiments.

---

### Approach

The central question of this project is the following: **what combination of factors can best explain the increase of brown bear population in Transylvania / Romanian Carpathians after 2000?** The target is not only to obtain population growth in the simulation, but to separate the effect of several overlapping mechanisms: hunting-policy changes, supplemental feeding, anthropogenic food near settlements, land-use change, and seasonal/climate effects.

To answer this question, the project uses a spatial agent-based simulation implemented in Java. Each bear is represented as an autonomous agent with its own internal state (age, sex, satiety, reproduction status and position) and with decision rules for movement, feeding, birth and death. In the current implementation, the world is represented as a grid with forest, field, mountain, village and road cells, each one having food and danger values.

The original contribution is both ecological and technical.

1. **Ecological contribution (current):** the model already includes core demographic and behavioural mechanisms (ageing, satiety, hunger-driven movement, danger-based mortality, reproduction constraints, crowding and local food dynamics).
2. **Ecological contribution (next step):** extend the model with explicit time-varying policy/habitat scenarios (for example hunting-pressure schedule, feeding-station effect, and seasonal effects).
3. **Technical contribution (current):** the simulation is multithreaded in the planning phase (parallel percept + action decision), with a synchronized tick-based commit phase.
4. **Technical contribution (next step):** evaluate distributed execution as an experimental option, with the explicit hypothesis that distributed execution inside one run may be slower for this fine-grained model due to communication and barrier overhead.

The methodological flow of the study is:

- define a baseline calibrated close to pre-2007 quasi-equilibrium,
- encode each hypothesis as a controllable simulation factor,
- run single-factor and combined counterfactual scenarios,
- repeat runs in batch mode to capture stochastic variability (fixed-seed control is a planned improvement),
- compare simulated trajectories with official Romanian estimates.

Through this workflow, the project is positioned not only as a simulation exercise, but as a practical decision-support instrument for wildlife-management discussion.

---

### Evaluation of the approach

The evaluation is done on two directions: **ecological validation** and **computational validation**. This subsection separates what is already available in the current codebase from what is planned for the final evaluation chapter.

For ecological validation, the model is calibrated using demographic ranges from the literature [5], [6], [7] and Romanian context sources [1], [2], [3]. After calibration, we compare several families of experiments:

- baseline case (without major post-2000 changes),
- single-driver cases (for example hunting reduction only),
- combined-driver cases (policy + feeding + land-use + seasonality).

The main ecological metrics are annual growth rate, age-structure behaviour, death-cause distribution, and long-term equilibrium tendency. In the current implementation, population and death-cause summaries are already produced; trajectory-level error metrics (MAE / RMSE versus yearly reference values) are planned additions for the final experiments.

For computational validation, the current implementation already measures detailed per-tick timing breakdowns (planning versus commit and sub-steps), together with runtime and throughput indicators. Next, we compare against a sequential baseline and separate two distributed modes:

1. distributed execution of one simulation run,
2. distributed execution of many independent runs.

In this thesis we explicitly expect that mode (1) may perform worse for this type of fine-grained tick model, because communication and synchronization overhead can dominate the useful work of each agent. In contrast, mode (2) is expected to be effective, because independent runs require minimal inter-node communication.

By combining ecological and computational results, we can derive both a scientific conclusion (which factors best explain the population increase) and an engineering conclusion (which execution strategy is actually efficient for this ABM). In this context, an important expected conclusion is that distributed computing is not the best option for the core tick-by-tick world update, but it is very useful for large experiment batches.
