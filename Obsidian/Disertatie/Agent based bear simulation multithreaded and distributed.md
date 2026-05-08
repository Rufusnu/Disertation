Todos:

- [x] Folosit proiect existent de la MAS
- [x] schimbat numele la tot in Bear + a little bit of folder file restructure pentru separation of concerns and single responsibility
- [ ] Create Bear Cell
	- [x] Adaugat CellTypes (FOREST FIELD VILLAGE ROAD MOUNTAIN NONE)
	- [x] Oare ce informatii ar mai fi necesare in Cell?
		- [x] food amount
			- [x] poate de facut sa scada cand un urs mananca de acolo si sa creasca over time (intr-un mod eficient)
				- [ ] todo restoreRandomFood() de mutat intr-un loc mai bun
		- [x] threat amount
			- [x] threat should also probabilistically kill bears
- [ ] Change Bear behaviour from vacuum to bear
	- [x] look into neighbouring cells and decide (maybe also on a bit of a weighted probability) the next action
	- [ ] reproducing
		- [ ] male needed
		- [ ] issue: female can reproduce repeatedly (cooldown only)
		- [ ] longer birth interval
		- [ ] gestation/nursing constraint
		- [ ] juvenile penalties
		- [ ] density/carrying-capacity effects
		- [x] cooldown
		- [ ] time until birth
		- [x] birth only of satiety high enough
	- [x] eating
	- [x] death
		- [x] die of starvation
		- [x] die of danger
	- [ ] very hungry (influenced behaviour to enter cities for food)
	- [x] hunger/satiety
		- [x] die of starvation
- [ ] Simulation performance and functioning
	- [ ] Verificari sa fie simulare cu discrete timing
	- [ ] check if proper multi threading
	- [ ] make distributed

1 bear -> 750.000 actions in 10 sec-> 75.000h / sec -> 3.125h / sec -> 8.5 yrs / sec
1000 bears -> 30926000 actions in 10 sec -> 3092600 actions / s -> 3092h / sec

Done. Bears now die probabilistically from danger on their current tile each tick.

The death probability per tick is: P(death)=danger×BEAR_DANGER_DEATH_RATE_PER_TICKP(death)=danger×BEAR_DANGER_DEATH_RATE_PER_TICK

With [BEAR_DANGER_DEATH_RATE_PER_TICK = 0.002](vscode-file://vscode-app/c:/Program%20Files/Microsoft%20VS%20Code/resources/app/out/vs/code/electron-browser/workbench/workbench.html):

- Village/Road (danger ~0.9): ~0.18% death chance per tick → ~1.5% per day
- Field (danger ~0.4): ~0.08% per tick → ~0.67% per day
- Forest (danger ~0.1): ~0.02% per tick → ~0.17% per day

This gives bears a strong incentive to avoid villages and roads, which is the desired behavior. [DANGER](vscode-file://vscode-app/c:/Program%20Files/Microsoft%20VS%20Code/resources/app/out/vs/code/electron-browser/workbench/workbench.html) is also added to the [DeathCause](vscode-file://vscode-app/c:/Program%20Files/Microsoft%20VS%20Code/resources/app/out/vs/code/electron-browser/workbench/workbench.html) enum so it shows up in the end-of-run summary



is it reasonable that the population growth was x6.7 in 13 years?

Short answer: **yes in your current model, not very realistic biologically**.

(6.7\times) in 13 years implies about:

[  
r \approx \frac{\ln(6.7)}{13} \approx 0.146  
]

So roughly **14–15% net annual growth**.

That is plausible for your rules because you currently have:

- no male needed,
- female can reproduce repeatedly (cooldown only),
- no gestation/nursing constraint,
- limited juvenile penalties.

For real bear populations, that growth is usually too high over long periods.  
If you want realism, reduce growth by adding one or more of: mate requirement, juvenile mortality, longer interbirth interval, reproductive age window, and density/carrying-capacity effects.