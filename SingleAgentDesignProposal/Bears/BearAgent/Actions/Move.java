package Bears.BearAgent.Actions;

import Bears.BearEnvironment.BearActionEffects;
import Bears.BearEnvironment.BearState;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.Coords;
import MASInterface.Environment.State;

/**
 * A vacuum cleaning agent action that causes the agent to advance one step.
 */
public class Move extends Action {
    private final Coords target;

    public Move(Coords target) {
        this.target = target;
	}

    @Override
    public void contributeToStep(Agent agent, State state, StepActionContext context) throws Exception {
        BearState bearState = BearState.requireBearState(state, "Move.contributeToStep()");
        BearActionEffects effects = (BearActionEffects) context;

        if (target != null && !bearState.isWall(target.x, target.y)) {
            effects.requestMove(agent.getId(), target);
        }

        effects.recordAction();
    }

	public String toString() {
		return "(MOVE)";
	}
}
