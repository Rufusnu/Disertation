package Bears.BearAgent.Actions;

import Bears.BearEnvironment.BearActionEffects;
import Bears.BearEnvironment.BearState;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.State;

/** An action that represents the agent doing nothing this tick. */
public class Nothing extends Action {

	public Nothing() {
	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) {
        BearState.requireBearState(state, "Nothing.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;
        effects.recordAction();
    }

	public String toString() {
		return "(NOTHING)";
	}
}
