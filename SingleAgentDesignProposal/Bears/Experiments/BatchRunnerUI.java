package Bears.Experiments;

import Bears.BearEnvironment.BearCellType;
import Bears.BearEnvironment.MapGridLoader;
import MASInterface.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Minimal desktop UI for dissertation demos:
 * - inspect/apply Settings values,
 * - define and run a scenario,
 * - preview the current map file.
 */
public final class BatchRunnerUI extends JFrame {

    private final SettingsTableModel settingsTableModel = new SettingsTableModel();
    private final JTable settingsTable = new JTable(settingsTableModel);

    private final JTextField scenarioIdField = new JTextField("baseline-ui");
    private final JTextField replicatesField = new JTextField("3");
    private final JTextField yearsField = new JTextField("30");
    private final JTextField initialBearsField = new JTextField("2000");
    private final JTextField mapLengthField = new JTextField("200");
    private final JTextField baseSeedField = new JTextField("42");
    private final JTextField mapSourceField = new JTextField("reference-data/romania-map-clc2018.txt");
    private final JTextField scheduleField = new JTextField("SingleAgentDesignProposal/Bears/Experiments/Schedules/schedule.csv");
    private final JTextField referenceField = new JTextField("reference-data/romania_brown_bear_population.csv");
    private final JTextField outputDirField = new JTextField("experiment-output");

