package Bears.Experiments;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Tiny CSV helper - no external dependencies. */
public final class CsvWriter {

    private CsvWriter() {}

    public static void writeRows(Path path, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            w.write(joinCsv(header));
            w.newLine();
            for (List<String> row : rows) {
                w.write(joinCsv(row));
                w.newLine();
            }
        }
    }

    /** Append a single row to a CSV file, writing the header if the file does not yet exist. */
    public static void appendRow(Path path, List<String> header, List<String> row) throws IOException {
        Files.createDirectories(path.getParent());
        boolean exists = Files.exists(path);
        try (BufferedWriter w = Files.newBufferedWriter(
                path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!exists) {
                w.write(joinCsv(header));
                w.newLine();
            }
            w.write(joinCsv(row));
            w.newLine();
        }
    }

    private static String joinCsv(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(fields.get(i)));
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.indexOf(',') < 0 && s.indexOf('"') < 0 && s.indexOf('\n') < 0) {
            return s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
