package com.minestaller.ui;

import com.minestaller.model.ConfigManager;
import com.minestaller.service.DownloadManager.InstallTask;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MainFrame extends JFrame {

    private final ConfigManager configManager;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Header Indicators
    private JLabel stepLink;
    private JLabel stepDiag;
    private JLabel stepSearch;
    private JLabel stepInstall;

    // Panels
    private BootPanel bootPanel;
    private ScanPanel scanPanel;
    private SearchPanel searchPanel;
    private InstallPanel installPanel;
    private DashboardPanel dashboardPanel;
    private WorldManagerPanel worldManagerPanel;

    // Shared State
    private String targetMinecraftDir = "";
    private String targetMcVersion = "Unknown";
    private String targetLoader = "Unknown";
    private String activeDatapackTargetWorld = null;

    public MainFrame() {
        // Load config
        configManager = ConfigManager.load();

        setTitle("Minestaller - Minecraft Installer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 650);
        setLocationRelativeTo(null);

        // Main background
        getContentPane().setBackground(StyleHelper.COLOR_BG);
        setLayout(new BorderLayout());

        initUI();
    }

    private void initUI() {
        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(StyleHelper.COLOR_BG);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));

        JLabel title = StyleHelper.createLabel("Minestaller - Minecraft Installer", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_TITLE);
        headerPanel.add(title, BorderLayout.WEST);

        // Step breadcrumbs
        JPanel breadcrumbs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        breadcrumbs.setBackground(StyleHelper.COLOR_BG);

        stepLink = StyleHelper.createLabel("[1. SELECT FOLDER]", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        JLabel sep1 = StyleHelper.createLabel("->", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);
        stepDiag = StyleHelper.createLabel("[2. DASHBOARD & WORLDS]", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);
        JLabel sep2 = StyleHelper.createLabel("->", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);
        stepSearch = StyleHelper.createLabel("[3. SEARCH]", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);
        JLabel sep3 = StyleHelper.createLabel("->", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);
        stepInstall = StyleHelper.createLabel("[4. INSTALL]", StyleHelper.COLOR_GRID, StyleHelper.FONT_MONO_HEADER);

        breadcrumbs.add(stepLink);
        breadcrumbs.add(sep1);
        breadcrumbs.add(stepDiag);
        breadcrumbs.add(sep2);
        breadcrumbs.add(stepSearch);
        breadcrumbs.add(sep3);
        breadcrumbs.add(stepInstall);

        headerPanel.add(breadcrumbs, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Center card panel
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(StyleHelper.COLOR_BG);
        cardPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 10, 15));

        bootPanel = new BootPanel(this);
        scanPanel = new ScanPanel(this);
        searchPanel = new SearchPanel(this);
        installPanel = new InstallPanel(this);
        dashboardPanel = new DashboardPanel(this);
        worldManagerPanel = new WorldManagerPanel(this);

        cardPanel.add(bootPanel, "boot");
        cardPanel.add(scanPanel, "scan");
        cardPanel.add(searchPanel, "search");
        cardPanel.add(installPanel, "install");
        cardPanel.add(dashboardPanel, "dashboard");
        cardPanel.add(worldManagerPanel, "worldManager");

        add(cardPanel, BorderLayout.CENTER);

        // Bottom status footer
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(StyleHelper.COLOR_BG);
        footer.setBorder(BorderFactory.createEmptyBorder(5, 15, 10, 15));

        JLabel statusText = StyleHelper.createLabel("Status: Ready", StyleHelper.COLOR_GREEN, StyleHelper.FONT_MONO_SMALL);
        footer.add(statusText, BorderLayout.WEST);

        JLabel connectionText = StyleHelper.createLabel("Database: Modrinth (Secure Connection)", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_SMALL);
        footer.add(connectionText, BorderLayout.EAST);

        add(footer, BorderLayout.SOUTH);

        // Set default theme manager properties
        UIManager.put("Button.arc", 0);
        UIManager.put("Component.arc", 0);
        UIManager.put("TextComponent.arc", 0);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 0);
        
        // Show first step
        goToStep("boot");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public void startScanning(String path) {
        this.targetMinecraftDir = path;
        goToStep("scan");
        scanPanel.runDiagnostics(path);
    }

    public void setTargetProfile(String version, String loader) {
        this.targetMcVersion = version;
        this.targetLoader = loader;
    }

    public String getTargetMinecraftDir() {
        return targetMinecraftDir;
    }

    public String getTargetMcVersion() {
        return targetMcVersion;
    }

    public String getTargetLoader() {
        return targetLoader;
    }

    public String getActiveDatapackTargetWorld() {
        return activeDatapackTargetWorld;
    }

    public void setActiveDatapackTargetWorld(String world) {
        this.activeDatapackTargetWorld = world;
    }

    public void goToDatapacksSearch(String worldName) {
        this.activeDatapackTargetWorld = worldName;
        searchPanel.updateProfileForDatapacks();
        goToStep("search");
    }

    public void updateSearchPanelProfile(String version, String loader) {
        searchPanel.updateProfile(version, loader);
    }

    public void startDownloads(List<InstallTask> tasks) {
        goToStep("install");
        installPanel.showConfirmation(targetMinecraftDir, tasks);
    }

    public void goToStep(String stepName) {
        cardLayout.show(cardPanel, stepName);

        // Reset step header highlights
        stepLink.setForeground(StyleHelper.COLOR_GRID);
        stepDiag.setForeground(StyleHelper.COLOR_GRID);
        stepSearch.setForeground(StyleHelper.COLOR_GRID);
        stepInstall.setForeground(StyleHelper.COLOR_GRID);

        switch (stepName) {
            case "boot":
                stepLink.setForeground(StyleHelper.COLOR_CYAN);
                break;
            case "scan":
            case "dashboard":
            case "worldManager":
                stepDiag.setForeground(StyleHelper.COLOR_CYAN);
                if (stepName.equals("dashboard")) {
                    dashboardPanel.updateDetails();
                } else if (stepName.equals("worldManager")) {
                    worldManagerPanel.startScan();
                }
                break;
            case "search":
                stepSearch.setForeground(StyleHelper.COLOR_CYAN);
                // Do NOT trigger updateProfile here to preserve search state
                break;
            case "install":
                stepInstall.setForeground(StyleHelper.COLOR_CYAN);
                break;
        }
    }
}