    private final JComboBox<String> overrideFieldCombo = new JComboBox<>();
    private final JTextField overrideValueField = new JTextField(14);
    private final DefaultTableModel overridesModel = new DefaultTableModel(new String[]{"Settings field", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JTable overridesTable = new JTable(overridesModel);

    private final JTextArea logArea = new JTextArea();

    private final JTextField previewMapPathField = new JTextField("reference-data/romania-map-clc2018.txt");
    private final JLabel mapStatsLabel = new JLabel("No map loaded.");
    private final MapPreviewPanel mapPreviewPanel = new MapPreviewPanel();

    public BatchRunnerUI() {
        super("Bear Simulation - Minimal Scenario UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Settings", buildSettingsTab());
        tabs.addTab("Scenario", buildScenarioTab());
        tabs.addTab("Map", buildMapTab());

        setContentPane(tabs);

        loadSettingsIntoTable();
        loadOverrideFieldCombo();
        previewMapPathField.setText(mapSourceField.getText().trim());
    }

    private JPanel buildSettingsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        settingsTable.setAutoCreateRowSorter(true);
        settingsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh from Settings");
        refreshButton.addActionListener(e -> loadSettingsIntoTable());

        JButton applyButton = new JButton("Apply edited values");
        applyButton.addActionListener(e -> applyEditedSettings());

        topButtons.add(refreshButton);
        topButtons.add(applyButton);

        JTextArea hintArea = new JTextArea(
                "Tip: Edit only numeric/boolean fields you need. " +
                        "These changes apply in-process and are useful for quick scenario prototyping.");
        hintArea.setWrapStyleWord(true);
        hintArea.setLineWrap(true);
        hintArea.setEditable(false);
        hintArea.setOpaque(false);

        panel.add(topButtons, BorderLayout.NORTH);
        panel.add(new JScrollPane(settingsTable), BorderLayout.CENTER);
        panel.add(hintArea, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildScenarioTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel form = new JPanel(new GridLayout(0, 4, 6, 6));
        addField(form, "Scenario id", scenarioIdField);
        addField(form, "Replicates", replicatesField);
        addField(form, "Years", yearsField);
        addField(form, "Initial bears", initialBearsField);
        addField(form, "Map length", mapLengthField);
        addField(form, "Base seed", baseSeedField);
        addField(form, "Map source", mapSourceField);
        addField(form, "Schedule file", scheduleField);
        addField(form, "Reference CSV", referenceField);
        addField(form, "Output directory", outputDirField);

        JButton browseMapButton = new JButton("Browse map");
        browseMapButton.addActionListener(this::browseMapFile);
        form.add(new JLabel());
        form.add(browseMapButton);

        JPanel overridesPanel = new JPanel(new BorderLayout(6, 6));
        overridesPanel.setBorder(BorderFactory.createTitledBorder("Scenario overrides"));

        JPanel addOverridePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addOverrideButton = new JButton("Add override");
        addOverrideButton.addActionListener(e -> addOverride());
        JButton removeOverrideButton = new JButton("Remove selected");
        removeOverrideButton.addActionListener(e -> removeSelectedOverride());
        addOverridePanel.add(overrideFieldCombo);
        addOverridePanel.add(overrideValueField);
        addOverridePanel.add(addOverrideButton);
        addOverridePanel.add(removeOverrideButton);

        overridesPanel.add(addOverridePanel, BorderLayout.NORTH);
        overridesPanel.add(new JScrollPane(overridesTable), BorderLayout.CENTER);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveProfileButton = new JButton("Save scenario profile");
        saveProfileButton.addActionListener(e -> saveScenarioProfile());

        JButton loadProfileButton = new JButton("Load scenario profile");
        loadProfileButton.addActionListener(e -> loadScenarioProfile());

        JButton runButton = new JButton("Run scenario");
        runButton.addActionListener(e -> runScenario());

        actionsPanel.add(saveProfileButton);
        actionsPanel.add(loadProfileButton);
        actionsPanel.add(runButton);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Run log"));

        JPanel upper = new JPanel(new BorderLayout(6, 6));
        upper.add(form, BorderLayout.NORTH);
        upper.add(overridesPanel, BorderLayout.CENTER);
        upper.add(actionsPanel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, logScroll);
        split.setResizeWeight(0.6);

        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMapTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JButton loadButton = new JButton("Load map preview");
        loadButton.addActionListener(e -> loadMapPreview());

        top.add(new JLabel("Map file"), BorderLayout.WEST);
        top.add(previewMapPathField, BorderLayout.CENTER);
        top.add(loadButton, BorderLayout.EAST);

        mapPreviewPanel.setPreferredSize(new Dimension(900, 600));

        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(mapPreviewPanel), BorderLayout.CENTER);
        panel.add(mapStatsLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void addField(JPanel form, String label, JTextField field) {
        form.add(new JLabel(label));
        form.add(field);
    }

    private void loadSettingsIntoTable() {
        settingsTableModel.reloadFromSettings();
    }

    private void applyEditedSettings() {
        try {
            settingsTableModel.applyToSettings();
            appendLog("Applied edited settings values to runtime.");
            JOptionPane.showMessageDialog(this, "Settings applied.", "Settings", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Failed to apply settings: " + ex.getMessage());
        }
    }

    private void loadOverrideFieldCombo() {
        overrideFieldCombo.removeAllItems();
        for (String fieldName : SettingsTableModel.getStaticFieldNames()) {
            overrideFieldCombo.addItem(fieldName);
        }
    }

    private void addOverride() {
        Object field = overrideFieldCombo.getSelectedItem();
        String value = overrideValueField.getText().trim();
        if (field == null || value.isEmpty()) {
            showError("Choose a Settings field and enter a value.");
            return;
        }
        overridesModel.addRow(new Object[]{field.toString(), value});
        overrideValueField.setText("");
    }

    private void removeSelectedOverride() {
        int row = overridesTable.getSelectedRow();
        if (row >= 0) {
            overridesModel.removeRow(row);
        }
    }

    private void browseMapFile(ActionEvent ignored) {
        JFileChooser chooser = new JFileChooser(Paths.get("").toAbsolutePath().toFile());
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String selected = chooser.getSelectedFile().toPath().toString();
            mapSourceField.setText(selected);
            previewMapPathField.setText(selected);
        }
    }

    private void saveScenarioProfile() {
        try {
            String scenarioId = scenarioIdField.getText().trim();
            if (scenarioId.isEmpty()) {
                throw new IllegalArgumentException("Scenario id cannot be empty.");
            }

            Properties props = new Properties();
            props.setProperty("scenario.id", scenarioId);
            props.setProperty("replicates", replicatesField.getText().trim());
            props.setProperty("years", yearsField.getText().trim());
            props.setProperty("initialBears", initialBearsField.getText().trim());
            props.setProperty("mapLength", mapLengthField.getText().trim());
            props.setProperty("baseSeed", baseSeedField.getText().trim());
            props.setProperty("mapSource", mapSourceField.getText().trim());
            props.setProperty("schedule", scheduleField.getText().trim());
            props.setProperty("reference", referenceField.getText().trim());
            props.setProperty("outputDir", outputDirField.getText().trim());

            for (int i = 0; i < overridesModel.getRowCount(); i++) {
                String key = Objects.toString(overridesModel.getValueAt(i, 0), "").trim();
                String value = Objects.toString(overridesModel.getValueAt(i, 1), "").trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    props.setProperty("override." + key, value);
                }
            }

            Path profileDir = Paths.get("experiment-output", "scenario-profiles");
            Files.createDirectories(profileDir);
            Path out = profileDir.resolve(scenarioId + ".properties");
            try (Writer writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                props.store(writer, "BatchRunnerUI scenario profile");
            }

            appendLog("Saved scenario profile: " + out.toAbsolutePath());
            JOptionPane.showMessageDialog(this, "Saved: " + out.toAbsolutePath(), "Profile saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            showError("Failed to save profile: " + ex.getMessage());
        }
    }

    private void loadScenarioProfile() {
        JFileChooser chooser = new JFileChooser(Paths.get("experiment-output", "scenario-profiles").toFile());
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        try {
            Properties props = new Properties();
            try (var in = Files.newInputStream(chooser.getSelectedFile().toPath())) {
                props.load(in);
            }

            scenarioIdField.setText(props.getProperty("scenario.id", scenarioIdField.getText()));
            replicatesField.setText(props.getProperty("replicates", replicatesField.getText()));
            yearsField.setText(props.getProperty("years", yearsField.getText()));
            initialBearsField.setText(props.getProperty("initialBears", initialBearsField.getText()));
            mapLengthField.setText(props.getProperty("mapLength", mapLengthField.getText()));
            baseSeedField.setText(props.getProperty("baseSeed", baseSeedField.getText()));
            mapSourceField.setText(props.getProperty("mapSource", mapSourceField.getText()));
            scheduleField.setText(props.getProperty("schedule", scheduleField.getText()));
            referenceField.setText(props.getProperty("reference", referenceField.getText()));
            outputDirField.setText(props.getProperty("outputDir", outputDirField.getText()));
            previewMapPathField.setText(mapSourceField.getText().trim());

            overridesModel.setRowCount(0);
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith("override.")) {
                    overridesModel.addRow(new Object[]{name.substring("override.".length()), props.getProperty(name)});
                }
            }

            appendLog("Loaded scenario profile: " + chooser.getSelectedFile().toPath());
        } catch (Exception ex) {
            showError("Failed to load profile: " + ex.getMessage());
        }
    }

    private void runScenario() {
        final String scenarioId = scenarioIdField.getText().trim();
        if (scenarioId.isEmpty()) {
            showError("Scenario id cannot be empty.");
            return;
        }

        final int replicates;
        final double years;
        final int initialBears;
        final int mapLength;
        final long baseSeed;
        try {
            replicates = Integer.parseInt(replicatesField.getText().trim());
            years = Double.parseDouble(yearsField.getText().trim());
            initialBears = Integer.parseInt(initialBearsField.getText().trim());
            mapLength = Integer.parseInt(mapLengthField.getText().trim());
            baseSeed = Long.parseLong(baseSeedField.getText().trim());
        } catch (NumberFormatException nfe) {
            showError("Replicates/years/initial bears/map length/base seed must be numeric.");
            return;
        }

        final String mapSource = mapSourceField.getText().trim();
        final String schedulePath = scheduleField.getText().trim();
        final String referencePath = referenceField.getText().trim();
        final String outputDir = outputDirField.getText().trim();

        final Map<String, String> overrides = new LinkedHashMap<>();
        for (int i = 0; i < overridesModel.getRowCount(); i++) {
            String key = Objects.toString(overridesModel.getValueAt(i, 0), "").trim();
            String value = Objects.toString(overridesModel.getValueAt(i, 1), "").trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                overrides.put(key, value);
            }
        }

        appendLog("Starting scenario run: " + scenarioId + " (replicates=" + replicates + ", years=" + years + ")");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                long maxTicks = (long) Math.ceil(years / Settings.ONE_TICK_IN_YEARS);
                NavigableMap<Integer, Double> reference = loadReferenceSeries(referencePath);
                ParameterSchedule schedule = loadSchedule(schedulePath);

                Scenario scenario = new Scenario(
                        scenarioId,
                        replicates,
                        baseSeed,
                        maxTicks,
                        initialBears,
                        mapLength,
                        overrides,
                        reference,
                        schedule,
                        mapSource
                );

                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try (PrintStream tee = new PrintStream(new TeeOutputStream(originalOut, new SwingTextAreaStream(logArea)), true, StandardCharsets.UTF_8)) {
                    System.setOut(tee);
                    System.setErr(tee);
                    BatchRunner.runScenarios(List.of(scenario), resolvePath(outputDir));
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    appendLog("Run finished successfully.");
                } catch (Exception ex) {
                    appendLog("Run failed: " + ex.getMessage());
                    showError("Run failed: " + ex.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void loadMapPreview() {
        String rawPath = previewMapPathField.getText().trim();
        if (rawPath.isEmpty()) {
            showError("Map path is empty.");
            return;
        }

        try {
            Path path = resolvePath(rawPath);
            MapGridLoader.Grid grid = MapGridLoader.load(path);
            mapPreviewPanel.setGrid(grid);
            mapStatsLabel.setText(formatMapStats(path, grid));
            appendLog("Loaded map preview: " + path.toAbsolutePath());
        } catch (Exception ex) {
            showError("Failed to load map: " + ex.getMessage());
        }
    }

    private NavigableMap<Integer, Double> loadReferenceSeries(String referencePath) throws IOException {
        if (referencePath == null || referencePath.isBlank()) {
            return new TreeMap<>();
        }
        Path path = resolvePath(referencePath);
        if (!Files.exists(path)) {
            appendLog("Reference CSV not found at " + path + "; running without reference.");
            return new TreeMap<>();
        }

        NavigableMap<Integer, Double> raw = ReferenceData.loadAnnualPopulation(path);
        int startYear = raw.isEmpty() ? 0 : raw.firstKey();
        NavigableMap<Integer, Double> shifted = new TreeMap<>();
        for (Map.Entry<Integer, Double> e : raw.entrySet()) {
            shifted.put(e.getKey() - startYear, e.getValue());
        }
        return shifted;
    }

    private ParameterSchedule loadSchedule(String schedulePath) throws IOException {
        if (schedulePath == null || schedulePath.isBlank()) {
            return ParameterSchedule.empty();
        }
        Path path = resolvePath(schedulePath);
        if (!Files.exists(path)) {
            appendLog("Schedule file not found at " + path + "; running stationary scenario.");
            return ParameterSchedule.empty();
        }
        return ParameterSchedule.load(path);
    }

    private String formatMapStats(Path path, MapGridLoader.Grid grid) {
        Map<BearCellType, Integer> counts = new LinkedHashMap<>();
        for (BearCellType t : BearCellType.values()) {
            counts.put(t, 0);
        }
        int total = grid.width * grid.height;
        for (int y = 0; y < grid.height; y++) {
            for (int x = 0; x < grid.width; x++) {
                BearCellType t = grid.cells[x][y];
                counts.put(t, counts.get(t) + 1);
            }
        }

        return String.format(
                Locale.ROOT,
                "%s | %dx%d | F %.1f%%, I %.1f%%, V %.1f%%, R %.1f%%, M %.1f%%, N %.1f%%",
                path.toAbsolutePath(),
                grid.width,
                grid.height,
                pct(counts.get(BearCellType.FOREST), total),
                pct(counts.get(BearCellType.FIELD), total),
                pct(counts.get(BearCellType.VILLAGE), total),
                pct(counts.get(BearCellType.ROAD), total),
                pct(counts.get(BearCellType.MOUNTAIN), total),
                pct(counts.get(BearCellType.NONE), total)
        );
    }

    private static double pct(int count, int total) {
        return total <= 0 ? 0.0 : (100.0 * count / total);
    }

    private static Path resolvePath(String relativeOrAbsolute) {
        Path direct = Paths.get(relativeOrAbsolute);
        if (direct.isAbsolute() || Files.exists(direct)) {
            return direct;
        }
        Path probe = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 7; depth++) {
            Path candidate = probe.resolve(relativeOrAbsolute);
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path parent = probe.getParent();
            if (parent == null) {
                break;
            }
            probe = parent;
        }
        return direct;
    }

    private void appendLog(String message) {
        String line = String.format("[%s] %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), message);
        SwingUtilities.invokeLater(() -> {
            logArea.append(line);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void showError(String message) {
        appendLog("ERROR: " + message);
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BatchRunnerUI().setVisible(true));
    }

    private static final class SettingEntry {
        final String name;
        final Class<?> type;
        String value;

        SettingEntry(String name, Class<?> type, String value) {
            this.name = name;
            this.type = type;
            this.value = value;
        }
    }

    private static final class SettingsTableModel extends AbstractTableModel {
        private final List<SettingEntry> rows = new ArrayList<>();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            if (column == 0) return "Field";
            if (column == 1) return "Type";
            return "Value";
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            SettingEntry e = rows.get(rowIndex);
            if (columnIndex == 0) return e.name;
            if (columnIndex == 1) return e.type.getSimpleName();
            return e.value;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 2) {
                rows.get(rowIndex).value = String.valueOf(aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }

        void reloadFromSettings() {
            rows.clear();
            for (Field field : Settings.class.getFields()) {
                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    continue;
                }
                try {
                    Object value = field.get(null);
                    rows.add(new SettingEntry(field.getName(), field.getType(), String.valueOf(value)));
                } catch (IllegalAccessException ignored) {
                    // skip inaccessible fields
                }
            }
            rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
            fireTableDataChanged();
        }

        void applyToSettings() throws IllegalAccessException {
            for (SettingEntry e : rows) {
                Field field;
                try {
                    field = Settings.class.getField(e.name);
                } catch (NoSuchFieldException ex) {
                    continue;
                }
                Object parsed = parseByType(e.type, e.value);
                field.set(null, parsed);
            }
        }

        private static Object parseByType(Class<?> type, String raw) {
            if (type == int.class || type == Integer.class) return Integer.parseInt(raw.trim());
            if (type == long.class || type == Long.class) return Long.parseLong(raw.trim());
            if (type == double.class || type == Double.class) return Double.parseDouble(raw.trim());
            if (type == float.class || type == Float.class) return Float.parseFloat(raw.trim());
            if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(raw.trim());
            return raw;
        }

        static List<String> getStaticFieldNames() {
            List<String> names = new ArrayList<>();
            for (Field field : Settings.class.getFields()) {
                if ((field.getModifiers() & Modifier.STATIC) != 0) {
                    names.add(field.getName());
                }
            }
            names.sort(String::compareToIgnoreCase);
            return names;
        }
    }

    private static final class MapPreviewPanel extends JPanel {
        private BufferedImage mapImage;

        void setGrid(MapGridLoader.Grid grid) {
            BufferedImage img = new BufferedImage(grid.width, grid.height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < grid.height; y++) {
                for (int x = 0; x < grid.width; x++) {
                    img.setRGB(x, y, colorFor(grid.cells[x][y]).getRGB());
                }
            }
            this.mapImage = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setColor(new Color(245, 245, 245));
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (mapImage == null) {
                    g2.setColor(Color.DARK_GRAY);
                    g2.drawString("Load a map file to preview its terrain layout.", 20, 30);
                    return;
                }

                Image scaled = mapImage.getScaledInstance(getWidth(), getHeight(), Image.SCALE_FAST);
                g2.drawImage(scaled, 0, 0, null);
            } finally {
                g2.dispose();
            }
        }

        private Color colorFor(BearCellType type) {
            return switch (type) {
                case FOREST -> new Color(34, 139, 34);
                case FIELD -> new Color(210, 180, 90);
                case VILLAGE -> new Color(178, 34, 34);
                case ROAD -> new Color(80, 80, 80);
                case MOUNTAIN -> new Color(120, 120, 150);
                case NONE -> new Color(225, 235, 245);
            };
        }
    }

    private static final class SwingTextAreaStream extends OutputStream {
        private final JTextArea area;
        private final StringBuilder lineBuffer = new StringBuilder();

        SwingTextAreaStream(JTextArea area) {
            this.area = area;
        }

        @Override
        public void write(int b) {
            char c = (char) b;
            lineBuffer.append(c);
            if (c == '\n') {
                flushBuffer();
            }
        }

        @Override
        public void flush() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (lineBuffer.isEmpty()) {
                return;
            }
            final String text = lineBuffer.toString();
            lineBuffer.setLength(0);
            SwingUtilities.invokeLater(() -> {
                area.append(text);
                area.setCaretPosition(area.getDocument().getLength());
            });
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream left;
        private final OutputStream right;

        TeeOutputStream(OutputStream left, OutputStream right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public void write(int b) throws IOException {
            left.write(b);
            right.write(b);
        }

        @Override
        public void flush() throws IOException {
            left.flush();
            right.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
