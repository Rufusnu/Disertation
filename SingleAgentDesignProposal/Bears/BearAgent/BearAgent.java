package Bears.BearAgent;

import Bears.BearAgent.Actions.Die;
import Bears.BearAgent.Actions.Eat;
import Bears.BearAgent.Actions.Move;
import Bears.BearAgent.Actions.Nothing;
import Bears.BearAgent.Actions.GiveBirth;
import Bears.BearEnvironment.BearPercept;
import Bears.BearEnvironment.DeathCause;
import MASInterface.Agent.Action;
import MASInterface.Agent.Agent;
import MASInterface.Agent.Percept;
import MASInterface.Settings;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** The Vacuum Cleaning MASInterface.Agent - it is a simple reactive agent*/
public class BearAgent extends Agent {

    public enum Gender {
        FEMALE,
        MALE
    }

    /** a deliberative agent stores its internal state - it may keep a history in the environment*/
    private final int id;
    private final Gender gender;
    private BearPercept currentPercept;
    private double age;
    private double reproductionCooldownYearsRemaining;
    private double satiety;
    private double gestationPeriod;
    private boolean isPregnant;
    private int homeX;
    private int homeY;

    public BearAgent(int id) {
        this(id,
                ThreadLocalRandom.current().nextDouble(0, Settings.BEAR_MAX_GENERATE_AGE),
                randomGender());
    }

    public BearAgent (int id, int newAge) {
        this(id, (double) newAge, randomGender());
    }

    private BearAgent(int id, double initialAge, Gender gender) {
        this.id = id;
        this.age = initialAge;
        this.gender = gender;
        this.reproductionCooldownYearsRemaining = 0;
        this.satiety = ThreadLocalRandom.current().nextDouble(Settings.BEAR_INITIAL_SATIETY_MIN, Settings.BEAR_INITIAL_SATIETY_MAX);
        this.gestationPeriod = 0;
        this.isPregnant = false;
        this.homeX = -1;
        this.homeY = -1;
    }

    private static Gender randomGender() {
        return ThreadLocalRandom.current().nextBoolean() ? Gender.FEMALE : Gender.MALE;
    }

    public int getId() {
        return id;
    }

    public Gender getGender() {
        return gender;
    }

    public void see(Percept percept) {
        currentPercept = (BearPercept) percept;
    }

    public Action selectAction() {
        passTime();

        // check if death came
        if (satiety <= 0) {
            return new Die(DeathCause.STARVATION);
        }

        if (age >= Settings.BEAR_MAX_AGE && ThreadLocalRandom.current().nextDouble(0, 1) <= Settings.DEATH_RATE_AFTER_MAX_AGE)  {
            return new Die(DeathCause.OLD_AGE);
        }

        // danger on the current tile kills probabilistically
        double danger = currentPercept.currentCell() != null ? currentPercept.currentCell().danger() : 0;
        if (danger > 0 && ThreadLocalRandom.current().nextDouble() < danger * Settings.BEAR_DANGER_DEATH_RATE_PER_TICK * ageDangerMultiplier()) {
            return new Die(DeathCause.DANGER);
        }

        if (canGiveBirthNow()) {
            reproductionCooldownYearsRemaining = Settings.BEAR_REPRODUCTION_COOLDOWN_YEARS;
            isPregnant = false;
            return new GiveBirth();
        }

        if (canReproduceNow() && currentPercept.nearbyMaleBear()) {
            isPregnant = true;
            gestationPeriod = Settings.BEAR_GESTATION_PERIOD_YEARS;
        }

        BearPercept.NeighborCellInfo current = currentPercept.currentCell();
    initializeHomeRegion(current);

        // hungry bears eat immediately if there is any food on current tile
        if (satiety < Settings.BEAR_SATIETY_EAT_PREFERENCE_THRESHOLD && currentTileHasEnoughFood(current)) {
            applySatietyFromEating(current);
            return new Eat();
        }

        // well-fed bears move to the best neighbor if it scores better than staying
        BearPercept.NeighborCellInfo bestNeighbor = chooseBestNeighbor(currentPercept.neighborCells(), current);
        if (bestNeighbor != null && !bestNeighbor.coords().equals(current.coords())) {
            return new Move(bestNeighbor.coords());
        }

        // no better neighbor — eat in place if food available, otherwise wait
        if (currentTileHasEnoughFood(current)) {
            applySatietyFromEating(current);
            return new Eat();
        }
        return new Nothing(); // no food here, no better neighbor — wait
    }

    private void passTime() {
        // age 1 hour
        age += Settings.ONE_TICK_IN_YEARS;
        reproductionCooldownYearsRemaining = Math.max(0, reproductionCooldownYearsRemaining - Settings.ONE_TICK_IN_YEARS);
        gestationPeriod = Math.max(0, gestationPeriod - Settings.ONE_TICK_IN_YEARS);
        satiety = Math.max(0, satiety - Settings.BEAR_SATIETY_DECAY_PER_TICK * satietyPregnantMultiplier());
    }

    private void applySatietyFromEating(BearPercept.NeighborCellInfo cell) {
        if (cell == null) {
            return;
        }

        double availableFood = Math.max(0, cell.food());
        double gainedSatiety = availableFood * Settings.BEAR_SATIETY_GAIN_PER_EAT;
        satiety = Math.min(Settings.BEAR_SATIETY_MAX, satiety + gainedSatiety);
    }

