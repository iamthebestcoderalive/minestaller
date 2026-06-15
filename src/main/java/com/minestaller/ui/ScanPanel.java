package com.minestaller.ui;

import com.minestaller.model.InstanceScanner;
import com.minestaller.model.InstanceScanner.ScanResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ScanPanel extends JPanel {

    private final MainFrame mainFrame;
    private JTextArea scanConsole;
    private JPanel resultsPanel;

    private JLabel detectedVerLabel;
    private JLabel detectedLoaderLabel;
    
    private JComboBox<String> verOverrideCombo;
    private JComboBox<String> loaderOverrideCombo;

    private JList<String> modsList;
    private JList<String> rpList;
    private JList<String> shadersList;

    private ScanResult scanResult;
    private int logLineIndex = 0;
    private Timer printTimer;

    private final String[] commonVersions = {
            "1.21.1", "1.21", "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15",
            "1.14.4", "1.14",
            "1.13.2", "1.13",
            "1.12.2", "1.12",
            "1.11.2", "1.11",
            "1.10.2", "1.10",
            "1.9.4", "1.9",
            "1.8.9", "1.8",
            "1.7.10", "1.7.2",
            "1.6.4", "1.5.2", "1.4.7", "1.2.5"
    };
    private final String[] commonLoaders = {
            "Fabric", "Forge", "NeoForge", "Quilt", "Vanilla"
    };

    public ScanPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setBackground(StyleHelper.COLOR_BG);

        initUI();
    }

    private void initUI() {
        // Step Header (internal to step if needed, but we have main frame header)
        
        // Scan Console
        JPanel consoleContainer = StyleHelper.createGridPanel("Folder Analysis Log");
        scanConsole = new JTextArea(6, 40);
        scanConsole.setBackground(StyleHelper.COLOR_BG_PANEL);
        scanConsole.setForeground(StyleHelper.COLOR_GREEN);
        scanConsole.setFont(StyleHelper.FONT_MONO_BODY);
        scanConsole.setEditable(false);
        
        JScrollPane scrollConsole = new JScrollPane(scanConsole);
        StyleHelper.styleScrollPane(scrollConsole);
        scrollConsole.setPreferredSize(new Dimension(800, 140));
        consoleContainer.add(scrollConsole, BorderLayout.CENTER);
        
        add(consoleContainer, BorderLayout.NORTH);

        // Results Dashboard (collapsible/visible after scan)
        resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBackground(StyleHelper.COLOR_BG);
        resultsPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Profile panel (Version/Loader details and overrides)
        JPanel profilePanel = StyleHelper.createGridPanel("Minecraft Version & Mod Loader");
        profilePanel.setLayout(new GridLayout(2, 2, 10, 10));

        JPanel leftLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftLabelPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        detectedVerLabel = StyleHelper.createLabel("Detected Version: --", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        leftLabelPanel.add(detectedVerLabel);

        JPanel rightLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rightLabelPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        detectedLoaderLabel = StyleHelper.createLabel("Detected Mod Loader: --", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        rightLabelPanel.add(detectedLoaderLabel);

        JPanel leftOverridePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftOverridePanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        leftOverridePanel.add(StyleHelper.createLabel("Change Version to: ", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY));
        verOverrideCombo = new JComboBox<>(commonVersions);
        StyleHelper.styleComboBox(verOverrideCombo);
        leftOverridePanel.add(verOverrideCombo);

        JPanel rightOverridePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rightOverridePanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        rightOverridePanel.add(StyleHelper.createLabel("Change Mod Loader to: ", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY));
        loaderOverrideCombo = new JComboBox<>(commonLoaders);
        StyleHelper.styleComboBox(loaderOverrideCombo);
        rightOverridePanel.add(loaderOverrideCombo);

        profilePanel.add(leftLabelPanel);
        profilePanel.add(rightLabelPanel);
        profilePanel.add(leftOverridePanel);
        profilePanel.add(rightOverridePanel);

        // Files List Panel (Tabs for Mods/RP/Shaders)
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(StyleHelper.FONT_MONO_HEADER);
        tabbedPane.setBackground(StyleHelper.COLOR_BG_PANEL);
        tabbedPane.setForeground(StyleHelper.COLOR_CYAN);

        modsList = createStyledList();
        JScrollPane modsScroll = new JScrollPane(modsList);
        StyleHelper.styleScrollPane(modsScroll);
        tabbedPane.addTab("Mods", modsScroll);

        rpList = createStyledList();
        JScrollPane rpScroll = new JScrollPane(rpList);
        StyleHelper.styleScrollPane(rpScroll);
        tabbedPane.addTab("Resource Packs", rpScroll);

        shadersList = createStyledList();
        JScrollPane shadersScroll = new JScrollPane(shadersList);
        StyleHelper.styleScrollPane(shadersScroll);
        tabbedPane.addTab("Shaders", shadersScroll);

        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.setBackground(StyleHelper.COLOR_BG);
        contentPanel.add(profilePanel, BorderLayout.NORTH);
        contentPanel.add(tabbedPane, BorderLayout.CENTER);

        // Control Panel
        JPanel controls = new JPanel(new BorderLayout());
        controls.setBackground(StyleHelper.COLOR_BG);
        controls.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        
        JButton backButton = StyleHelper.createCyberButton("Go Back", false);
        backButton.addActionListener(e -> mainFrame.goToStep("boot"));
        controls.add(backButton, BorderLayout.WEST);

        JButton proceedButton = StyleHelper.createCyberButton("Continue to Dashboard", true);
        proceedButton.addActionListener(e -> proceedToDashboard());
        controls.add(proceedButton, BorderLayout.EAST);

        resultsPanel.add(contentPanel, BorderLayout.CENTER);
        resultsPanel.add(controls, BorderLayout.SOUTH);

        add(resultsPanel, BorderLayout.CENTER);
        resultsPanel.setVisible(false);
    }

    private JList<String> createStyledList() {
        JList<String> list = new JList<>();
        list.setBackground(StyleHelper.COLOR_BG_PANEL);
        list.setForeground(StyleHelper.COLOR_TEXT);
        list.setFont(StyleHelper.FONT_MONO_BODY);
        list.setSelectionBackground(StyleHelper.COLOR_CYAN);
        list.setSelectionForeground(StyleHelper.COLOR_BG);
        return list;
    }

    public void runDiagnostics(String path) {
        scanConsole.setText("");
        resultsPanel.setVisible(false);
        logLineIndex = 0;

        // Perform the scan on a background thread
        CompletableFuture.supplyAsync(() -> InstanceScanner.scan(path))
                .thenAccept(result -> SwingUtilities.invokeLater(() -> {
                    this.scanResult = result;
                    startConsolePrintTimer();
                }));
    }

    private void startConsolePrintTimer() {
        if (printTimer != null && printTimer.isRunning()) {
            printTimer.stop();
        }

        printTimer = new Timer(50, e -> {
            if (logLineIndex < scanResult.logs.size()) {
                scanConsole.append(scanResult.logs.get(logLineIndex) + "\n");
                scanConsole.setCaretPosition(scanConsole.getDocument().getLength());
                logLineIndex++;
            } else {
                printTimer.stop();
                displayResults();
            }
        });
        printTimer.start();
    }

    private void displayResults() {
        detectedVerLabel.setText("Detected Version: [ " + scanResult.minecraftVersion + " ]");
        detectedLoaderLabel.setText("Detected Mod Loader: [ " + scanResult.loader + " ]");

        // Sync overrides
        selectComboValue(verOverrideCombo, scanResult.minecraftVersion);
        selectComboValue(loaderOverrideCombo, scanResult.loader);

        // Populating directories lists
        fillList(modsList, scanResult.mods, "No local mods detected.");
        fillList(rpList, scanResult.resourcePacks, "No local resource packs detected.");
        fillList(shadersList, scanResult.shaders, "No local shaders detected.");

        resultsPanel.setVisible(true);
        revalidate();
        repaint();
    }

    private void selectComboValue(JComboBox<String> combo, String value) {
        if (value == null) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equalsIgnoreCase(value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        // If not found in common, add it temporarily and select
        combo.addItem(value);
        combo.setSelectedItem(value);
    }

    private void fillList(JList<String> jList, List<String> items, String emptyMsg) {
        DefaultListModel<String> model = new DefaultListModel<>();
        if (items.isEmpty()) {
            model.addElement(" > " + emptyMsg);
        } else {
            for (String item : items) {
                model.addElement(" > " + item);
            }
        }
        jList.setModel(model);
    }

    private void proceedToDashboard() {
        String finalVer = (String) verOverrideCombo.getSelectedItem();
        String finalLoader = (String) loaderOverrideCombo.getSelectedItem();

        // Update the main configurations
        mainFrame.setTargetProfile(finalVer, finalLoader);
        mainFrame.updateSearchPanelProfile(finalVer, finalLoader);
        mainFrame.goToStep("dashboard");
    }
}
