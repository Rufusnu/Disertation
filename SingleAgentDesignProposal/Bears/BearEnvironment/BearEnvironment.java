package Bears.BearEnvironment;

import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Environment.Environment;
import MASInterface.Agent.Percept;
import MASInterface.Settings;

/** A simulator for the bear world environment. */
public class BearEnvironment extends Environment{

  public BearEnvironment() {
  }
    
  /** Creates a percept for an agent. This implements the see: S -> P
      function. */
  public Percept getPercept(Agent agent) {
    
    BearPercept bearPercept;

    if (state instanceof BearState) {
      try {
        bearPercept = new BearPercept((BearState)state, agent);
        if (Settings.VERBOSE_PERCEPT) {
            System.out.println("Percept: " + bearPercept.toString()
                    + ";  (" + agent
                    + ");  (Coords: x=" + ((BearState) state).getAgentCoords(agent.getId()).x
                    + " - y=" + ((BearState) state).getAgentCoords(agent.getId()).y
                    + ");");
        }
        return bearPercept;
      } catch (Exception e) {
        System.out.println(e);
      }
    }
    else {
      System.out.println("ERROR - state is not a BearState object.");
      return null;
    }
    return null;
  }

  public void setAgentRandomCoords(int agentId) {
    ((BearState)state).setAgentRandomCoords(agentId);
  }


    public void restoreRandomFood() {
        ((BearState)state).restoreRandomFood();
    }
}
