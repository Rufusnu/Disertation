package Bears.BearAgent.Actions;

import Bears.BearEnvironment.BearActionEffects;
import Bears.BearEnvironment.BearState;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.State;

/**
 * A vacuum cleaning world action that causes the agent to suck up dirt from it
 * current location.
 */
public class GiveBirth extends Action {

	public GiveBirth() {

	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) {
        BearState bearState = BearState.requireBearState(state, "GiveBirth.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;
        
        effects.requestBirth(agent.getId());
        effects.recordAction();
    }

	public String toString() {
		return " (GIVEBIRTH)";
	}
}
