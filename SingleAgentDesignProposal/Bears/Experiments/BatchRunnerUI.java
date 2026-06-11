package Bears.Experiments;

import Bears.BearEnvironment.BearCellType;
import Bears.BearEnvironment.DeathCause;
import Bears.BearEnvironment.MapGridLoader;
import MASInterface.Settings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.JProgressBar;
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
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;

/**
 * Minimal desktop UI for dissertation demos:
 * - inspect/apply Settings values,
 * - define and run a scenario,
 * - preview the generated simulation map.
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
    private final JTextField mapSourceField = new JTextField("map-data/u2018_clc2018_v2020_20u1_raster100m/DATA/U2018_CLC2018_V2020_20u1.tif");
    private final JTextField scheduleField = new JTextField("SingleAgentDesignProposal/Bears/Experiments/Schedules/schedule.csv");
    private final JTextField referenceField = new JTextField("reference-data/romania_brown_bear_population.csv");
    private final JTextField outputDirField = new JTextField("experiment-output");
    private final JButton runScenarioButton = new JButton("Run scenario");
    private final JButton stopScenarioButton = new JButton("Force stop");

    private final JComboBox<String> overrideFieldCombo = new JComboBox<>();
    private final JTextField overrideYearField = new JTextField(6);
    private final JTextField overrideValueField = new JTextField(14);
    private final DefaultTableModel overridesModel = new DefaultTableModel(new String[]{"Year (optional)", "Settings field", "Value"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return true;
        }
    };
    private final JTable overridesTable = new JTable(overridesModel);

    private final JTextArea logArea = new JTextArea();
    private final PopulationChartPanel populationChartPanel = new PopulationChartPanel();
    private final JComboBox<String> chartSelectorCombo = new JComboBox<>();
    private final JComboBox<ChartTimeStep> chartTimeStepCombo = new JComboBox<>();
    private final JCheckBox chartShowSeasonsCheckbox = new JCheckBox("Show seasons", true);
    private final JCheckBox chartShowYearBoundariesCheckbox = new JCheckBox("Show year boundaries", true);
    private final Map<String, ChartSeriesData> chartSeriesByKey = new LinkedHashMap<>();
    private BatchRunner.BatchOutcome lastChartOutcome = null;
    private final JProgressBar runProgressBar = new JProgressBar(0, 1);
    private final JLabel progressStateValueLabel = new JLabel("Idle");
    private final JLabel progressReplicateValueLabel = new JLabel("- / -");
    private final JLabel progressEtaValueLabel = new JLabel("N/A");
    private final JLabel progressElapsedValueLabel = new JLabel("00:00:00");
    private RunProgressTracker progressTracker = null;
    private SwingWorker<BatchRunner.BatchOutcome, String> currentRunWorker = null;

    private final JTextField previewMapPathField = new JTextField("reference-data/romania-map-clc2018.txt");
    private final JLabel mapStatsLabel = new JLabel("No map loaded.");
    private final MapPreviewPanel mapPreviewPanel = new MapPreviewPanel();

    private static final Pattern RUN_REPLICATE_PATTERN = Pattern.compile("-r(\\d+)\\b");
    private static final Path DEFAULT_MASK_PATH = Paths.get("map-data", "romania-outline", "gadm41_ROU.gpkg");

    public BatchRunnerUI() {
        super("Bear Simulation - Minimal Scenario UI");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 780);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Settings", buildSettingsTab());
        tabs.addTab("Scenario", buildScenarioTab());
        tabs.addTab("Charts", buildChartsTab());
        tabs.addTab("Map", buildMapTab());

        setContentPane(tabs);

        loadSettingsIntoTable();
        loadOverrideFieldCombo();
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
        addField(form, "Source raster map", mapSourceField);
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
        JButton importScheduleButton = new JButton("Import schedule");
        importScheduleButton.addActionListener(e -> importScheduleIntoOverridesTable());
        addOverridePanel.add(new JLabel("Year"));
        addOverridePanel.add(overrideYearField);
        addOverridePanel.add(overrideFieldCombo);
        addOverridePanel.add(overrideValueField);
        addOverridePanel.add(addOverrideButton);
        addOverridePanel.add(removeOverrideButton);
        addOverridePanel.add(importScheduleButton);

        overridesPanel.add(addOverridePanel, BorderLayout.NORTH);
        overridesPanel.add(new JScrollPane(overridesTable), BorderLayout.CENTER);

        JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton saveProfileButton = new JButton("Save scenario profile");
        saveProfileButton.addActionListener(e -> saveScenarioProfile());

        JButton loadProfileButton = new JButton("Load scenario profile");
        loadProfileButton.addActionListener(e -> loadScenarioProfile());

        runScenarioButton.addActionListener(e -> runScenario());
        stopScenarioButton.setEnabled(false);
        stopScenarioButton.addActionListener(e -> forceStopCurrentScenario());

        actionsPanel.add(saveProfileButton);
        actionsPanel.add(loadProfileButton);
        actionsPanel.add(runScenarioButton);
        actionsPanel.add(stopScenarioButton);

        JPanel progressPanel = buildProgressPanel();

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Run log"));

        JPanel upper = new JPanel(new BorderLayout(6, 6));
        upper.add(form, BorderLayout.NORTH);
        upper.add(overridesPanel, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.add(actionsPanel, BorderLayout.NORTH);
        south.add(progressPanel, BorderLayout.CENTER);
        upper.add(south, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, logScroll);
        split.setResizeWeight(0.6);

        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildChartsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        populationChartPanel.setBorder(BorderFactory.createTitledBorder("Population over time"));
        populationChartPanel.setPreferredSize(new Dimension(900, 620));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Graph"));
        chartSelectorCombo.addActionListener(e -> refreshSelectedChart());
        top.add(chartSelectorCombo);
        top.add(new JLabel("Time step"));
        if (chartTimeStepCombo.getItemCount() == 0) {
            for (ChartTimeStep ts : ChartTimeStep.values()) {
                chartTimeStepCombo.addItem(ts);
            }
            chartTimeStepCombo.setSelectedItem(ChartTimeStep.YEAR);
        }
        chartTimeStepCombo.addActionListener(e -> refreshChartsForSelectedTimeStep());
        top.add(chartTimeStepCombo);
        chartShowSeasonsCheckbox.addActionListener(e -> populationChartPanel.repaint());
        top.add(chartShowSeasonsCheckbox);
        chartShowYearBoundariesCheckbox.addActionListener(e -> populationChartPanel.repaint());
        top.add(chartShowYearBoundariesCheckbox);
        initializeChartSelector();

        JTextArea hint = new JTextArea("This chart updates automatically after each scenario run. Use the dropdown to view population, births, total deaths, and deaths by cause.");
        hint.setEditable(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setOpaque(false);

        panel.add(top, BorderLayout.NORTH);
        panel.add(populationChartPanel, BorderLayout.CENTER);
        panel.add(hint, BorderLayout.SOUTH);
        return panel;
    }

    private void initializeChartSelector() {
        ChartTimeStep ts = getSelectedChartTimeStep();
        chartSeriesByKey.clear();
        chartSelectorCombo.removeAllItems();

        chartSeriesByKey.put("Population", new ChartSeriesData("Population", List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel));
        chartSeriesByKey.put("Births", new ChartSeriesData("Births", List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel));
        chartSeriesByKey.put("Deaths (total)", new ChartSeriesData("Deaths (total)", List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel));
        for (DeathCause cause : DeathCause.values()) {
            String key = "Deaths (" + formatDeathCauseLabel(cause) + ")";
            chartSeriesByKey.put(key, new ChartSeriesData(key, List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel));
        }

        for (String key : chartSeriesByKey.keySet()) {
            chartSelectorCombo.addItem(key);
        }
        if (chartSelectorCombo.getItemCount() > 0) {
            chartSelectorCombo.setSelectedIndex(0);
        }
        refreshSelectedChart();
    }

    private static String formatDeathCauseLabel(DeathCause cause) {
        String[] parts = cause.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    private void refreshSelectedChart() {
        String selected = (String) chartSelectorCombo.getSelectedItem();
        ChartTimeStep ts = getSelectedChartTimeStep();
        if (selected == null) {
            populationChartPanel.setBorder(BorderFactory.createTitledBorder("Chart"));
            populationChartPanel.setSeries(List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel);
            populationChartPanel.setChartTimeStep(ts);
            populationChartPanel.setShowSeasons(chartShowSeasonsCheckbox.isSelected());
            populationChartPanel.setShowYearBoundaries(chartShowYearBoundariesCheckbox.isSelected());
            return;
        }
        ChartSeriesData data = chartSeriesByKey.get(selected);
        if (data == null) {
            populationChartPanel.setBorder(BorderFactory.createTitledBorder(selected));
            populationChartPanel.setSeries(List.of(), List.of(), "No data", ts.axisSuffix, ts.tooltipLabel);
            populationChartPanel.setChartTimeStep(ts);
            populationChartPanel.setShowSeasons(chartShowSeasonsCheckbox.isSelected());
            populationChartPanel.setShowYearBoundaries(chartShowYearBoundariesCheckbox.isSelected());
            return;
        }
        populationChartPanel.setBorder(BorderFactory.createTitledBorder(data.title));
        populationChartPanel.setSeries(data.xValues, data.yValues, data.subtitle, data.xAxisSuffix, data.xTooltipLabel);
        populationChartPanel.setChartTimeStep(ts);
        populationChartPanel.setShowSeasons(chartShowSeasonsCheckbox.isSelected());
        populationChartPanel.setShowYearBoundaries(chartShowYearBoundariesCheckbox.isSelected());
    }

    private ChartTimeStep getSelectedChartTimeStep() {
        ChartTimeStep selected = (ChartTimeStep) chartTimeStepCombo.getSelectedItem();
        return selected != null ? selected : ChartTimeStep.YEAR;
    }

    private void refreshChartsForSelectedTimeStep() {
        ChartTimeStep selected = (ChartTimeStep) chartTimeStepCombo.getSelectedItem();
        populationChartPanel.setChartTimeStep(selected != null ? selected : ChartTimeStep.YEAR);
        if (lastChartOutcome != null && !lastChartOutcome.runResults.isEmpty()) {
            updateCharts(lastChartOutcome);
        } else {
            initializeChartSelector();
        }
    }

    private JPanel buildProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createTitledBorder("Run progress"));

        runProgressBar.setStringPainted(true);
        runProgressBar.setValue(0);
        runProgressBar.setString("0 / 0 replicates");

        JPanel metrics = new JPanel(new GridLayout(0, 4, 8, 4));
        metrics.add(new JLabel("State"));
        metrics.add(progressStateValueLabel);
        metrics.add(new JLabel("Replicate"));
        metrics.add(progressReplicateValueLabel);
        metrics.add(new JLabel("Estimated completion"));
        metrics.add(progressEtaValueLabel);
        metrics.add(new JLabel("Elapsed"));
        metrics.add(progressElapsedValueLabel);

        panel.add(runProgressBar, BorderLayout.NORTH);
        panel.add(metrics, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMapTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        JButton loadButton = new JButton("Load map preview");
        loadButton.addActionListener(e -> loadMapPreview());

        top.add(new JLabel("Generated simulation map"), BorderLayout.WEST);
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
        overrideFieldCombo.addActionListener(e -> syncOverrideValueWithSelectedField());
        if (overrideFieldCombo.getItemCount() > 0) {
            overrideFieldCombo.setSelectedIndex(0);
            syncOverrideValueWithSelectedField();
        }
    }

    private void syncOverrideValueWithSelectedField() {
        Object selected = overrideFieldCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            Field field = Settings.class.getField(selected.toString());
            overrideValueField.setText(String.valueOf(field.get(null)));
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Keep the current value if the field cannot be resolved.
        }
    }

    private void addOverride() {
        Object field = overrideFieldCombo.getSelectedItem();
        String year = overrideYearField.getText().trim();
        String value = overrideValueField.getText().trim();
        if (field == null || value.isEmpty()) {
            showError("Choose a Settings field and enter a value.");
            return;
        }
        if (!year.isEmpty()) {
            try {
                Double.parseDouble(year);
            } catch (NumberFormatException nfe) {
                showError("Year must be numeric (e.g. 10 or 12.5), or blank for static override.");
                return;
            }
        }
        overridesModel.addRow(new Object[]{year, field.toString(), value});
        overrideYearField.setText("");
        overrideValueField.setText("");
    }

    private void removeSelectedOverride() {
        int row = overridesTable.getSelectedRow();
        if (row >= 0) {
            overridesModel.removeRow(row);
        }
    }

    private void importScheduleIntoOverridesTable() {
        try {
            String schedulePath = scheduleField.getText().trim();
            if (schedulePath.isEmpty()) {
                showError("Schedule path is empty.");
                return;
            }

            Path resolved = resolvePath(schedulePath);
            if (!Files.exists(resolved)) {
                showError("Schedule file not found: " + resolved.toAbsolutePath());
                return;
            }

            ParameterSchedule schedule = ParameterSchedule.load(resolved);
            overridesModel.setRowCount(0);
            for (ParameterSchedule.Entry entry : schedule.entries()) {
                overridesModel.addRow(new Object[]{formatScheduleYear(entry.simulationYear), entry.parameter, entry.value});
            }

            appendLog("Imported schedule into overrides table: " + resolved.toAbsolutePath()
                    + " (entries=" + schedule.entries().size() + ")");
        } catch (Exception ex) {
            showError("Failed to import schedule: " + ex.getMessage());
        }
    }

    private static String formatScheduleYear(double year) {
        long asLong = (long) year;
        if (year == asLong) {
            return Long.toString(asLong);
        }
        return Double.toString(year);
    }

    private void browseMapFile(ActionEvent ignored) {
        JFileChooser chooser = new JFileChooser(Paths.get("").toAbsolutePath().toFile());
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            String selected = chooser.getSelectedFile().toPath().toString();
            mapSourceField.setText(selected);
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
                String year = Objects.toString(overridesModel.getValueAt(i, 0), "").trim();
                String field = Objects.toString(overridesModel.getValueAt(i, 1), "").trim();
                String value = Objects.toString(overridesModel.getValueAt(i, 2), "").trim();
                if (!field.isEmpty() && !value.isEmpty()) {
                    String p = "override." + i + ".";
                    props.setProperty(p + "year", year);
                    props.setProperty(p + "field", field);
                    props.setProperty(p + "value", value);
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

            String loadedSchedulePath = scheduleField.getText().trim();
            if (!loadedSchedulePath.isEmpty()) {
                Path resolvedSchedule = resolvePath(loadedSchedulePath);
                if (Files.exists(resolvedSchedule)) {
                    try {
                        ParameterSchedule loadedSchedule = ParameterSchedule.load(resolvedSchedule);
                        appendLog("Profile schedule loaded: " + resolvedSchedule.toAbsolutePath()
                                + " (entries=" + loadedSchedule.entries().size() + ")");
                    } catch (Exception scheduleEx) {
                        appendLog("Profile schedule path set, but failed to parse schedule file: "
                                + resolvedSchedule.toAbsolutePath() + " (" + scheduleEx.getMessage() + ")");
                    }
                } else {
                    appendLog("Profile schedule path set, but file was not found: "
                            + resolvedSchedule.toAbsolutePath()
                            + ". The run will fallback to stationary unless path is fixed.");
                }
            }

            overridesModel.setRowCount(0);
            Map<Integer, String> idxYear = new TreeMap<>();
            Map<Integer, String> idxField = new TreeMap<>();
            Map<Integer, String> idxValue = new TreeMap<>();
            for (String name : props.stringPropertyNames()) {
                if (name.startsWith("override.")) {
                    String rest = name.substring("override.".length());
                    String[] parts = rest.split("\\.", 2);
                    if (parts.length == 2 && parts[0].matches("\\d+")) {
                        int idx = Integer.parseInt(parts[0]);
                        switch (parts[1]) {
                            case "year" -> idxYear.put(idx, props.getProperty(name, ""));
                            case "field" -> idxField.put(idx, props.getProperty(name, ""));
                            case "value" -> idxValue.put(idx, props.getProperty(name, ""));
                            default -> {
                            }
                        }
                    } else {
                        // Backward compatibility: old format override.<FIELD>=<VALUE> means static override.
                        overridesModel.addRow(new Object[]{"", rest, props.getProperty(name)});
                    }
                }
            }
            for (Integer idx : idxField.keySet()) {
                String field = idxField.get(idx);
                String value = idxValue.getOrDefault(idx, "");
                if (field != null && !field.isBlank() && !value.isBlank()) {
                    overridesModel.addRow(new Object[]{idxYear.getOrDefault(idx, ""), field, value});
                }
            }

            appendLog("Loaded scenario profile: " + chooser.getSelectedFile().toPath());
        } catch (Exception ex) {
            showError("Failed to load profile: " + ex.getMessage());
        }
    }

    private void runScenario() {
        if (currentRunWorker != null && !currentRunWorker.isDone()) {
            showError("A scenario is already running. Use Force stop first.");
            return;
        }

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
        final String preparedMapSource;

        try {
            preparedMapSource = prepareMapSourceForRun(mapSource, mapLength);
            if (preparedMapSource == null) {
                appendLog("Run canceled.");
                return;
            }
        } catch (Exception ex) {
            showError("Failed to prepare map: " + ex.getMessage());
            return;
        }

        final Map<String, String> overrides = new LinkedHashMap<>();
        final List<ParameterSchedule.Entry> uiScheduleEntries = new ArrayList<>();
        for (int i = 0; i < overridesModel.getRowCount(); i++) {
            String yearRaw = Objects.toString(overridesModel.getValueAt(i, 0), "").trim();
            String key = Objects.toString(overridesModel.getValueAt(i, 1), "").trim();
            String value = Objects.toString(overridesModel.getValueAt(i, 2), "").trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            if (yearRaw.isEmpty()) {
                overrides.put(key, value);
            } else {
                try {
                    double year = Double.parseDouble(yearRaw);
                    uiScheduleEntries.add(new ParameterSchedule.Entry(year, key, value));
                } catch (NumberFormatException nfe) {
                    showError("Invalid year in overrides table: '" + yearRaw + "' at row " + (i + 1));
                    return;
                }
            }
        }

        appendLog("Starting scenario run: " + scenarioId + " (replicates=" + replicates + ", years=" + years + ")");
        Settings.STOP_REQUESTED = false;
        progressTracker = new RunProgressTracker(replicates);
        progressTracker.setState("Preparing");
        refreshProgressUi();
        runScenarioButton.setEnabled(false);
        stopScenarioButton.setEnabled(true);

        SwingWorker<BatchRunner.BatchOutcome, String> worker = new SwingWorker<>() {
            @Override
            protected BatchRunner.BatchOutcome doInBackground() throws Exception {
                long maxTicks = (long) Math.ceil(years / Settings.ONE_TICK_IN_YEARS);
                NavigableMap<Integer, Double> reference = loadReferenceSeries(referencePath);
                ParameterSchedule schedule = loadSchedule(schedulePath);
                if (!uiScheduleEntries.isEmpty()) {
                    List<ParameterSchedule.Entry> merged = new ArrayList<>(schedule.entries());
                    merged.addAll(uiScheduleEntries);
                    String src = schedule.sourceDescription();
                    if (src == null || src.isBlank()) {
                        src = "ui-overrides";
                    } else {
                        src = src + ";ui-overrides";
                    }
                    schedule = new ParameterSchedule(merged, src);
                }

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
                        preparedMapSource
                );

                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                try (PrintStream tee = new PrintStream(
                        new TeeOutputStream(originalOut, new SwingTextAreaStream(logArea, BatchRunnerUI.this::handleRunnerLogLine)),
                        true,
                        StandardCharsets.UTF_8)) {
                    System.setOut(tee);
                    System.setErr(tee);
                    return BatchRunner.runScenarios(List.of(scenario), resolvePath(outputDir));
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                }
            }

            @Override
            protected void done() {
                try {
                    BatchRunner.BatchOutcome outcome = get();
                    if (progressTracker != null) {
                        progressTracker.setState(Settings.STOP_REQUESTED ? "Stopped" : "Completed");
                    }
                    refreshProgressUi();
                    updateCharts(outcome);
                    if (Settings.STOP_REQUESTED) {
                        appendLog("Run stopped by user.");
                    } else {
                        appendLog("Run finished successfully.");
                    }
                } catch (Exception ex) {
                    if (progressTracker != null) {
                        progressTracker.setState(Settings.STOP_REQUESTED ? "Stopped" : "Failed");
                    }
                    refreshProgressUi();
                    initializeChartSelector();
                    if (Settings.STOP_REQUESTED) {
                        appendLog("Run stopped by user.");
                    } else {
                        appendLog("Run failed: " + ex.getMessage());
                        showError("Run failed: " + ex.getMessage());
                    }
                } finally {
                    currentRunWorker = null;
                    runScenarioButton.setEnabled(true);
                    stopScenarioButton.setEnabled(false);
                    Settings.STOP_REQUESTED = false;
                }
            }
        };

        currentRunWorker = worker;
        worker.execute();
    }

    private String prepareMapSourceForRun(String rawMapSource, int targetMapLength) throws IOException {
        if (rawMapSource == null || rawMapSource.isBlank()) {
            return rawMapSource;
        }
        if (targetMapLength <= 0) {
            throw new IllegalArgumentException("Map length must be positive.");
        }

        Path sourcePath = resolvePath(rawMapSource);
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source map not found: " + sourcePath);
        }

        boolean rasterSource = isRasterMapSource(sourcePath);
        if (!rasterSource) {
            MapGridLoader.Grid sourceGrid = MapGridLoader.load(sourcePath);
            int sourceLength = sourceGrid.mapLength();
            if (sourceLength == targetMapLength) {
                return sourcePath.toString();
            }
        }

        Path generatedPath = deriveGeneratedMapPath(sourcePath, targetMapLength);
        boolean regenerate = true;
        if (Files.exists(generatedPath)) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                        "Generated simulation map already exists:\n" + generatedPath.toAbsolutePath()
                            + "\n\nRegenerate it from source raster map?",
                        "Generated simulation map exists",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) {
                return null;
            }
            regenerate = choice == JOptionPane.YES_OPTION;
        }

        if (regenerate) {
            if (rasterSource) {
                runBuildRomaniaMapScript(sourcePath, targetMapLength, generatedPath);
                appendLog("Generated simulation map with build_romania_map.py: " + generatedPath.toAbsolutePath());
            } else {
                MapGridLoader.Grid sourceGrid = MapGridLoader.load(sourcePath);
                int sourceLength = sourceGrid.mapLength();
                BearCellType[][] resizedCells = resizeGrid(sourceGrid.cells, sourceGrid.width, sourceGrid.height, targetMapLength);
                writeGeneratedMap(generatedPath, resizedCells, targetMapLength, sourcePath, sourceLength);
                appendLog("Generated simulation map: " + generatedPath.toAbsolutePath()
                        + " (" + sourceLength + " -> " + targetMapLength + ")");
            }
        } else {
            appendLog("Using existing generated simulation map: " + generatedPath.toAbsolutePath());
        }

        String generatedPathText = generatedPath.toString();
        previewMapPathField.setText(generatedPathText);
        return generatedPathText;
    }

    private static boolean isRasterMapSource(Path sourcePath) {
        String name = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".tif") || name.endsWith(".tiff");
    }

    private void runBuildRomaniaMapScript(Path clcPath, int gridSize, Path outPath) throws IOException {
        Path scriptPath = resolvePath("tools/build_romania_map.py");
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("Map build script not found: " + scriptPath);
        }

        List<String> baseArgs = new ArrayList<>();
        baseArgs.add(scriptPath.toString());
        baseArgs.add("--clc");
        baseArgs.add(clcPath.toString());
        Path defaultMask = resolvePath(DEFAULT_MASK_PATH.toString());
        if (Files.exists(defaultMask)) {
            baseArgs.add("--mask");
            baseArgs.add(defaultMask.toString());
        }
        baseArgs.add("--grid-size");
        baseArgs.add(String.valueOf(gridSize));
        baseArgs.add("--out");
        baseArgs.add(outPath.toString());

        IOException lastIo = null;
        RuntimeException lastRuntime = null;
        for (String interpreter : List.of("python", "py")) {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(interpreter);
                cmd.addAll(baseArgs);
                appendLog("Generating map via " + interpreter + " " + scriptPath + " ...");
                runExternalProcess(cmd);
                return;
            } catch (IOException io) {
                lastIo = io;
            } catch (RuntimeException rt) {
                lastRuntime = rt;
            }
        }

        if (lastRuntime != null) {
            throw new IOException(lastRuntime.getMessage(), lastRuntime);
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new IOException("Failed to start map generation process.");
    }

    private static void runExternalProcess(List<String> command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Paths.get("").toAbsolutePath().toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Map generation process was interrupted.", ie);
        }

        if (exitCode != 0) {
            String text = output.toString().trim();
            if (text.length() > 1200) {
                text = text.substring(text.length() - 1200);
            }
            throw new RuntimeException("Map generation failed (exit " + exitCode + "):\n" + text);
        }
    }

    private static Path deriveGeneratedMapPath(Path sourcePath, int targetMapLength) {
        String sourceName = sourcePath.getFileName() == null ? "map" : sourcePath.getFileName().toString();
        int dot = sourceName.lastIndexOf('.');
        String stem = dot > 0 ? sourceName.substring(0, dot) : sourceName;
        String sanitizedStem = stem.replaceAll("[^A-Za-z0-9._-]", "_");
        String generatedName = sanitizedStem + "-" + targetMapLength + "x" + targetMapLength + ".txt";
        return Paths.get("reference-data", "generated-maps", generatedName);
    }

    private static BearCellType[][] resizeGrid(BearCellType[][] sourceCells, int sourceWidth, int sourceHeight, int targetLength) {
        BearCellType[][] resized = new BearCellType[targetLength][targetLength];
        BearCellType[] tieBreakPriority = new BearCellType[]{
                BearCellType.FOREST,
                BearCellType.FIELD,
                BearCellType.MOUNTAIN,
                BearCellType.VILLAGE,
                BearCellType.ROAD,
                BearCellType.NONE
        };

        for (int ty = 0; ty < targetLength; ty++) {
            int yStart = (int) Math.floor((double) ty * sourceHeight / targetLength);
            int yEnd = (int) Math.floor((double) (ty + 1) * sourceHeight / targetLength) - 1;
            if (yEnd < yStart) {
                yEnd = yStart;
            }
            yStart = Math.max(0, Math.min(sourceHeight - 1, yStart));
            yEnd = Math.max(0, Math.min(sourceHeight - 1, yEnd));

            for (int tx = 0; tx < targetLength; tx++) {
                int xStart = (int) Math.floor((double) tx * sourceWidth / targetLength);
                int xEnd = (int) Math.floor((double) (tx + 1) * sourceWidth / targetLength) - 1;
                if (xEnd < xStart) {
                    xEnd = xStart;
                }
                xStart = Math.max(0, Math.min(sourceWidth - 1, xStart));
                xEnd = Math.max(0, Math.min(sourceWidth - 1, xEnd));

                EnumMap<BearCellType, Integer> counts = new EnumMap<>(BearCellType.class);
                for (int y = yStart; y <= yEnd; y++) {
                    for (int x = xStart; x <= xEnd; x++) {
                        BearCellType cell = sourceCells[x][y];
                        counts.merge(cell, 1, Integer::sum);
                    }
                }

                BearCellType chosen = BearCellType.NONE;
                int bestCount = -1;
                for (BearCellType candidate : tieBreakPriority) {
                    int count = counts.getOrDefault(candidate, 0);
                    if (count > bestCount) {
                        bestCount = count;
                        chosen = candidate;
                    }
                }
                resized[tx][ty] = chosen;
            }
        }
        return resized;
    }

    private static void writeGeneratedMap(Path path,
                                          BearCellType[][] cells,
                                          int mapLength,
                                          Path sourcePath,
                                          int sourceLength) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>(mapLength + 8);
        lines.add("# Auto-generated simulation map.");
        lines.add("# generatedBy=BatchRunnerUI");
        lines.add("# sourceMap=" + sourcePath.toAbsolutePath());
        lines.add("# sourceSize=" + sourceLength);
        lines.add("# targetSize=" + mapLength);
        lines.add("width=" + mapLength);
        lines.add("height=" + mapLength);

        for (int y = 0; y < mapLength; y++) {
            StringBuilder row = new StringBuilder(mapLength);
            for (int x = 0; x < mapLength; x++) {
                BearCellType cell = cells[x][y];
                char c = cell == BearCellType.NONE ? 'N' : cell.mapSymbol();
                row.append(c);
            }
            lines.add(row.toString());
        }

        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private void forceStopCurrentScenario() {
        if (currentRunWorker == null || currentRunWorker.isDone()) {
            appendLog("No active scenario run to stop.");
            return;
        }
        Settings.STOP_REQUESTED = true;
        if (progressTracker != null) {
            progressTracker.setState("Stopping (force)");
        }
        refreshProgressUi();
        currentRunWorker.cancel(true);
        appendLog("Force stop requested. Waiting for current simulation loop to exit...");
    }

    private void updateCharts(BatchRunner.BatchOutcome outcome) {
        lastChartOutcome = outcome;
        if (outcome == null || outcome.runResults.isEmpty()) {
            initializeChartSelector();
            return;
        }

        ChartTimeStep timeStep = getSelectedChartTimeStep();

        // Aggregate per tick across replicates: each metric stores mean value per sampled tick.
        Map<Long, double[]> byTickPopulation = new TreeMap<>(); // [sum, count, year]
        Map<Long, double[]> byTickBirths = new TreeMap<>(); // [sum, count, year]
        Map<Long, double[]> byTickDeathsTotal = new TreeMap<>(); // [sum, count, year]
        Map<DeathCause, Map<Long, double[]>> byTickDeathsByCause = new LinkedHashMap<>();
        for (DeathCause cause : DeathCause.values()) {
            byTickDeathsByCause.put(cause, new TreeMap<>());
        }

        for (BatchRunner.RunResult rr : outcome.runResults) {
            for (TickMetrics s : rr.recorder.samples()) {
                aggregateTickValue(byTickPopulation, s.tick, s.simulationYear, s.population);
                aggregateTickValue(byTickBirths, s.tick, s.simulationYear, s.births);
                aggregateTickValue(byTickDeathsTotal, s.tick, s.simulationYear, s.deathsTotal);

                for (DeathCause cause : DeathCause.values()) {
                    int value = s.deathsByCause.getOrDefault(cause, 0);
                    aggregateTickValue(byTickDeathsByCause.get(cause), s.tick, s.simulationYear, value);
                }
            }
        }

        int replicateCount = outcome.runResults.size();
        String replicateSuffix = replicateCount == 1 ? "" : "s";
        String perLabel = timeStep.perLabel;

        chartSeriesByKey.clear();
        chartSeriesByKey.put("Population", buildSeriesData(
                "Population over time",
                byTickPopulation,
                String.format(Locale.ROOT, "Mean population (%d replicate%s, %s)", replicateCount, replicateSuffix, perLabel),
            ChartAggregation.MEAN,
                timeStep
        ));
        chartSeriesByKey.put("Births", buildSeriesData(
                "Births over time",
                byTickBirths,
            String.format(Locale.ROOT, "Total births per %s (%d replicate%s)",
                perLabel, replicateCount, replicateSuffix),
            ChartAggregation.SUM,
                timeStep
        ));
        chartSeriesByKey.put("Deaths (total)", buildSeriesData(
                "Total deaths over time",
                byTickDeathsTotal,
            String.format(Locale.ROOT, "Total deaths per %s (%d replicate%s)",
                perLabel, replicateCount, replicateSuffix),
            ChartAggregation.SUM,
                timeStep
        ));
        for (DeathCause cause : DeathCause.values()) {
            String key = "Deaths (" + formatDeathCauseLabel(cause) + ")";
            chartSeriesByKey.put(key, buildSeriesData(
                    key + " over time",
                    byTickDeathsByCause.get(cause),
                String.format(Locale.ROOT, "Total deaths per %s (%s, %d replicate%s)",
                    perLabel, formatDeathCauseLabel(cause), replicateCount, replicateSuffix),
                ChartAggregation.SUM,
                    timeStep
            ));
        }

        String selected = (String) chartSelectorCombo.getSelectedItem();
        chartSelectorCombo.removeAllItems();
        for (String key : chartSeriesByKey.keySet()) {
            chartSelectorCombo.addItem(key);
        }
        if (selected != null && chartSeriesByKey.containsKey(selected)) {
            chartSelectorCombo.setSelectedItem(selected);
        } else if (chartSelectorCombo.getItemCount() > 0) {
            chartSelectorCombo.setSelectedIndex(0);
        }
        refreshSelectedChart();
    }

    private static void aggregateTickValue(Map<Long, double[]> byTick, long tick, double simulationYear, double value) {
        double[] agg = byTick.computeIfAbsent(tick, k -> new double[]{0.0, 0.0, simulationYear});
        agg[0] += value;
        agg[1] += 1.0;
        agg[2] = simulationYear;
    }

    private static ChartSeriesData buildSeriesData(String title,
                                                   Map<Long, double[]> byTick,
                                                   String subtitle,
                                                   ChartAggregation aggregation,
                                                   ChartTimeStep timeStep) {
        Map<Long, double[]> rebinned = rebinByTimeStep(byTick, timeStep);
        List<Double> xValues = new ArrayList<>(rebinned.size());
        List<Double> yValues = new ArrayList<>(rebinned.size());
        for (double[] agg : rebinned.values()) {
            xValues.add(agg[2]);
            double v;
            if (aggregation == ChartAggregation.SUM) {
                v = agg[0];
            } else {
                v = agg[1] > 0 ? (agg[0] / agg[1]) : 0.0;
            }
            yValues.add(v);
        }
        return new ChartSeriesData(title, xValues, yValues, subtitle, timeStep.axisSuffix, timeStep.tooltipLabel);
    }

    private static Map<Long, double[]> rebinByTimeStep(Map<Long, double[]> byTick, ChartTimeStep timeStep) {
        Map<Long, double[]> byBin = new TreeMap<>();
        for (Map.Entry<Long, double[]> e : byTick.entrySet()) {
            long tick = e.getKey();
            double[] agg = e.getValue();
            double value = agg[1] > 0 ? (agg[0] / agg[1]) : 0.0;
            long bin = timeStep.toBin(tick, agg[2]);
            double[] binAgg = byBin.computeIfAbsent(bin, k -> new double[]{0.0, 0.0, timeStep.toXValue(k)});
            binAgg[0] += value;
            binAgg[1] += 1.0;
            binAgg[2] = timeStep.toXValue(bin);
        }
        return byBin;
    }

    private enum ChartAggregation {
        MEAN,
        SUM
    }

    private void handleRunnerLogLine(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || progressTracker == null) {
            return;
        }

        if (line.startsWith("===== Scenario:")) {
            progressTracker.setState("Preparing");
            refreshProgressUi();
            return;
        }

        if (line.startsWith("--- Run:")) {
            Matcher m = RUN_REPLICATE_PATTERN.matcher(line);
            if (m.find()) {
                int replicateIndex = Integer.parseInt(m.group(1));
                progressTracker.onReplicateStarted(replicateIndex);
                refreshProgressUi();
            }
            return;
        }

        if (line.startsWith("mae=") || line.contains(" mae=")) {
            progressTracker.onReplicateCompleted();
            refreshProgressUi();
            return;
        }

        if (line.startsWith("Batch written to:")) {
            progressTracker.setState("Completed");
            refreshProgressUi();
        }
    }

    private void refreshProgressUi() {
        RunProgressTracker tracker = progressTracker;
        if (tracker == null) {
            return;
        }
        RunProgressSnapshot snapshot = tracker.snapshot();
        SwingUtilities.invokeLater(() -> {
            runProgressBar.setMaximum(Math.max(1, snapshot.totalReplicates));
            runProgressBar.setValue(Math.min(snapshot.completedReplicates, snapshot.totalReplicates));
            runProgressBar.setString(snapshot.completedReplicates + " / " + snapshot.totalReplicates + " replicates");
            progressStateValueLabel.setText(snapshot.state);
            progressReplicateValueLabel.setText(snapshot.replicateLabel);
            progressEtaValueLabel.setText(snapshot.etaLabel);
            progressElapsedValueLabel.setText(snapshot.elapsedLabel);
        });
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000L);
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    private static final class ChartSeriesData {
        final String title;
        final List<Double> xValues;
        final List<Double> yValues;
        final String subtitle;
        final String xAxisSuffix;
        final String xTooltipLabel;

        ChartSeriesData(String title,
                        List<Double> xValues,
                        List<Double> yValues,
                        String subtitle,
                        String xAxisSuffix,
                        String xTooltipLabel) {
            this.title = title;
            this.xValues = xValues;
            this.yValues = yValues;
            this.subtitle = subtitle;
            this.xAxisSuffix = xAxisSuffix;
            this.xTooltipLabel = xTooltipLabel;
        }
    }

    private enum ChartTimeStep {
        TICK("Tick", "tick", "t", "Tick") {
            @Override
            long toBin(long tick, double simulationYear) {
                return tick;
            }
        },
        HOUR("Hour", "hour", "h", "Hour") {
            @Override
            long toBin(long tick, double simulationYear) {
                return (long) Math.floor(simulationYear * 365.0 * 24.0);
            }
        },
        DAY("Day", "day", "d", "Day") {
            @Override
            long toBin(long tick, double simulationYear) {
                return (long) Math.floor(simulationYear * 365.0);
            }
        },
        MONTH("Month", "month", "mo", "Month") {
            @Override
            long toBin(long tick, double simulationYear) {
                return (long) Math.floor(simulationYear * 12.0);
            }
        },
        YEAR("Year", "year", "y", "Year") {
            @Override
            long toBin(long tick, double simulationYear) {
                return (long) Math.floor(simulationYear);
            }
        };

        final String label;
        final String perLabel;
        final String axisSuffix;
        final String tooltipLabel;

        ChartTimeStep(String label, String perLabel, String axisSuffix, String tooltipLabel) {
            this.label = label;
            this.perLabel = perLabel;
            this.axisSuffix = axisSuffix;
            this.tooltipLabel = tooltipLabel;
        }

        abstract long toBin(long tick, double simulationYear);

        double toXValue(long bin) {
            return (double) bin;
        }

        @Override
        public String toString() {
            return label;
        }
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
                    g2.drawString("Load a generated simulation map to preview its terrain layout.", 20, 30);
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

    private static final class PopulationChartPanel extends JPanel {
        private List<Double> xValues = List.of();
        private List<Double> yPopulation = List.of();
        private String subtitle = "Run a scenario to render the chart.";
        private String xAxisSuffix = "y";
        private String xTooltipLabel = "Year";
        private int hoverIndex = -1;
        private boolean showSeasons = true;
        private boolean showYearBoundaries = true;
        private ChartTimeStep currentTimeStep = ChartTimeStep.YEAR;

        PopulationChartPanel() {
            setToolTipText(" ");
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = findNearestIndex(e.getX(), e.getY());
                    if (idx != hoverIndex) {
                        hoverIndex = idx;
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    if (hoverIndex != -1) {
                        hoverIndex = -1;
                        repaint();
                    }
                }
            });
        }

        void setSeries(List<Double> xValues, List<Double> yPopulation, String subtitle, String xAxisSuffix, String xTooltipLabel) {
            this.xValues = xValues == null ? List.of() : xValues;
            this.yPopulation = yPopulation == null ? List.of() : yPopulation;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.xAxisSuffix = (xAxisSuffix == null || xAxisSuffix.isBlank()) ? "" : xAxisSuffix;
            this.xTooltipLabel = (xTooltipLabel == null || xTooltipLabel.isBlank()) ? "Time" : xTooltipLabel;
            this.hoverIndex = -1;
            repaint();
        }

        void setShowSeasons(boolean show) {
            this.showSeasons = show;
            repaint();
        }

        void setShowYearBoundaries(boolean show) {
            this.showYearBoundaries = show;
            repaint();
        }

        void setChartTimeStep(ChartTimeStep timeStep) {
            this.currentTimeStep = timeStep != null ? timeStep : ChartTimeStep.YEAR;
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int idx = findNearestIndex(event.getX(), event.getY());
            if (idx < 0 || idx >= xValues.size() || idx >= yPopulation.size()) {
                return null;
            }
            return String.format(Locale.ROOT, "%s: %s, Value: %s",
                    xTooltipLabel,
                    formatValue(xValues.get(idx), 2),
                    formatValue(yPopulation.get(idx), 3));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(248, 248, 248));
                g2.fillRect(0, 0, getWidth(), getHeight());

                ChartGeometry cg = buildGeometry();
                Insets in = getInsets();

                g2.setColor(Color.DARK_GRAY);
                g2.drawString(subtitle, cg.left, in.top + 14);

                g2.setColor(new Color(220, 220, 220));
                g2.drawRect(cg.left, cg.top, cg.w, cg.h);

                drawSeasonBands(g2, cg);
                drawYearBoundaries(g2, cg);

                if (xValues.isEmpty() || yPopulation.isEmpty() || xValues.size() != yPopulation.size()) {
                    g2.setColor(Color.GRAY);
                    g2.drawString("No run data yet.", cg.left + 8, cg.top + 18);
                    return;
                }

                g2.setColor(new Color(120, 120, 120));
                g2.drawString(formatAxisValue(cg.maxY, cg.maxY - cg.minY), cg.left - 40, cg.top + 4);
                g2.drawString(formatAxisValue(cg.minY, cg.maxY - cg.minY), cg.left - 40, cg.bottom + 4);
                String minXText = formatValue(cg.minX, 1) + xAxisSuffix;
                String maxXText = formatValue(cg.maxX, 1) + xAxisSuffix;
                g2.drawString(minXText, cg.left, cg.bottom + 18);
                g2.drawString(maxXText, cg.right - g2.getFontMetrics().stringWidth(maxXText), cg.bottom + 18);

                g2.setColor(new Color(50, 120, 220));
                g2.setStroke(new BasicStroke(2f));

                int prevX = -1;
                int prevY = -1;
                for (int i = 0; i < xValues.size(); i++) {
                    int px = toPixelX(cg, xValues.get(i));
                    int py = toPixelY(cg, yPopulation.get(i));
                    if (prevX >= 0) {
                        g2.drawLine(prevX, prevY, px, py);
                    }
                    prevX = px;
                    prevY = py;
                }

                if (hoverIndex >= 0 && hoverIndex < xValues.size() && hoverIndex < yPopulation.size()) {
                    int hx = toPixelX(cg, xValues.get(hoverIndex));
                    int hy = toPixelY(cg, yPopulation.get(hoverIndex));
                    g2.setColor(new Color(30, 30, 30, 90));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(hx, cg.top, hx, cg.bottom);
                    g2.drawLine(cg.left, hy, cg.right, hy);
                    g2.setColor(new Color(220, 70, 50));
                    g2.fillOval(hx - 4, hy - 4, 8, 8);
                }
            } finally {
                g2.dispose();
            }
        }

        private int findNearestIndex(int mouseX, int mouseY) {
            if (xValues.isEmpty() || yPopulation.isEmpty() || xValues.size() != yPopulation.size()) {
                return -1;
            }
            ChartGeometry cg = buildGeometry();
            if (mouseX < cg.left || mouseX > cg.right || mouseY < cg.top || mouseY > cg.bottom) {
                return -1;
            }
            int bestIdx = -1;
            int bestDx = Integer.MAX_VALUE;
            for (int i = 0; i < xValues.size(); i++) {
                int px = toPixelX(cg, xValues.get(i));
                int dx = Math.abs(mouseX - px);
                if (dx < bestDx) {
                    bestDx = dx;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        private int toPixelX(ChartGeometry cg, double year) {
            double xNorm = (year - cg.minX) / (cg.maxX - cg.minX);
            return cg.left + (int) Math.round(xNorm * cg.w);
        }

        private int toPixelY(ChartGeometry cg, double population) {
            double yNorm = (population - cg.minY) / (cg.maxY - cg.minY);
            return cg.bottom - (int) Math.round(yNorm * cg.h);
        }

        private ChartGeometry buildGeometry() {
            Insets in = getInsets();
            int left = in.left + 46;
            int top = in.top + 24;
            int right = getWidth() - in.right - 16;
            int bottom = getHeight() - in.bottom - 34;
            int w = Math.max(1, right - left);
            int h = Math.max(1, bottom - top);

            double minX = 0.0;
            double maxX = 1.0;
            double minY = 0.0;
            double maxY = 1.0;
            if (!xValues.isEmpty() && !yPopulation.isEmpty() && xValues.size() == yPopulation.size()) {
                minX = xValues.get(0);
                maxX = xValues.get(xValues.size() - 1);
                minY = Double.POSITIVE_INFINITY;
                maxY = Double.NEGATIVE_INFINITY;
                for (double y : yPopulation) {
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
                if (maxX <= minX) maxX = minX + 1.0;
                if (maxY <= minY) maxY = minY + 1.0;
            }

            return new ChartGeometry(left, top, right, bottom, w, h, minX, maxX, minY, maxY);
        }

        private double convertXToYears(double xValue) {
            if (currentTimeStep == null || xValues.isEmpty()) {
                return xValue;
            }
            switch (currentTimeStep) {
                case TICK:
                    // Ticks per year is stored in simulation; assume 365 ticks/year for calendar alignment
                    return xValue / 365.0;
                case HOUR:
                    // 365.25 days * 24 hours per year
                    return xValue / (365.25 * 24.0);
                case DAY:
                    // 365.25 days per year
                    return xValue / 365.25;
                case MONTH:
                    // 12 months per year
                    return xValue / 12.0;
                case YEAR:
                default:
                    return xValue;
            }
        }

        private void drawSeasonBands(Graphics2D g2, ChartGeometry cg) {
            if (!showSeasons || xValues.isEmpty()) {
                return;
            }
            double minXOriginal = cg.minX;
            double maxXOriginal = cg.maxX;
            if (maxXOriginal <= minXOriginal) return;

            // Convert chart bounds from current time step to years
            double minXYears = convertXToYears(minXOriginal);
            double maxXYears = convertXToYears(maxXOriginal);

            Color[] seasonColors = {
                new Color(100, 200, 100, 40),  // Spring - light green
                new Color(255, 200, 100, 40),  // Summer - light orange
                new Color(200, 100, 50, 40),   // Fall - light brown
                new Color(100, 150, 200, 40)   // Winter - light blue
            };

            int startYear = (int) Math.floor(minXYears);
            int endYear = (int) Math.ceil(maxXYears);
            for (int year = startYear; year <= endYear; year++) {
                for (int season = 0; season < 4; season++) {
                    double seasonStartYears = year + (season * 0.25);
                    double seasonEndYears = year + ((season + 1) * 0.25);
                    if (seasonEndYears < minXYears || seasonStartYears > maxXYears) continue;

                    seasonStartYears = Math.max(seasonStartYears, minXYears);
                    seasonEndYears = Math.min(seasonEndYears, maxXYears);

                    // Convert back to original time step for pixel calculation
                    double seasonStartOriginal = convertYearsToX(seasonStartYears);
                    double seasonEndOriginal = convertYearsToX(seasonEndYears);

                    int px1 = toPixelX(cg, seasonStartOriginal);
                    int px2 = toPixelX(cg, seasonEndOriginal);
                    g2.setColor(seasonColors[season]);
                    g2.fillRect(px1, cg.top, Math.max(1, px2 - px1), cg.h);
                }
            }
        }

        private double convertYearsToX(double yearValue) {
            if (currentTimeStep == null) {
                return yearValue;
            }
            switch (currentTimeStep) {
                case TICK:
                    return yearValue * 365.0;
                case HOUR:
                    return yearValue * (365.25 * 24.0);
                case DAY:
                    return yearValue * 365.25;
                case MONTH:
                    return yearValue * 12.0;
                case YEAR:
                default:
                    return yearValue;
            }
        }

        private void drawYearBoundaries(Graphics2D g2, ChartGeometry cg) {
            if (!showYearBoundaries || xValues.isEmpty()) {
                return;
            }
            double minXOriginal = cg.minX;
            double maxXOriginal = cg.maxX;
            if (maxXOriginal <= minXOriginal) return;

            // Convert chart bounds from current time step to years
            double minXYears = convertXToYears(minXOriginal);
            double maxXYears = convertXToYears(maxXOriginal);

            int startYear = (int) Math.floor(minXYears);
            int endYear = (int) Math.ceil(maxXYears);
            g2.setColor(new Color(100, 100, 100, 150));
            g2.setStroke(new BasicStroke(1.5f));

            for (int year = startYear; year <= endYear; year++) {
                if (year < minXYears || year > maxXYears) continue;
                double yearInOriginal = convertYearsToX(year);
                int px = toPixelX(cg, yearInOriginal);
                g2.drawLine(px, cg.top, px, cg.bottom);
            }
        }

        private static String formatAxisValue(double value, double range) {
            if (range < 2.0) return formatValue(value, 2);
            if (range < 20.0) return formatValue(value, 1);
            return formatValue(value, 0);
        }

        private static String formatValue(double value, int decimals) {
            return String.format(Locale.ROOT, "%1$." + decimals + "f", value);
        }

        private static final class ChartGeometry {
            final int left;
            final int top;
            final int right;
            final int bottom;
            final int w;
            final int h;
            final double minX;
            final double maxX;
            final double minY;
            final double maxY;

            ChartGeometry(int left, int top, int right, int bottom, int w, int h,
                          double minX, double maxX, double minY, double maxY) {
                this.left = left;
                this.top = top;
                this.right = right;
                this.bottom = bottom;
                this.w = w;
                this.h = h;
                this.minX = minX;
                this.maxX = maxX;
                this.minY = minY;
                this.maxY = maxY;
            }
        }
    }

    private static final class SwingTextAreaStream extends OutputStream {
        private final JTextArea area;
        private final Consumer<String> onLine;
        private final StringBuilder lineBuffer = new StringBuilder();

        SwingTextAreaStream(JTextArea area) {
            this(area, null);
        }

        SwingTextAreaStream(JTextArea area, Consumer<String> onLine) {
            this.area = area;
            this.onLine = onLine;
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
            if (onLine != null) {
                onLine.accept(text);
            }
            SwingUtilities.invokeLater(() -> {
                area.append(text);
                area.setCaretPosition(area.getDocument().getLength());
            });
        }
    }

    private static final class RunProgressTracker {
        private final int totalReplicates;
        private final long runStartMs;
        private String state = "Idle";
        private int currentReplicateIndex = -1;
        private long currentReplicateStartMs = -1L;
        private final List<Long> completedReplicateDurationsMs = new ArrayList<>();

        RunProgressTracker(int totalReplicates) {
            this.totalReplicates = Math.max(1, totalReplicates);
            this.runStartMs = System.currentTimeMillis();
        }

        synchronized void setState(String state) {
            this.state = state;
        }

        synchronized void onReplicateStarted(int replicateIndex) {
            this.currentReplicateIndex = replicateIndex;
            this.currentReplicateStartMs = System.currentTimeMillis();
            this.state = "Running replicate " + (replicateIndex + 1) + " of " + totalReplicates;
        }

        synchronized void onReplicateCompleted() {
            if (currentReplicateStartMs > 0) {
                completedReplicateDurationsMs.add(System.currentTimeMillis() - currentReplicateStartMs);
                currentReplicateStartMs = -1L;
            }
            this.state = "Writing CSV";
        }

        synchronized RunProgressSnapshot snapshot() {
            long now = System.currentTimeMillis();
            int completed = Math.min(completedReplicateDurationsMs.size(), totalReplicates);
            String replicateLabel;
            if (currentReplicateIndex >= 0 && completed < totalReplicates && state.startsWith("Running replicate")) {
                replicateLabel = (currentReplicateIndex + 1) + " / " + totalReplicates;
            } else {
                replicateLabel = completed + " / " + totalReplicates;
            }

            String elapsedLabel = formatDuration(Math.max(0, now - runStartMs));

            String etaLabel = "N/A";
            if (!completedReplicateDurationsMs.isEmpty()) {
                long sum = 0L;
                for (Long d : completedReplicateDurationsMs) {
                    sum += d;
                }
                long avg = sum / completedReplicateDurationsMs.size();
                int remaining = Math.max(0, totalReplicates - completed);
                long etaMs = avg * (long) remaining;
                LocalDateTime etaTime = LocalDateTime.now().plusSeconds(etaMs / 1000L);
                etaLabel = formatDuration(etaMs) + " (around " + etaTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ")";
            }

            return new RunProgressSnapshot(state, replicateLabel, etaLabel, elapsedLabel, completed, totalReplicates);
        }
    }

    private static final class RunProgressSnapshot {
        final String state;
        final String replicateLabel;
        final String etaLabel;
        final String elapsedLabel;
        final int completedReplicates;
        final int totalReplicates;

        RunProgressSnapshot(String state, String replicateLabel, String etaLabel, String elapsedLabel,
                            int completedReplicates, int totalReplicates) {
            this.state = state;
            this.replicateLabel = replicateLabel;
            this.etaLabel = etaLabel;
            this.elapsedLabel = elapsedLabel;
            this.completedReplicates = completedReplicates;
            this.totalReplicates = totalReplicates;
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
