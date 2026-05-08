package Bears.BearAgent.Actions;

import Bears.BearEnvironment.BearActionEffects;
import Bears.BearEnvironment.BearState;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.Coords;
import MASInterface.Environment.State;

/**
 * A vacuum cleaning world action that causes the agent to suck up dirt from it
 * current location.
 */
public class Reproduce extends Action {

	public Reproduce() {

	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) {
        BearState bearState = BearState.requireBearState(state, "Reproduce.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;
        
        effects.requestReproduce(agent.getId());
        effects.recordAction();
    }

	public String toString() {
		return " (REPRODUCE)";
	}
}
