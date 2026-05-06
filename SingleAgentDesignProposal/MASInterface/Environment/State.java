package MASInterface.Environment;

import MASInterface.Environment.Coords;

/** A complete representation of a situation in the agent environment.
    Since this is very domain specific, few methods are given.
    However, there should be methods for updating and retrieving various
    aspects of the state. */
public abstract class State {

  /** Displays information about the state. This may be as simple as
      text-based output, or could update a graphical display. */
  public abstract void display();
  public abstract int mapLength();
  public abstract boolean isAgentOnTile(Coords coords);
  public abstract void putAgent(int agentId, Coords coords);

}
