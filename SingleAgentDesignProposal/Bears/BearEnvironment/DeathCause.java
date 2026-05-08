package Bears.BearEnvironment;

public enum DeathCause {
    OLD_AGE,
    STARVATION,
    DANGER;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}