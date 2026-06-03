package Bears.Experiments.Calibration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A point in the calibration search space: an immutable mapping from
 * Settings-field name to its proposed value. Order matches
 * {@link CalibrationSpace#PARAMS}.
 */
public final class CandidatePoint {

    private final Map<String, Double> values;

    public CandidatePoint(Map<String, Double> values) {
        Map<String, Double> ordered = new LinkedHashMap<>();
        for (CalibrationParam p : CalibrationSpace.PARAMS) {
            Double v = values.get(p.settingsField());
            if (v == null) {
                throw new IllegalArgumentException("Missing value for " + p.settingsField());
            }
            ordered.put(p.settingsField(), p.clamp(v));
        }
        this.values = Collections.unmodifiableMap(ordered);
    }

    public Map<String, Double> values() { return values; }

    public double get(String field) {
        Double v = values.get(field);
        if (v == null) throw new IllegalArgumentException("Unknown field: " + field);
        return v;
    }

    /** Convert this point to a {@code BatchRunner}-style overrides map. */
    public Map<String, String> asOverrides() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            out.put(e.getKey(), Double.toString(e.getValue()));
        }
        return out;
    }

    /** Compact, deterministic string for logs and run-IDs. */
    public String shortId() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Double v : values.values()) {
            if (i++ > 0) sb.append('_');
            sb.append(String.format(java.util.Locale.ROOT, "%.4g", v));
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CandidatePoint{");
        int i = 0;
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (i++ > 0) sb.append(", ");
            sb.append(e.getKey()).append('=').append(String.format(java.util.Locale.ROOT, "%.5g", e.getValue()));
        }
        return sb.append('}').toString();
    }
}
