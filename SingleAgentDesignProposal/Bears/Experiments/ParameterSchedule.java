package Bears.Experiments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A time-varying schedule of parameter changes, applied to the static
 * {@link MASInterface.Settings} during a simulation run.
 *
 * <p>Each entry says: when the simulation year reaches {@code year}, set the
 * Settings field {@code parameter} to {@code value} (parsed according to the
 * field's declared type). This is the minimum infrastructure required to test
 * non-stationary scenarios such as "danger drops after 2000" or "food
 * resources grow after a policy change".</p>
 *
 * <h3>Supported formats</h3>
 *
 * <p>CSV (recommended): one entry per line, header optional.</p>
 * <pre>
 *   year,parameter,value
 *   # comments and blank lines are ignored
 *   0,BEAR_DANGER_DEATH_RATE_PER_TICK,0.000020
 *   25,BEAR_DANGER_DEATH_RATE_PER_TICK,0.000010
 *   30,FOOD_GROWN_PER_TICK,0.0065
 * </pre>
 *
 * <p>JSON: a flat array of objects with the same three fields. Only this
 * minimal shape is supported (no nested objects, no comments).</p>
 * <pre>
 *   [
 *     {"year": 0,  "parameter": "BEAR_DANGER_DEATH_RATE_PER_TICK", "value": "0.000020"},
 *     {"year": 25, "parameter": "BEAR_DANGER_DEATH_RATE_PER_TICK", "value": "0.000010"}
 *   ]
 * </pre>
 *
 * <p>The {@code year} column is interpreted in <em>simulation years</em> (the
 * same time axis used by the reference data CSV), i.e. {@code tick *
 * Settings.ONE_TICK_IN_YEARS}, starting from 0 at simulation start.</p>
 */
public final class ParameterSchedule {

    public static final class Entry {
        public final double simulationYear;
        public final String parameter;
        public final String value;

        public Entry(double simulationYear, String parameter, String value) {
            this.simulationYear = simulationYear;
            this.parameter = parameter;
            this.value = value;
        }

        @Override
        public String toString() {
            return "@y=" + simulationYear + " " + parameter + "=" + value;
        }
    }

    private final List<Entry> entries; // sorted ascending by simulationYear
    private final String sourceDescription;

    public ParameterSchedule(List<Entry> entries, String sourceDescription) {
        List<Entry> copy = new ArrayList<>(entries);
        copy.sort(Comparator.comparingDouble(e -> e.simulationYear));
        this.entries = Collections.unmodifiableList(copy);
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
    }

    public List<Entry> entries() { return entries; }
    public boolean isEmpty() { return entries.isEmpty(); }
    public String sourceDescription() { return sourceDescription; }

    public static ParameterSchedule empty() {
        return new ParameterSchedule(Collections.emptyList(), "");
    }

    /** Loads from CSV (.csv) or minimal JSON (.json), dispatched by file extension. */
    public static ParameterSchedule load(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".json")) {
            return loadJson(path);
        }
        return loadCsv(path);
    }

    public static ParameterSchedule loadCsv(Path path) throws IOException {
        List<Entry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        boolean headerCheckDone = false;
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerCheckDone) {
                headerCheckDone = true;
                if (line.toLowerCase().startsWith("year")) {
                    continue; // header row
                }
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 3) {
                throw new IOException("Schedule CSV " + path + " line " + lineNo
                        + ": expected 'year,parameter,value', got: " + raw);
            }
            try {
                double year = Double.parseDouble(parts[0].trim());
                String param = parts[1].trim();
                String value = parts[2].trim();
                if (param.isEmpty()) {
                    throw new IOException("Schedule CSV " + path + " line " + lineNo
                            + ": empty parameter name.");
                }
                entries.add(new Entry(year, param, value));
            } catch (NumberFormatException e) {
                throw new IOException("Schedule CSV " + path + " line " + lineNo
                        + ": invalid year: " + parts[0], e);
            }
        }
        return new ParameterSchedule(entries, path.toString());
    }

    /**
     * Minimal JSON reader: expects a top-level array of flat objects with
     * keys "year", "parameter", "value". Strings may be quoted; numbers may
     * be unquoted. No nested objects, no escaped quotes, no comments. This
     * is intentionally tiny - drop in a real JSON library if more is needed.
     */
    public static ParameterSchedule loadJson(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return new ParameterSchedule(Collections.emptyList(), path.toString());
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IOException("Schedule JSON " + path + ": expected a top-level array.");
        }
        String body = text.substring(start + 1, end).trim();
        List<Entry> entries = new ArrayList<>();
        if (body.isEmpty()) {
            return new ParameterSchedule(entries, path.toString());
        }
        // Split on object boundaries. Assumes no nested objects.
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && (body.charAt(i) == ',' || Character.isWhitespace(body.charAt(i)))) i++;
            if (i >= body.length()) break;
            if (body.charAt(i) != '{') {
                throw new IOException("Schedule JSON " + path + ": expected '{' at offset " + i);
            }
            int objEnd = body.indexOf('}', i);
            if (objEnd < 0) {
                throw new IOException("Schedule JSON " + path + ": unterminated object.");
            }
            String obj = body.substring(i + 1, objEnd).trim();
            i = objEnd + 1;

            Double year = null;
            String param = null;
            String value = null;
            // Split key:value pairs by comma at top-level (no nesting expected).
            for (String pair : obj.split(",")) {
                String p = pair.trim();
                if (p.isEmpty()) continue;
                int colon = p.indexOf(':');
                if (colon < 0) {
                    throw new IOException("Schedule JSON " + path + ": missing ':' in '" + p + "'.");
                }
                String k = stripQuotes(p.substring(0, colon).trim());
                String v = stripQuotes(p.substring(colon + 1).trim());
                switch (k) {
                    case "year":
                        try { year = Double.parseDouble(v); }
                        catch (NumberFormatException e) {
                            throw new IOException("Schedule JSON " + path + ": invalid year '" + v + "'.", e);
                        }
                        break;
                    case "parameter": param = v; break;
                    case "value":     value = v; break;
                    default: /* ignore unknown keys */ break;
                }
            }
            if (year == null || param == null || value == null) {
                throw new IOException("Schedule JSON " + path
                        + ": each entry needs 'year', 'parameter', 'value'.");
            }
            entries.add(new Entry(year, param, value));
        }
        return new ParameterSchedule(entries, path.toString());
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
