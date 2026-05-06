package MASInterface.Environment;


import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.Percept;

/** This can be used for either single or multi-agent environments. */
public abstract class Environment {

  protected State state;

  /** Constructs a new environment */
  public Environment() {

  }

  /** Creates a percept for an agent. This should implement the
      see: S -> P function. */
  public abstract Percept getPercept(Agent agent);
  
  /** The current state of the environment. */
  public State currentState(){
	  return this.state;
  }

  /** Set the initial state of the environment. */
  public void setInitialState(State state){
	  this.state=state;
  }
}