    private boolean currentTileHasEnoughFood(BearPercept.NeighborCellInfo cell) {
        return cell != null && cell.food() >= Settings.FOOD_THRESHOLD;
    }

    private boolean canGiveBirthNow() {
        return gestationPeriod <= 0 && isPregnant;
    }

    private boolean canReproduceNow() {
        if (isPregnant) {
            return false;
        }
        if (gender != Gender.FEMALE) {
            return false;
        }
        if (age < Settings.BEAR_MIN_REPRODUCTION_AGE) {
            return false;
        }
        if (satiety < Settings.BEAR_MIN_SATIETY_TO_REPRODUCE) {
            return false;
        }
        return reproductionCooldownYearsRemaining <= 0;
    }

    private double ageDangerMultiplier() {
        return age <= Settings.BEAR_MIN_REPRODUCTION_AGE ? Settings.BEAR_DANGER_CHILD_MULTIPLIER : 1;
    }

    private double satietyPregnantMultiplier() {
        return isPregnant ? Settings.BEAR_SATIETY_DECAY_PER_TICK_PREGNANT_DEBUFF : 1;
    }

    private BearPercept.NeighborCellInfo chooseBestNeighbor(
            List<BearPercept.NeighborCellInfo> neighbors,
            BearPercept.NeighborCellInfo currentCell
    ) {
        BearPercept.NeighborCellInfo bestCell = currentCell;
        double bestScore = Double.NEGATIVE_INFINITY;

        if (currentCell != null) {
            bestScore = scoreNeighbor(currentCell);
        }

        for (BearPercept.NeighborCellInfo neighbor : neighbors) {
            if (neighbor.blocked()) {
                continue;
            }

            double score = scoreNeighbor(neighbor);
            if (score > bestScore) {
                bestScore = score;
                bestCell = neighbor;
            }
        }

        return bestCell;
    }

    private double scoreNeighbor(BearPercept.NeighborCellInfo neighbor) {
        double hunger = hungerLevel();
        double foodWeight = weightedByHunger(
                Settings.BEAR_MOVEMENT_FOOD_WEIGHT_WHEN_FULL,
                Settings.BEAR_MOVEMENT_FOOD_WEIGHT_WHEN_HUNGRY,
                hunger
        );
        double dangerWeight = weightedByHunger(
                Settings.BEAR_MOVEMENT_DANGER_WEIGHT_WHEN_FULL,
                Settings.BEAR_MOVEMENT_DANGER_WEIGHT_WHEN_HUNGRY,
                hunger
        );
        double crowdingWeight = weightedByHunger(
                Settings.BEAR_MOVEMENT_CROWDING_WEIGHT_WHEN_FULL,
                Settings.BEAR_MOVEMENT_CROWDING_WEIGHT_WHEN_HUNGRY,
                hunger
        );
        double homeRangeWeight = weightedByHunger(
                Settings.BEAR_MOVEMENT_HOME_RANGE_WEIGHT_WHEN_FULL,
                Settings.BEAR_MOVEMENT_HOME_RANGE_WEIGHT_WHEN_HUNGRY,
                hunger
        );
        double randomNoise = randomMovementNoise(hunger);
        return foodWeight * neighbor.food()
                - dangerWeight * neighbor.danger()
                - crowdingWeight * neighbor.nearbyBearCount()
                - homeRangeWeight * homeRangePenalty(neighbor)
                + randomNoise;
    }

    private void initializeHomeRegion(BearPercept.NeighborCellInfo currentCell) {
        if (homeX != -1 || currentCell == null) {
            return;
        }

        homeX = currentCell.coords().x;
        homeY = currentCell.coords().y;
    }

    private int homeRangePenalty(BearPercept.NeighborCellInfo neighbor) {
        if (homeX == -1) {
            return 0;
        }

        int distanceFromHome = Math.max(
                Math.abs(neighbor.coords().x - homeX),
                Math.abs(neighbor.coords().y - homeY)
        );
        return Math.max(0, distanceFromHome - Settings.BEAR_HOME_RANGE_RADIUS);
    }

    private double randomMovementNoise(double hunger) {
        double noiseMagnitude = weightedByHunger(
                Settings.BEAR_MOVEMENT_RANDOM_NOISE_WHEN_FULL,
                Settings.BEAR_MOVEMENT_RANDOM_NOISE_WHEN_HUNGRY,
                hunger
        );
        return ThreadLocalRandom.current().nextDouble(-noiseMagnitude, noiseMagnitude);
    }

    private double hungerLevel() {
        return 1 - Math.min(Settings.BEAR_SATIETY_MAX, satiety) / Settings.BEAR_SATIETY_MAX;
    }

    private double weightedByHunger(double fullWeight, double hungryWeight, double hunger) {
        return fullWeight + (hungryWeight - fullWeight) * hunger;
    }

    public String toString() {
        return "Robot#" + id;
    }

    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof BearAgent)) {
            return false;
        }
        BearAgent bearAgent = (BearAgent) object;

        return bearAgent.id == this.id;
    }

    public Action decideNextAction(Percept percept) {
        this.see(percept);
        return this.selectAction();
    }
}