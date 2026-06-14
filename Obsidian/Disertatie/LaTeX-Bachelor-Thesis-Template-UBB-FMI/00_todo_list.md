# Todo plan for the dissertation

## Immediate objective
Transform the current simulation project into a complete bachelor thesis draft, grounded in actual code and experiments already present in the repository.

## Main checklist

- [x] Analyze codebase and extract thesis-relevant technical contributions.
- [x] Build a thesis table-of-contents structure in Overleaf/LaTeX style.
- [x] Start writing chapter by chapter in a narrative style close to your previous thesis.
- [ ] Expand chapter drafts with figures, tables, and final experiment values.
- [ ] Validate and finalize all bibliography entries and BibTeX keys.
- [ ] Run the complete experimental pipeline and export final CSV/plots.
- [ ] Align writing with supervisor feedback and faculty template.

## What to emphasize in the thesis

- The project is an agent-based brown bear population simulator with explicit ecological rules.
- The simulation loop is tick-based with parallel planning and serial commit.
- Multithreading is useful for one simulation run.
- Distributed computing is mostly useful for running many independent runs, not for one tick-synchronized run with small per-agent computation.
- The project includes a full experiment layer: schedules, seeds, metrics, calibration, and batch reproducibility.

## Writing workflow from now

1. Refine Chapter 1 and Chapter 2 with local context (Romania/Transylvania data and citations).
2. Add architecture figures from code modules and interaction flow.
3. Execute baseline + counterfactual experiment batches.
4. Fill Chapter 4 with real numeric tables and plots.
5. Finalize discussion, limitations, and future work.
