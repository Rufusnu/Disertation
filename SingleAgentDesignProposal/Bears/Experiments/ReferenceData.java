package Bears.Experiments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Loader for reference population data used to evaluate the simulation.
 *
 * Expected CSV format (with header row):
 *   year,population
 *   2000,4500
 *   2001,4650
 *   ...
 *
 * Lines starting with '#' and blank lines are ignored.
 */
public final class ReferenceData {

    private ReferenceData() {}

    public static NavigableMap<Integer, Double> loadAnnualPopulation(Path csvPath) throws IOException {
        TreeMap<Integer, Double> result = new TreeMap<>();
        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        boolean headerSkipped = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSkipped) {
                headerSkipped = true;
                // Skip if the first non-comment line looks like a header.
                if (line.toLowerCase().startsWith("year")) {
                    continue;
                }
            }
            String[] parts = line.split(",");
            if (parts.length < 2) {
                continue;
            }
            try {
                int year = Integer.parseInt(parts[0].trim());
                double pop = Double.parseDouble(parts[1].trim());
                result.put(year, pop);
            } catch (NumberFormatException ignored) {
                // skip malformed
            }
        }
        return result;
    }
}
