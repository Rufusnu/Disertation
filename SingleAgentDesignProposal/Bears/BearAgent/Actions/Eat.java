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
public class Eat extends Action {

	public Eat() {

	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) {
        BearState bearState = BearState.requireBearState(state, "Eat.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;

        Coords agentCoords = bearState.getAgentCoords(agent.getId());
        effects.requestFoodReduction(new Coords(agentCoords.x, agentCoords.y));
        effects.recordAction();
    }

	public String toString() {
		return " (EAT FOOD)";
	}
}
