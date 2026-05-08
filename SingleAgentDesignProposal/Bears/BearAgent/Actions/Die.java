package Bears.BearAgent.Actions;

import Bears.BearEnvironment.BearActionEffects;
import Bears.BearEnvironment.BearState;
import Bears.BearEnvironment.DeathCause;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.State;

/**
 * A vacuum cleaning world action that causes the agent to suck up dirt from it
 * current location.
 */
public class Die extends Action {
    private final DeathCause cause;

    public Die(DeathCause cause) {
		this.cause = cause;
	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) {
        BearState.requireBearState(state, "Die.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;

        effects.requestDie(agent.getId(), cause);
        effects.recordAction();
    }

	public String toString() {
		return " (DIE: " + cause + ")";
	}
}
