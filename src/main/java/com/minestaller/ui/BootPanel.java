package com.minestaller.ui;

import com.minestaller.model.ConfigManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BootPanel extends JPanel {

    private final MainFrame mainFrame;
    private final ConfigManager config;

    private JTextArea terminalArea;
    private JPanel pathPanel;
    private JComboBox<String> instanceCombo;
    private JTextField pathField;
    private JButton browseButton;
    private JButton connectButton;
    private JButton skipButton;

    private final Map<String, String> instancePaths = new HashMap<>();

    private final String[] bootLines = {
            "MINESTALLER(TM) SECURE LINK SYSTEM v2.10",
            "Initializing handshake protocol...",
            "Loading local system variables...",
            "Bypassing sandbox constraints... OK",
            "Resolving virtual machine configurations... OK",
            "Scanning local environment nodes...",
            "WAITING FOR DIRECTORY LINK UPLINK..."
    };

    private int currentLineIndex = 0;
    private Timer bootTimer;
    private boolean bootCompleted = false;

    public BootPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.config = mainFrame.getConfigManager();

        setLayout(new BorderLayout());
        setBackground(StyleHelper.COLOR_BG);

        initUI();
        startBootSequence();
    }

    private void initUI() {
        // Terminal output panel
        JPanel terminalPanel = StyleHelper.createGridPanel("System Boot Log");
        terminalArea = new JTextArea();
        terminalArea.setBackground(StyleHelper.COLOR_BG_PANEL);
        terminalArea.setForeground(StyleHelper.COLOR_GREEN);
        terminalArea.setFont(StyleHelper.FONT_MONO_BODY);
        terminalArea.setEditable(false);
        terminalArea.setLineWrap(true);
        terminalArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(terminalArea);
        StyleHelper.styleScrollPane(scroll);
        terminalPanel.add(scroll, BorderLayout.CENTER);

        // Path Selector Panel (hidden initially or shown empty)
        pathPanel = new JPanel(new GridBagLayout());
        pathPanel.setBackground(StyleHelper.COLOR_BG);
        pathPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // 1. Detected Instances Dropdown Row
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel comboLabel = StyleHelper.createLabel("DETECTED MINECRAFT PROFILES:", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        pathPanel.add(comboLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        instanceCombo = new JComboBox<>();
        StyleHelper.styleComboBox(instanceCombo);
        
        // Scan for standard instances
        scanAndPopulateInstances();
        instanceCombo.addActionListener(e -> {
            String selected = (String) instanceCombo.getSelectedItem();
            if (selected != null && instancePaths.containsKey(selected)) {
                pathField.setText(instancePaths.get(selected));
            }
        });
        pathPanel.add(instanceCombo, gbc);

        // Reset grid width
        gbc.gridwidth = 1;

        // 2. Custom Directory Input Row
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        JLabel pathLabel = StyleHelper.createLabel("MINECRAFT FOLDER PATH:", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        pathPanel.add(pathLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        pathField = new JTextField(35);
        StyleHelper.styleTextField(pathField);
        pathField.setText(config.getLastMinecraftDir());
        pathPanel.add(pathField, gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        browseButton = StyleHelper.createCyberButton("Browse...", false);
        browseButton.addActionListener(e -> browseFolder());
        pathPanel.add(browseButton, gbc);

        gbc.gridx = 2;
        gbc.gridy = 3;
        connectButton = StyleHelper.createCyberButton("Select Folder", true);
        connectButton.addActionListener(e -> connectInstance());
        pathPanel.add(connectButton, gbc);

        // Header controls (skip animation)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topBar.setBackground(StyleHelper.COLOR_BG);
        skipButton = StyleHelper.createCyberButton("Skip Boot Animation", false);
        skipButton.setFont(StyleHelper.FONT_MONO_SMALL);
        skipButton.addActionListener(e -> skipAnimation());
        topBar.add(skipButton);

        add(topBar, BorderLayout.NORTH);
        add(terminalPanel, BorderLayout.CENTER);
        add(pathPanel, BorderLayout.SOUTH);

        // Hide path controls until boot completes
        pathPanel.setVisible(false);
    }

    private void scanAndPopulateInstances() {
        instancePaths.clear();
        instanceCombo.removeAllItems();

        List<File> detected = detectStandardInstances();
        instanceCombo.addItem("Select a detected profile or choose custom path below...");

        for (File f : detected) {
            String displayName = formatInstanceName(f);
            instancePaths.put(displayName, f.getAbsolutePath());
            instanceCombo.addItem(displayName);
        }

        // Add option to refresh
        instancePaths.put("RE-SCAN DIRECTORIES...", "");
        // If last target is set, try matching
        String lastDir = config.getLastMinecraftDir();
        if (lastDir != null && !lastDir.isEmpty()) {
            for (Map.Entry<String, String> entry : instancePaths.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(lastDir)) {
                    instanceCombo.setSelectedItem(entry.getKey());
                    break;
                }
            }
        }
    }

    private List<File> detectStandardInstances() {
        List<File> instances = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        String userHome = System.getProperty("user.home");

        // 1. Vanilla Minecraft profiles folder
        if (appData != null) {
            File vanilla = new File(appData, ".minecraft");
            if (vanilla.exists()) {
                instances.add(vanilla);
                
                File vanillaInstances = new File(vanilla, "instances");
                if (vanillaInstances.exists() && vanillaInstances.isDirectory()) {
                    File[] subs = vanillaInstances.listFiles(File::isDirectory);
                    if (subs != null) {
                        instances.addAll(Arrays.asList(subs));
                    }
                }
            }
        }

        // 2. Prism Launcher
        if (appData != null) {
            File prism = new File(appData, "PrismLauncher/instances");
            if (prism.exists() && prism.isDirectory()) {
                File[] subs = prism.listFiles(File::isDirectory);
                if (subs != null) {
                    instances.addAll(Arrays.asList(subs));
                }
            }
        }

        // 3. MultiMC
        if (appData != null) {
            File multimc = new File(appData, "MultiMC/instances");
            if (multimc.exists() && multimc.isDirectory()) {
                File[] subs = multimc.listFiles(File::isDirectory);
                if (subs != null) {
                    instances.addAll(Arrays.asList(subs));
                }
            }
        }

        // 4. CurseForge
        if (userHome != null) {
            File curseforge = new File(userHome, "curseforge/minecraft/Instances");
            if (curseforge.exists() && curseforge.isDirectory()) {
                File[] subs = curseforge.listFiles(File::isDirectory);
                if (subs != null) {
                    instances.addAll(Arrays.asList(subs));
                }
            }
        }

        return instances;
    }

    private String formatInstanceName(File file) {
        String path = file.getAbsolutePath().replace("\\", "/").toLowerCase();
        String name = file.getName();
        if (name.equalsIgnoreCase(".minecraft")) {
            return "Vanilla Minecraft (.minecraft)";
        }
        if (path.contains("prismlauncher")) {
            return name + " (Prism Launcher)";
        }
        if (path.contains("multimc")) {
            return name + " (MultiMC)";
        }
        if (path.contains("curseforge")) {
            return name + " (CurseForge)";
        }
        if (path.contains(".minecraft/instances")) {
            String loader = "Vanilla";
            if (name.toLowerCase().contains("fabric")) loader = "Fabric";
            else if (name.toLowerCase().contains("neoforge")) loader = "NeoForge";
            else if (name.toLowerCase().contains("forge")) loader = "Forge";
            else if (name.toLowerCase().contains("quilt")) loader = "Quilt";
            return name + " (" + loader + " Profile)";
        }
        return name + " (Custom Instance)";
    }

    private void startBootSequence() {
        bootTimer = new Timer(400, e -> {
            if (currentLineIndex < bootLines.length) {
                terminalArea.append(" > " + bootLines[currentLineIndex] + "\n");
                currentLineIndex++;
            } else {
                completeBoot();
            }
        });
        bootTimer.start();
    }

    private void skipAnimation() {
        if (bootTimer != null && bootTimer.isRunning()) {
            bootTimer.stop();
        }
        terminalArea.setText("");
        for (String line : bootLines) {
            terminalArea.append(" > " + line + "\n");
        }
        completeBoot();
    }

    private void completeBoot() {
        // Critical: Stop timer to prevent endless loops & terminal area spamming
        if (bootTimer != null && bootTimer.isRunning()) {
            bootTimer.stop();
        }
        if (bootCompleted) {
            return;
        }
        bootCompleted = true;
        
        skipButton.setVisible(false);
        pathPanel.setVisible(true);
        revalidate();
        repaint();
        terminalArea.append("\n >>> SYSTEM READY. SELECT YOUR MINECRAFT FOLDER PATH TO CONTINUE.\n");
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Minecraft Instance Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        String currentPath = pathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File f = new File(currentPath);
            if (f.exists()) {
                chooser.setCurrentDirectory(f);
            }
        } else {
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
        }

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void connectInstance() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select or type a directory path.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Selected path does not exist or is not a directory.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Save path in config
        config.setLastMinecraftDir(path);
        config.save();

        // Pass to main frame to scan
        mainFrame.startScanning(path);
    }
}
