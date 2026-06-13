package Bears.BearEnvironment;

import Bears.BearAgent.BearAgent;
import MASInterface.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Generates a founding population from measured stable-state distributions
 * (produced by {@code SnapshotTool -Dsnap.mode=characterize}) instead of the
 * default forest-only, full-larder initialization. The goal is to start the
 * simulation already near its stationary state so the population does not have
 * to crash-and-recover through a multi-year transient.
 *
 * <p>It sets two things:</p>
 * <ol>
 *   <li><b>Bears</b> - age, satiety, sex and reproductive state are sampled
 *       from the measured marginal distributions, and each bear is placed in a
 *       habitat drawn from the measured bears-per-habitat mix.</li>
 *   <li><b>Map food</b> - each cell's food is set to its habitat's measured
 *       <em>drawn-down</em> fill level (fillFraction x cap), not the full cap,
 *       so there is no initial larder to fuel a breeding overshoot.</li>
 * </ol>
 *
 * <p>Sampling is from marginal distributions (age, satiety, etc. independently),
 * so correlations between attributes are not reproduced; a small residual
 * transient may remain, far smaller than the default initialization's. Behaviour
 * is untouched - only the starting distribution changes.</p>
 */
public final class BalancedInitializer {

    /** Parsed contents of a characterization directory. */
    public static final class Characterization {
        public double femaleFraction = 0.5;
        public double pregnantFraction = 0.0;   // among reproductive-age females
        public double cooldownFraction = 0.0;   // among reproductive-age females
        public double[] ageCdf;                 // cumulative over 1-year bins
        public double[] satietyCdf;             // cumulative over 0.1-wide bins
        public final EnumMap<BearCellType, Double> habitatWeight = new EnumMap<>(BearCellType.class);
        public final EnumMap<BearCellType, Double> habitatFill = new EnumMap<>(BearCellType.class);
    }

    private final Characterization c;

