package Bears.BearEnvironment;

import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.Coords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects all action intents for a single simulation tick. */
public class BearActionEffects implements StepActionContext {
    private final Map<Integer, Coords> moveIntents = new HashMap<Integer, Coords>();
    private final Set<Coords> foodReductions = new HashSet<Coords>();
    private int performedActions = 0;

    public void requestMove(int agentId, Coords target) {
        moveIntents.put(agentId, target);
    }
    public void requestFoodReduction(Coords coords) {
        foodReductions.add(coords);
    }


    public void recordAction() {
        performedActions++;
    }

    public Map<Coords, List<Integer>> moveClaimsByTarget() {
        Map<Coords, List<Integer>> claimantsByTarget = new HashMap<Coords, List<Integer>>();
        for (Map.Entry<Integer, Coords> moveIntent : moveIntents.entrySet()) {
            claimantsByTarget.computeIfAbsent(moveIntent.getValue(), ignored -> new ArrayList<Integer>()).add(moveIntent.getKey());
        }
        return claimantsByTarget;
    }

    public Set<Coords> foodReductions() {
        return foodReductions;
    }

    public int performedActions() {
        return performedActions;
    }
}