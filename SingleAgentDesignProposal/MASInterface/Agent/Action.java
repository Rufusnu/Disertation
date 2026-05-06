package MASInterface.Agent;

import MASInterface.Environment.State;

/** An abstract class for actions in an agent environment. Each type of
    Action should be a separate subclass.  */
public abstract class Action {

  /**
      Contribute this action's intended effects for the current discrete time
      step. Step-based simulations should let each agent plan against the same
      state snapshot, then resolve all accumulated effects together.
   */
  public void contributeToStep(Agent a, State s, StepActionContext context) throws Exception {
    throw new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support discrete-step planning.");
  }

  public abstract String toString();
}