    public BalancedInitializer(Characterization characterization) {
        this.c = characterization;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public static Characterization load(Path dir) throws IOException {
        Characterization ch = new Characterization();

        Map<String, Double> summary = readKeyValue(dir.resolve("summary.csv"));
        ch.femaleFraction = summary.getOrDefault("femaleFraction", 0.5);
        ch.pregnantFraction = summary.getOrDefault("pregnantFraction", 0.0);
        ch.cooldownFraction = summary.getOrDefault("cooldownFraction", 0.0);

        ch.ageCdf = readFractionCdf(dir.resolve("age_histogram.csv"));
        ch.satietyCdf = readFractionCdf(dir.resolve("satiety_histogram.csv"));

        // habitat.csv: habitat,cells,bears,bearsPerCell,meanFood,maxFood,fillFraction
        List<String[]> habitat = readCsv(dir.resolve("habitat.csv"));
        double totalBears = 0;
        for (String[] row : habitat) {
            if (row.length < 7) continue;
            BearCellType type;
            try {
                type = BearCellType.valueOf(row[0].trim());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            double bears = parse(row[2]);
            double fill = parse(row[6]);
            ch.habitatWeight.put(type, bears);
            ch.habitatFill.put(type, fill);
            totalBears += bears;
        }
        // Normalize habitat weights to fractions.
        if (totalBears > 0) {
            for (BearCellType t : ch.habitatWeight.keySet()) {
                ch.habitatWeight.put(t, ch.habitatWeight.get(t) / totalBears);
            }
        }
        return ch;
    }

    // ------------------------------------------------------------------
    // Application
    // ------------------------------------------------------------------

    /**
     * Sets drawn-down map food and generates {@code count} bears into
     * {@code out}, placing them in {@code state}. Returns the last agent id used
     * (ids are 0..count-1).
     */
    public int apply(BearState state, Map<Integer, BearAgent> out, int count, RandomGenerator rng) {
        int mapLength = state.mapLength();

        // 1. Initialise each cell's food to its habitat's drawn-down fill level.
        for (int x = 0; x < mapLength; x++) {
            for (int y = 0; y < mapLength; y++) {
                BearCell cell = state.getBearCell(x, y);
                if (cell == null) continue;
                BearCellType type = cell.bearCellType();
                double cap = type.maxFood();
                if (cap < 0) continue; // NONE: leave as-is
                double fill = c.habitatFill.getOrDefault(type, 1.0);
                cell.setFood(fill * cap);
            }
        }

        // 2. Index placeable cells per habitat (packed as x*mapLength+y).
        EnumMap<BearCellType, int[]> cellsByHabitat = indexCells(state, mapLength);

        // 3. Build a habitat sampler over the types that have both weight and cells.
        List<BearCellType> habTypes = new ArrayList<>();
        List<Double> habCum = new ArrayList<>();
        double acc = 0;
        for (Map.Entry<BearCellType, Double> e : c.habitatWeight.entrySet()) {
            int[] cells = cellsByHabitat.get(e.getKey());
            if (e.getValue() <= 0 || cells == null || cells.length == 0) continue;
            acc += e.getValue();
            habTypes.add(e.getKey());
            habCum.add(acc);
        }
        // Fallback if the characterization named no usable habitat: place anywhere wild.
        BearCellType fallback = pickFallbackHabitat(cellsByHabitat);

        // 4. Generate and place bears.
        for (int i = 0; i < count; i++) {
            BearAgent.Gender gender = rng.nextDouble() < c.femaleFraction
                    ? BearAgent.Gender.FEMALE : BearAgent.Gender.MALE;
            double age = sampleBin(c.ageCdf, 1.0, rng);
            double satiety = clamp01(sampleBin(c.satietyCdf, 0.1, rng));

            boolean pregnant = false;
            double gestation = 0;
            double cooldown = 0;
            if (gender == BearAgent.Gender.FEMALE && age >= Settings.BEAR_MIN_REPRODUCTION_AGE) {
                double r = rng.nextDouble();
                if (r < c.pregnantFraction) {
                    pregnant = true;
                    gestation = rng.nextDouble() * Settings.BEAR_GESTATION_PERIOD_YEARS;
                } else if (r < c.pregnantFraction + c.cooldownFraction) {
                    cooldown = rng.nextDouble() * Settings.BEAR_REPRODUCTION_COOLDOWN_YEARS;
                }
            }

            BearCellType habitat = sampleHabitat(habTypes, habCum, fallback, rng);
            int[] cells = cellsByHabitat.get(habitat);
            int packed = cells[rng.nextInt(cells.length)];
            int x = packed / mapLength;
            int y = packed % mapLength;

            BearAgent agent = BearAgent.fromSnapshot(i, gender, age, satiety,
                    cooldown, gestation, pregnant, -1, -1);
            out.put(i, agent);
            state.placeSnapshotAgent(i, gender, x, y);
        }
        return count - 1;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private EnumMap<BearCellType, int[]> indexCells(BearState state, int mapLength) {
        EnumMap<BearCellType, Integer> counts = new EnumMap<>(BearCellType.class);
        for (int x = 0; x < mapLength; x++) {
            for (int y = 0; y < mapLength; y++) {
                BearCell cell = state.getBearCell(x, y);
                if (cell == null) continue;
                BearCellType type = cell.bearCellType();
                if (type == BearCellType.NONE) continue;
                counts.merge(type, 1, Integer::sum);
            }
        }
        EnumMap<BearCellType, int[]> result = new EnumMap<>(BearCellType.class);
        EnumMap<BearCellType, Integer> cursor = new EnumMap<>(BearCellType.class);
        for (Map.Entry<BearCellType, Integer> e : counts.entrySet()) {
            result.put(e.getKey(), new int[e.getValue()]);
            cursor.put(e.getKey(), 0);
        }
        for (int x = 0; x < mapLength; x++) {
            for (int y = 0; y < mapLength; y++) {
                BearCell cell = state.getBearCell(x, y);
                if (cell == null) continue;
                BearCellType type = cell.bearCellType();
                if (type == BearCellType.NONE) continue;
                int idx = cursor.get(type);
                result.get(type)[idx] = x * mapLength + y;
                cursor.put(type, idx + 1);
            }
        }
        return result;
    }

    private static BearCellType pickFallbackHabitat(EnumMap<BearCellType, int[]> cellsByHabitat) {
        // Prefer forest, then any non-empty wild habitat.
        for (BearCellType pref : new BearCellType[]{
                BearCellType.FOREST, BearCellType.FIELD, BearCellType.MOUNTAIN,
                BearCellType.ROAD, BearCellType.VILLAGE}) {
            int[] cells = cellsByHabitat.get(pref);
            if (cells != null && cells.length > 0) return pref;
        }
        throw new IllegalStateException("No placeable habitat cells on the map.");
    }

    private static BearCellType sampleHabitat(List<BearCellType> types, List<Double> cum,
                                              BearCellType fallback, RandomGenerator rng) {
        if (types.isEmpty()) return fallback;
        double total = cum.get(cum.size() - 1);
        double u = rng.nextDouble() * total;
        for (int i = 0; i < types.size(); i++) {
            if (u <= cum.get(i)) return types.get(i);
        }
        return types.get(types.size() - 1);
    }

    /** Samples a value from a CDF over equal-width bins, uniform within the bin. */
    private static double sampleBin(double[] cdf, double binWidth, RandomGenerator rng) {
        if (cdf == null || cdf.length == 0) return 0;
        double u = rng.nextDouble();
        int bin = 0;
        while (bin < cdf.length - 1 && u > cdf[bin]) bin++;
        return (bin + rng.nextDouble()) * binWidth;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static Map<String, Double> readKeyValue(Path file) throws IOException {
        Map<String, Double> map = new java.util.LinkedHashMap<>();
        for (String[] row : readCsv(file)) {
            if (row.length >= 2) {
                try {
                    map.put(row[0].trim(), Double.parseDouble(row[1].trim()));
                } catch (NumberFormatException ignored) {
                    // non-numeric value (e.g. counts as ints still parse) - skip if it fails
                }
            }
        }
        return map;
    }

    /** Reads the third column ("fraction") of a histogram CSV into a CDF. */
    private static double[] readFractionCdf(Path file) throws IOException {
        List<String[]> rows = readCsv(file);
        double[] cdf = new double[rows.size()];
        double acc = 0;
        for (int i = 0; i < rows.size(); i++) {
            double frac = rows.get(i).length >= 3 ? parse(rows.get(i)[2]) : 0;
            acc += frac;
            cdf[i] = acc;
        }
        // Guard against rounding leaving the last entry < 1.
        if (cdf.length > 0) cdf[cdf.length - 1] = Math.max(cdf[cdf.length - 1], 1.0);
        return cdf;
    }

    /** Reads a CSV, skipping the header row. Returns data rows split on commas. */
    private static List<String[]> readCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) { // skip header
            String line = lines.get(i);
            if (line == null || line.isBlank()) continue;
            rows.add(line.split(",", -1));
        }
        return rows;
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
