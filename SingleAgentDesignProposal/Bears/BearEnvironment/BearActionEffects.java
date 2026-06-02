package Bears.BearEnvironment;

import MASInterface.Agent.StepActionContext;
import MASInterface.Environment.Coords;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects all action intents for a single simulation tick. */
public class BearActionEffects implements StepActionContext {
    private final Map<Integer, Coords> moveIntents = new LinkedHashMap<Integer, Coords>();
    private final Set<Integer> birthIntents = new LinkedHashSet<>();
    private final Set<Coords> eatIntents = new LinkedHashSet<>();
    private final Map<Integer, DeathCause> dieIntents = new LinkedHashMap<Integer, DeathCause>();
    private int performedActions = 0;
    private int conflictEvents = 0;

    public void requestMove(int agentId, Coords target) {
        moveIntents.put(agentId, target);
    }
    public void requestEatIntents(Coords coords) {
        eatIntents.add(coords);
    }
    public void requestBirth(int agentId) {
        birthIntents.add(agentId);
    }
    public void requestDie(int agentId, DeathCause cause) {
        dieIntents.put(agentId, cause);
    }

    public void recordAction() {
        performedActions++;
    }

    /** Counts one human-bear conflict event (a bear occupying a VILLAGE/ROAD cell this tick). */
    public void recordConflictEvent() {
        conflictEvents++;
    }

    public Map<Coords, List<Integer>> moveClaimsByTarget() {
        Map<Coords, List<Integer>> claimantsByTarget = new LinkedHashMap<Coords, List<Integer>>();
        for (Map.Entry<Integer, Coords> moveIntent : moveIntents.entrySet()) {
            claimantsByTarget.computeIfAbsent(moveIntent.getValue(), ignored -> new ArrayList<Integer>()).add(moveIntent.getKey());
        }
        return claimantsByTarget;
    }

    public Set<Coords> eatIntents() {
        return eatIntents;
    }
    public Set<Integer> birthIntents() {
        return birthIntents;
    }
    public Map<Integer, DeathCause> dieIntents() {
        return dieIntents;
    }

    public int performedActions() {
        return performedActions;
    }

    public int conflictEvents() {
        return conflictEvents;
    }
}