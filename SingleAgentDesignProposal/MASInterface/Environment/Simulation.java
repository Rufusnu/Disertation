package MASInterface.Environment;

/**
 * The top-level class for an agent simulation. This can be used for either
 * single or multi-agent simulations.
 */
public abstract class Simulation {
	protected Environment environment;

	/**
	 * Constructs a new simulation. Initializes the agent(or agents vector) and
	 * the environment.
	 */
	public Simulation(Environment environment) {
		this.environment = environment;
	}

	/**
	 * Runs the simulation starting from a given state. This consists of a
	 * sense-act loop for the/(each) agent. An alternative approach would be to
	 * allow the agent to decide when it will sense and act.
	 */
	public abstract void start(State initState);
}
