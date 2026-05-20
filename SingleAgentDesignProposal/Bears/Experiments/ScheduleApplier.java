package Bears.Experiments;

import MASInterface.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a {@link ParameterSchedule} during a simulation run.
 *
 * <p>The simulation calls {@link #applyDue(double, long)} once per tick. The
 * applier mutates the static {@link Settings} fields named in the schedule
 * via reflection, snapshots their original values the first time each field
 * is touched, and records every applied event so the batch runner can write
 * a per-run audit CSV.</p>
 *
 * <p>Call {@link #restore()} at the end of the run to revert the static
 * fields to their pre-run values, regardless of how many schedule events
 * fired.</p>
 *
 * <p>Thread-safety: meant to be called from the simulation's main loop only
 * (single-threaded driver thread). The agent worker pool reads Settings but
 * never writes it.</p>
 */
public final class ScheduleApplier {

    public static final class AppliedEvent {
        public final long tick;
        public final double simulationYear;
        public final String parameter;
        public final String oldValue;
        public final String newValue;
        public AppliedEvent(long tick, double simulationYear, String parameter,
                            String oldValue, String newValue) {
            this.tick = tick;
            this.simulationYear = simulationYear;
            this.parameter = parameter;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    private final ParameterSchedule schedule;
    private int nextEntryIndex = 0;
    private final Map<String, Object> originalValues = new LinkedHashMap<>();
    private final List<AppliedEvent> appliedEvents = new ArrayList<>();
    /** True once any unknown / non-static field has been logged, to avoid log spam. */
    private final java.util.Set<String> warnedFields = new java.util.HashSet<>();

    public ScheduleApplier(ParameterSchedule schedule) {
        this.schedule = schedule != null ? schedule : ParameterSchedule.empty();
    }

    public List<AppliedEvent> appliedEvents() { return appliedEvents; }
    public boolean isEmpty() { return schedule.isEmpty(); }
    public ParameterSchedule schedule() { return schedule; }

    /**
     * Applies any schedule entries whose simulation year is &le; the given
     * current year. Entries are processed in chronological order; multiple
     * entries due on the same tick are applied in input order.
     */
    public void applyDue(double currentSimulationYear, long tick) {
        if (schedule.isEmpty()) return;
        List<ParameterSchedule.Entry> entries = schedule.entries();
        while (nextEntryIndex < entries.size()
                && entries.get(nextEntryIndex).simulationYear <= currentSimulationYear) {
            ParameterSchedule.Entry entry = entries.get(nextEntryIndex++);
            applyEntry(entry, tick, currentSimulationYear);
        }
    }

    private void applyEntry(ParameterSchedule.Entry entry, long tick, double currentSimulationYear) {
        Field field;
        try {
            field = Settings.class.getField(entry.parameter);
        } catch (NoSuchFieldException e) {
            if (warnedFields.add(entry.parameter)) {
                System.err.println("WARN: schedule references unknown Settings field: " + entry.parameter);
            }
            return;
        }
        if ((field.getModifiers() & Modifier.STATIC) == 0) {
            if (warnedFields.add(entry.parameter)) {
                System.err.println("WARN: schedule target not static: " + entry.parameter);
            }
            return;
        }
        try {
            Object oldValue = field.get(null);
            // Snapshot original on first touch only.
            originalValues.putIfAbsent(entry.parameter, oldValue);
            Object parsed = parseFieldValue(field.getType(), entry.value);
            field.set(null, parsed);
            appliedEvents.add(new AppliedEvent(
                    tick, currentSimulationYear, entry.parameter,
                    String.valueOf(oldValue), String.valueOf(parsed)));
        } catch (IllegalAccessException e) {
            System.err.println("WARN: cannot set Settings field: " + entry.parameter + " - " + e);
        } catch (NumberFormatException e) {
            System.err.println("WARN: invalid value '" + entry.value + "' for " + entry.parameter
                    + " (expected " + field.getType().getSimpleName() + ")");
        }
    }

    /** Restores every Settings field this applier ever mutated. */
    public void restore() {
        for (Map.Entry<String, Object> e : originalValues.entrySet()) {
            try {
                Field field = Settings.class.getField(e.getKey());
                field.set(null, e.getValue());
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
                // best effort
            }
        }
        originalValues.clear();
        nextEntryIndex = 0;
    }

    private static Object parseFieldValue(Class<?> type, String raw) {
        if (type == int.class || type == Integer.class) return Integer.parseInt(raw);
        if (type == long.class || type == Long.class) return Long.parseLong(raw);
        if (type == double.class || type == Double.class) return Double.parseDouble(raw);
        if (type == float.class || type == Float.class) return Float.parseFloat(raw);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw);
        return raw;
    }
}
