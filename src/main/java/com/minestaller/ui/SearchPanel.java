package com.minestaller.ui;

import com.google.gson.JsonObject;
import com.minestaller.model.ProjectCard;
import com.minestaller.service.DownloadManager;
import com.minestaller.service.DownloadManager.InstallTask;
import com.minestaller.service.ModrinthClient;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SearchPanel extends JPanel {

    private final MainFrame mainFrame;
    private final ModrinthClient modrinthClient = new ModrinthClient();
    private final DownloadManager downloadManager = new DownloadManager();

    private JTextField searchField;
    private JComboBox<String> typeCombo;
    private JComboBox<String> verCombo;
    private JComboBox<String> loaderCombo;
    private JComboBox<String> categoryCombo;
    private JButton searchButton;

    private JList<ProjectCard> resultsList;
    private DefaultListModel<ProjectCard> listModel;

    // Detailed Pane Components
    private JLabel detailTitleLabel;
    private JLabel detailAuthorLabel;
    private JLabel detailDownloadsLabel;
    private JTextPane detailDescPane;
    private JLabel detailIconLabel;
    private JButton installButton;

    // Cart / Multi-Select Mode Fields
    private final List<ProjectCard> cartList = new ArrayList<>();
    private JCheckBox cartModeCheckbox;
    private JButton cartButton;

    // Collapsible Cart Panel Fields
    private JPanel cartContainer;
    private CardLayout cartCardLayout;
    private JButton verticalCartButton;
    private JComboBox<String> cartFilterCombo;
    private JPanel cartItemsPanel;
    private JButton cartPanelInstallButton;

    // Metadata Chips Panels
    private JPanel loadersFlowPanel;
    private JPanel versionsFlowPanel;

    private ProjectCard selectedProject;
    private String currentMcVersion = "Unknown";
    private String currentLoader = "Unknown";

    // Icon Cache for list rendering
    private static final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
    private final ImageIcon defaultIconPlaceholder = createPlaceholderIcon();

    private final String[] projectTypes = { "Mods", "Resource Packs", "Shaders", "Modpacks", "Datapacks" };
    
    // Comprehensive historical Minecraft versions list down to 1.2.5
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

    // Sub-lists of loaders depending on active project type (Mods, Resource Packs, Shaders)
    private final String[] modLoaders = { "Fabric", "Forge", "NeoForge", "Quilt", "Vanilla" };
    private final String[] resourcePackLoaders = { "Vanilla", "OptiFine" };
    private final String[] shaderLoaders = { "Iris / Oculus", "OptiFine", "Vanilla Shader", "Canvas" };

    private final String[] categoriesList = { "Any Category", "Optimization", "Technology", "Magic", "Adventure", "Utility", "Decoration", "Equipment", "Food", "World Gen", "Library" };

    public SearchPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout());
        setBackground(StyleHelper.COLOR_BG);

        initUI();
    }

    private ImageIcon createPlaceholderIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(StyleHelper.COLOR_BG_PANEL);
        g.fillRect(0, 0, 32, 32);
        g.setColor(StyleHelper.COLOR_GRID);
        g.drawRect(0, 0, 31, 31);
        g.setColor(StyleHelper.COLOR_CYAN);
        g.setFont(StyleHelper.FONT_MONO_SMALL);
        g.drawRect(8, 8, 15, 15);
        g.dispose();
        return new ImageIcon(img);
    }

    private void initUI() {
        // Top Filter Bar
        JPanel filterPanel = StyleHelper.createGridPanel("Search Filters");
        filterPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Search Query input
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        searchField = new JTextField();
        StyleHelper.styleTextField(searchField);
        searchField.putClientProperty("JTextField.placeholderText", "Search mods, resource packs, shaders...");
        searchField.addActionListener(e -> performSearch());
        filterPanel.add(searchField, gbc);

        // Project Type Dropdown
        gbc.gridx = 1;
        gbc.weightx = 0.0;
        typeCombo = new JComboBox<>(projectTypes);
        StyleHelper.styleComboBox(typeCombo);
        typeCombo.addActionListener(e -> {
            updateLoaderComboOptions((String) typeCombo.getSelectedItem());
            performSearch();
        });
        filterPanel.add(typeCombo, gbc);

        // Version Dropdown
        gbc.gridx = 2;
        verCombo = new JComboBox<>(commonVersions);
        StyleHelper.styleComboBox(verCombo);
        verCombo.addActionListener(e -> performSearch());
        filterPanel.add(verCombo, gbc);

        // Loader/Platform Dropdown (filled dynamically by type selection)
        gbc.gridx = 3;
        loaderCombo = new JComboBox<>();
        StyleHelper.styleComboBox(loaderCombo);
        updateLoaderComboOptions("Mods"); // Pre-populate for Mods
        loaderCombo.addActionListener(e -> performSearch());
        filterPanel.add(loaderCombo, gbc);

        // Category Dropdown
        gbc.gridx = 4;
        categoryCombo = new JComboBox<>(categoriesList);
        StyleHelper.styleComboBox(categoryCombo);
        categoryCombo.addActionListener(e -> performSearch());
        filterPanel.add(categoryCombo, gbc);

        // Search Button
        gbc.gridx = 5;
        searchButton = StyleHelper.createCyberButton("Search", true);
        searchButton.addActionListener(e -> performSearch());
        filterPanel.add(searchButton, gbc);

        add(filterPanel, BorderLayout.NORTH);

        // Split Pane (Results list on left, details on right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setBackground(StyleHelper.COLOR_BG);
        splitPane.setDividerSize(5);
        splitPane.setDividerLocation(320);

        // Left List panel
        JPanel listPanel = StyleHelper.createGridPanel("Search Results");
        listModel = new DefaultListModel<>();
        resultsList = new JList<>(listModel);
        resultsList.setBackground(StyleHelper.COLOR_BG_PANEL);
        resultsList.setForeground(StyleHelper.COLOR_TEXT);
        resultsList.setFont(StyleHelper.FONT_MONO_BODY);
        resultsList.setSelectionBackground(StyleHelper.COLOR_CYAN);
        resultsList.setSelectionForeground(StyleHelper.COLOR_BG);
        resultsList.setCellRenderer(new ProjectCellRenderer());
        
        resultsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectProject(resultsList.getSelectedValue());
            }
        });

        JScrollPane listScroll = new JScrollPane(resultsList);
        StyleHelper.styleScrollPane(listScroll);
        listPanel.add(listScroll, BorderLayout.CENTER);
        splitPane.setLeftComponent(listPanel);

        // Right details panel
        JPanel detailsPanel = StyleHelper.createGridPanel("Details");
        detailsPanel.setLayout(new BorderLayout());

        // Header containing icon, title, author, downloads
        JPanel detailHeader = new JPanel(new BorderLayout(10, 0));
        detailHeader.setBackground(StyleHelper.COLOR_BG_PANEL);
        detailHeader.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        detailIconLabel = new JLabel();
        detailIconLabel.setPreferredSize(new Dimension(64, 64));
        detailIconLabel.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        detailHeader.add(detailIconLabel, BorderLayout.WEST);

        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 2, 2));
        infoPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        detailTitleLabel = StyleHelper.createLabel("SELECT A PROJECT", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_TITLE);
        detailAuthorLabel = StyleHelper.createLabel("AUTHOR: --", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_SMALL);
        detailDownloadsLabel = StyleHelper.createLabel("DOWNLOADS: --", StyleHelper.COLOR_AMBER, StyleHelper.FONT_MONO_SMALL);
        infoPanel.add(detailTitleLabel);
        infoPanel.add(detailAuthorLabel);
        infoPanel.add(detailDownloadsLabel);
        detailHeader.add(infoPanel, BorderLayout.CENTER);

        // Metadata Chips Panel (CurseForge style)
        JPanel metadataPanel = new JPanel();
        metadataPanel.setLayout(new BoxLayout(metadataPanel, BoxLayout.Y_AXIS));
        metadataPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        metadataPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID),
                BorderFactory.createEmptyBorder(5, 0, 8, 0)
        ));

        // Loaders Row
        JPanel loadersRow = new JPanel(new BorderLayout(5, 0));
        loadersRow.setBackground(StyleHelper.COLOR_BG_PANEL);
        JLabel loadersTitle = StyleHelper.createLabel("MOD LOADERS: ", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_SMALL);
        loadersTitle.setPreferredSize(new Dimension(105, 20));
        loadersFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        loadersFlowPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        loadersRow.add(loadersTitle, BorderLayout.WEST);
        loadersRow.add(loadersFlowPanel, BorderLayout.CENTER);

        // Versions Row
        JPanel versionsRow = new JPanel(new BorderLayout(5, 0));
        versionsRow.setBackground(StyleHelper.COLOR_BG_PANEL);
        JLabel versionsTitle = StyleHelper.createLabel("GAME VERSIONS:", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_SMALL);
        versionsTitle.setPreferredSize(new Dimension(105, 20));
        versionsFlowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        versionsFlowPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        versionsRow.add(versionsTitle, BorderLayout.WEST);
        versionsRow.add(versionsFlowPanel, BorderLayout.CENTER);

        metadataPanel.add(loadersRow);
        metadataPanel.add(Box.createVerticalStrut(5));
        metadataPanel.add(versionsRow);

        // Container combining header + metadata chips panel
        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.setBackground(StyleHelper.COLOR_BG_PANEL);
        topContainer.add(detailHeader, BorderLayout.NORTH);
        topContainer.add(metadataPanel, BorderLayout.CENTER);

        detailsPanel.add(topContainer, BorderLayout.NORTH);

        // Description Body
        detailDescPane = new JTextPane();
        detailDescPane.setBackground(StyleHelper.COLOR_BG_PANEL);
        detailDescPane.setForeground(StyleHelper.COLOR_TEXT);
        detailDescPane.setFont(StyleHelper.FONT_MONO_BODY);
        detailDescPane.setEditable(false);
        detailDescPane.setContentType("text/html");
        
        JScrollPane descScroll = new JScrollPane(detailDescPane);
        StyleHelper.styleScrollPane(descScroll);
        detailsPanel.add(descScroll, BorderLayout.CENTER);

        // Install Control Panel
        JPanel installBar = new JPanel(new BorderLayout());
        installBar.setBackground(StyleHelper.COLOR_BG_PANEL);
        installBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JButton backButton = StyleHelper.createCyberButton("Go Back", false);
        backButton.addActionListener(e -> {
            if (mainFrame.getActiveDatapackTargetWorld() != null) {
                mainFrame.setActiveDatapackTargetWorld(null);
                mainFrame.goToStep("worldManager");
            } else {
                mainFrame.goToStep("dashboard");
            }
        });
        installBar.add(backButton, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightButtons.setBackground(StyleHelper.COLOR_BG_PANEL);

        cartModeCheckbox = new JCheckBox("Enable Cart Mode");
        cartModeCheckbox.setFont(StyleHelper.FONT_MONO_SMALL);
        cartModeCheckbox.setForeground(StyleHelper.COLOR_CYAN);
        cartModeCheckbox.setBackground(StyleHelper.COLOR_BG_PANEL);
        cartModeCheckbox.setFocusPainted(false);
        cartModeCheckbox.addActionListener(e -> toggleCartMode());
        rightButtons.add(cartModeCheckbox);

        cartButton = StyleHelper.createCyberButton("Install Cart (0)", true);
        cartButton.setEnabled(false);
        cartButton.addActionListener(e -> installCart());
        rightButtons.add(cartButton);

        installButton = StyleHelper.createCyberButton("Download & Install", true);
        installButton.setEnabled(false);
        installButton.addActionListener(e -> initiateDownload());
        rightButtons.add(installButton);

        installBar.add(rightButtons, BorderLayout.EAST);
        detailsPanel.add(installBar, BorderLayout.SOUTH);

        splitPane.setRightComponent(detailsPanel);
        add(splitPane, BorderLayout.CENTER);

        // Initialize and add collapsible cart container
        initCartContainer();
        add(cartContainer, BorderLayout.EAST);
    }

    private void updateLoaderComboOptions(String projectType) {
        if (loaderCombo == null) return;
        
        // Temporarily remove action listeners to prevent firing redundant search queries
        java.awt.event.ActionListener[] listeners = loaderCombo.getActionListeners();
        for (java.awt.event.ActionListener al : listeners) {
            loaderCombo.removeActionListener(al);
        }

        loaderCombo.removeAllItems();

        if ("Mods".equalsIgnoreCase(projectType) || "Modpacks".equalsIgnoreCase(projectType)) {
            loaderCombo.setEnabled(true);
            for (String l : modLoaders) {
                loaderCombo.addItem(l);
            }
            selectComboValue(loaderCombo, currentLoader);
        } else if ("Resource Packs".equalsIgnoreCase(projectType)) {
            loaderCombo.addItem("N/A");
            loaderCombo.setEnabled(false);
        } else if ("Shaders".equalsIgnoreCase(projectType)) {
            loaderCombo.setEnabled(true);
            for (String l : shaderLoaders) {
                loaderCombo.addItem(l);
            }
            selectComboValue(loaderCombo, "Iris / Oculus");
        }

        // Re-attach action listeners
        for (java.awt.event.ActionListener al : listeners) {
            loaderCombo.addActionListener(al);
        }
    }

    private String mapLoaderToApi(String loaderName) {
        if (loaderName == null || "N/A".equalsIgnoreCase(loaderName)) return "any";
        switch (loaderName) {
            case "Iris / Oculus": return "iris";
            case "OptiFine": return "optifine";
            case "Vanilla Shader": return "vanilla-shader";
            case "Canvas": return "canvas";
            case "Fabric": return "fabric";
            case "Forge": return "forge";
            case "NeoForge": return "neoforge";
            case "Quilt": return "quilt";
            case "Vanilla": return "vanilla";
            default: return loaderName.toLowerCase();
        }
    }

    public void updateProfile(String version, String loader) {
        if (version == null || version.equalsIgnoreCase("Unknown")) {
            version = "1.20.1";
        }
        if (loader == null || loader.equalsIgnoreCase("Unknown") || loader.equalsIgnoreCase("Vanilla")) {
            loader = "Fabric";
        }

        this.currentMcVersion = version;
        this.currentLoader = loader;

        // Force Mods and mod loader lists
        typeCombo.setSelectedItem("Mods");
        updateLoaderComboOptions("Mods");

        selectComboValue(verCombo, version);
        selectComboValue(loaderCombo, loader);

        performSearch();
    }

    private void selectComboValue(JComboBox<String> combo, String value) {
        if (value == null || value.equalsIgnoreCase("Unknown")) return;
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).equalsIgnoreCase(value)) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.addItem(value);
        combo.setSelectedItem(value);
    }

    private void performSearch() {
        if (typeCombo == null || verCombo == null || loaderCombo == null || categoryCombo == null) return;

        String query = searchField.getText().trim();
        String selectedType = (String) typeCombo.getSelectedItem();
        String typeParam = "mod";
        if ("Resource Packs".equals(selectedType)) typeParam = "resourcepack";
        else if ("Shaders".equals(selectedType)) typeParam = "shader";
        else if ("Modpacks".equals(selectedType)) typeParam = "modpack";
        else if ("Datapacks".equals(selectedType)) typeParam = "datapack";

        String verParam = (String) verCombo.getSelectedItem();
        String loaderParam = mapLoaderToApi((String) loaderCombo.getSelectedItem());
        String categoryParam = (String) categoryCombo.getSelectedItem();

        searchButton.setEnabled(false);
        searchButton.setText("Searching...");

        modrinthClient.search(query, verParam, loaderParam, typeParam, categoryParam)
                .thenAccept(cards -> SwingUtilities.invokeLater(() -> {
                    listModel.clear();
                    if (cards.isEmpty()) {
                        ProjectCard empty = new ProjectCard();
                        empty.setTitle("No Results Found");
                        empty.setAuthor("--");
                        empty.setDescription("No files match the specified filters.");
                        listModel.addElement(empty);
                    } else {
                        for (ProjectCard card : cards) {
                            listModel.addElement(card);
                        }
                    }
                    searchButton.setEnabled(true);
                    searchButton.setText("Search");
                }));
    }

    private void selectProject(ProjectCard project) {
        loadersFlowPanel.removeAll();
        versionsFlowPanel.removeAll();

        if (project == null || "NO RESULTS FOUND".equals(project.getTitle())) {
            this.selectedProject = null;
            detailTitleLabel.setText("Select a Project");
            detailAuthorLabel.setText("Author: --");
            detailDownloadsLabel.setText("Downloads: --");
            detailDescPane.setText("");
            detailIconLabel.setIcon(null);
            installButton.setText("Download & Install");
            installButton.setEnabled(false);
            
            loadersFlowPanel.revalidate();
            loadersFlowPanel.repaint();
            versionsFlowPanel.revalidate();
            versionsFlowPanel.repaint();
            return;
        }

        this.selectedProject = project;
        detailTitleLabel.setText(project.getTitle().toUpperCase());
        detailAuthorLabel.setText("Author: " + project.getAuthor());
        detailDownloadsLabel.setText("Total Downloads: " + String.format("%,d", project.getDownloads()));

        for (String cat : project.getCategories()) {
            loadersFlowPanel.add(createChip(cat, StyleHelper.COLOR_CYAN));
        }

        List<String> versions = project.getVersions();
        int maxVisible = 8;
        for (int i = 0; i < Math.min(versions.size(), maxVisible); i++) {
            versionsFlowPanel.add(createChip(versions.get(i), StyleHelper.COLOR_GREEN));
        }
        if (versions.size() > maxVisible) {
            versionsFlowPanel.add(createChip("+ " + (versions.size() - maxVisible) + " MORE", StyleHelper.COLOR_AMBER));
        }

        loadersFlowPanel.revalidate();
        loadersFlowPanel.repaint();
        versionsFlowPanel.revalidate();
        versionsFlowPanel.repaint();

        // Determine modpack state for current instance
        String currentInstance = mainFrame.getTargetMinecraftDir();
        com.minestaller.model.ConfigManager config = mainFrame.getConfigManager();
        com.minestaller.model.ConfigManager.ModpackState state = config.getModpackState(currentInstance);

        boolean isModpackInstalled = (state != null && state.installedModpackId != null && !state.installedModpackId.isEmpty());
        String warningMsg = "";

        if (project.getProjectType() != null && project.getProjectType().equalsIgnoreCase("modpack")) {
            if (isModpackInstalled) {
                if (state.installedModpackId.equals(project.getId())) {
                    installButton.setText("Uninstall Modpack");
                    installButton.setEnabled(true);
                    warningMsg = "<p style='color:#55FF55; font-weight:bold; font-size:12px;'>"
                            + "Status: Installed (This modpack is already installed in this folder)</p>";
                } else {
                    installButton.setText("Uninstall '" + state.installedModpackTitle + "' First");
                    installButton.setEnabled(true);
                    warningMsg = "<p style='color:#FF5555; font-weight:bold; font-size:12px;'>"
                            + "Warning: Another modpack '" + state.installedModpackTitle + "' is already installed in this folder.<br>"
                            + "You must uninstall it before you can install '" + project.getTitle() + "'.<br>"
                            + "Click the button below to uninstall '" + state.installedModpackTitle + "'.</p>";
                }
            } else {
                installButton.setText("Download & Install");
                installButton.setEnabled(true);
            }
        } else {
            installButton.setText("Download & Install");
            installButton.setEnabled(true);
        }

        boolean isUninstall = installButton.getText().startsWith("Uninstall");
        if (!isUninstall && cartModeCheckbox != null && cartModeCheckbox.isSelected()) {
            if (cartList.contains(project)) {
                installButton.setText("Remove from Cart");
            } else {
                installButton.setText("Add to Cart");
            }
        }

        String htmlDesc = "<html><body style='font-family:monospace; color:#D1D9E6; font-size:11px; margin:10px;'>"
                + "<h3>" + project.getTitle() + "</h3>"
                + warningMsg
                + "<p>" + project.getDescription() + "</p>"
                + "</body></html>";
        detailDescPane.setText(htmlDesc);

        // Load Icon using ImageIO.read (WebP TwelveMonkeys support)
        detailIconLabel.setIcon(null);
        if (project.getIconUrl() != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    URL url = new URL(project.getIconUrl());
                    BufferedImage bimg = ImageIO.read(url);
                    if (bimg != null) {
                        Image img = bimg.getScaledInstance(64, 64, Image.SCALE_SMOOTH);
                        SwingUtilities.invokeLater(() -> {
                            if (selectedProject == project) {
                                detailIconLabel.setIcon(new ImageIcon(img));
                            }
                        });
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private JLabel createChip(String text, Color fgColor) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(StyleHelper.FONT_MONO_SMALL);
        label.setForeground(fgColor);
        label.setBackground(StyleHelper.COLOR_BG);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        return label;
    }

    private void initiateDownload() {
        if (selectedProject == null) return;

        // Check if we should perform uninstall instead
        String btnText = installButton.getText();
        if (btnText.startsWith("UNINSTALL") || btnText.startsWith("Uninstall")) {
            performUninstall();
            return;
        }

        // If Cart Mode is active, perform add/remove from cart!
        if (cartModeCheckbox != null && cartModeCheckbox.isSelected()) {
            if (cartList.contains(selectedProject)) {
                removeFromCart(selectedProject);
            } else {
                addToCart(selectedProject);
            }
            return;
        }

        String verParam = (String) verCombo.getSelectedItem();
        String loaderParam = mapLoaderToApi((String) loaderCombo.getSelectedItem());

        installButton.setEnabled(false);
        installButton.setText("Checking dependencies...");

        downloadManager.resolveDependencies(selectedProject, verParam, loaderParam)
                .thenAccept(tasks -> SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    installButton.setText("Download & Install");

                    if (tasks.isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                                "Could not find a valid download version for " + selectedProject.getTitle() + "\n"
                                        + "matching Minecraft version " + verParam + " and loader " + (String) loaderCombo.getSelectedItem() + ".",
                                "Resolution Failed", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    if (hasDatapackTask(tasks)) {
                        String targetWorld = promptForTargetWorld();
                        if (targetWorld == null) {
                            return; // User cancelled
                        }
                        rewriteDatapackFolders(tasks, targetWorld);
                    }

                    mainFrame.startDownloads(tasks);
                }));
    }

    private void performUninstall() {
        String currentInstance = mainFrame.getTargetMinecraftDir();
        com.minestaller.model.ConfigManager config = mainFrame.getConfigManager();
        com.minestaller.model.ConfigManager.ModpackState state = config.getModpackState(currentInstance);
        
        if (state == null) {
            JOptionPane.showMessageDialog(this, "No modpack detected to uninstall.", "Uninstall Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to completely uninstall the modpack '" + state.installedModpackTitle + "'?\n"
                + "This will delete " + state.installedModpackFiles.size() + " files from your Minecraft directory.",
                "CONFIRM MODPACK DELETION",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        
        if (confirm == JOptionPane.YES_OPTION) {
            installButton.setEnabled(false);
            installButton.setText("Uninstalling...");
            
            CompletableFuture.runAsync(() -> {
                java.io.File rootDir = new java.io.File(currentInstance);
                int deletedCount = 0;
                
                for (String relPath : state.installedModpackFiles) {
                    java.io.File file = new java.io.File(rootDir, relPath);
                    if (file.exists() && file.isFile()) {
                        if (file.delete()) {
                            deletedCount++;
                        }
                    }
                    // Clean up empty parent folders recursively
                    java.io.File parent = file.getParentFile();
                    while (parent != null && !parent.equals(rootDir)) {
                        String[] kids = parent.list();
                        if (kids != null && kids.length == 0) {
                            parent.delete();
                            parent = parent.getParentFile();
                        } else {
                            break;
                        }
                    }
                }
                
                // Clear state
                config.setModpackState(currentInstance, null);
                config.save();
                
                final int finalDeleted = deletedCount;
                SwingUtilities.invokeLater(() -> {
                    installButton.setEnabled(true);
                    JOptionPane.showMessageDialog(this, 
                            "Successfully uninstalled modpack.\nDeleted " + finalDeleted + " files and cleaned up empty folders.",
                            "Uninstall Complete", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Refresh current project view
                    selectProject(selectedProject);
                });
            });
        }
    }



    // Custom Cell Renderer for Project Card with Async Icons support
    private class ProjectCellRenderer extends DefaultListCellRenderer {
        private final JPanel cellPanel;
        private final JLabel titleLabel;
        private final JLabel authorLabel;
        private final JLabel typeLabel;
        private final JLabel iconLabel;

        public ProjectCellRenderer() {
            cellPanel = new JPanel(new GridBagLayout());
            cellPanel.setOpaque(true);
            cellPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(2, 2, 2, 2),
                    BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID)
            ));

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 5, 2, 5);

            // Left Icon Label
            iconLabel = new JLabel();
            iconLabel.setPreferredSize(new Dimension(32, 32));
            iconLabel.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridheight = 2;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            cellPanel.add(iconLabel, gbc);

            // Title
            titleLabel = new JLabel();
            titleLabel.setFont(StyleHelper.FONT_MONO_HEADER);
            gbc.gridx = 1;
            gbc.gridy = 0;
            gbc.gridheight = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            cellPanel.add(titleLabel, gbc);

            // Author
            authorLabel = new JLabel();
            authorLabel.setFont(StyleHelper.FONT_MONO_SMALL);
            gbc.gridx = 1;
            gbc.gridy = 1;
            cellPanel.add(authorLabel, gbc);

            // Type Label on far right
            typeLabel = new JLabel();
            typeLabel.setFont(StyleHelper.FONT_MONO_SMALL);
            typeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            gbc.gridx = 2;
            gbc.gridy = 0;
            gbc.gridheight = 2;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            cellPanel.add(typeLabel, gbc);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            ProjectCard card = (ProjectCard) value;

            if (isSelected) {
                cellPanel.setBackground(StyleHelper.COLOR_CYAN);
                titleLabel.setForeground(StyleHelper.COLOR_BG);
                authorLabel.setForeground(StyleHelper.COLOR_BG);
                typeLabel.setForeground(StyleHelper.COLOR_BG);
            } else {
                cellPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
                titleLabel.setForeground(StyleHelper.COLOR_CYAN);
                authorLabel.setForeground(StyleHelper.COLOR_TEXT);
                typeLabel.setForeground(StyleHelper.COLOR_AMBER);
            }

            titleLabel.setText(card.getTitle());
            authorLabel.setText("by " + card.getAuthor());
            
            String typeStr = card.getProjectType() != null ? card.getProjectType().toUpperCase() : "";
            typeLabel.setText("[" + typeStr + "]");

            // Load and display icon asynchronously using ImageIO.read (TwelveMonkeys resolves WebP)
            iconLabel.setIcon(null);
            String iconUrl = card.getIconUrl();
            if (iconUrl != null && !iconUrl.isEmpty()) {
                if (iconCache.containsKey(iconUrl)) {
                    iconLabel.setIcon(iconCache.get(iconUrl));
                } else {
                    iconLabel.setIcon(defaultIconPlaceholder);
                    CompletableFuture.runAsync(() -> {
                        try {
                            URL url = new URL(iconUrl);
                            BufferedImage bimg = ImageIO.read(url);
                            if (bimg != null) {
                                Image scaledImg = bimg.getScaledInstance(32, 32, Image.SCALE_SMOOTH);
                                ImageIcon scaledIcon = new ImageIcon(scaledImg);
                                iconCache.put(iconUrl, scaledIcon);
                                SwingUtilities.invokeLater(() -> list.repaint());
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } else {
                iconLabel.setIcon(defaultIconPlaceholder);
            }

            return cellPanel;
        }
    }

    private void toggleCartMode() {
        boolean cartMode = cartModeCheckbox.isSelected();
        // Update current selected project button text
        selectProject(selectedProject);
        // Force redraw
        revalidate();
        repaint();
    }

    private void addToCart(ProjectCard project) {
        if (project == null) return;

        // Enforce modpack checks
        if (project.getProjectType() != null && project.getProjectType().equalsIgnoreCase("modpack")) {
            boolean hasModpackInCart = false;
            for (ProjectCard c : cartList) {
                if (c.getProjectType() != null && c.getProjectType().equalsIgnoreCase("modpack")) {
                    hasModpackInCart = true;
                    break;
                }
            }

            String currentInstance = mainFrame.getTargetMinecraftDir();
            com.minestaller.model.ConfigManager config = mainFrame.getConfigManager();
            com.minestaller.model.ConfigManager.ModpackState state = config.getModpackState(currentInstance);
            boolean isModpackInstalled = (state != null && state.installedModpackId != null && !state.installedModpackId.isEmpty());

            if (hasModpackInCart || isModpackInstalled) {
                JOptionPane.showMessageDialog(this,
                        "You cannot add a modpack. There is already one installed in this instance or added to the cart.",
                        "Modpack Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        if (!cartList.contains(project)) {
            cartList.add(project);
        }

        installButton.setText("Remove from Cart");
        cartButton.setText("Install Cart (" + cartList.size() + ")");
        cartButton.setEnabled(true);
        refreshCartView();
    }

    private void removeFromCart(ProjectCard project) {
        if (project == null) return;

        cartList.remove(project);

        installButton.setText("Add to Cart");
        cartButton.setText("Install Cart (" + cartList.size() + ")");
        cartButton.setEnabled(!cartList.isEmpty());
        refreshCartView();
    }

    private void installCart() {
        if (cartList.isEmpty()) return;

        String verParam = (String) verCombo.getSelectedItem();
        String loaderParam = mapLoaderToApi((String) loaderCombo.getSelectedItem());

        // Disable UI controls
        cartModeCheckbox.setEnabled(false);
        cartButton.setEnabled(false);
        cartButton.setText("Resolving Cart...");
        installButton.setEnabled(false);
        if (cartPanelInstallButton != null) {
            cartPanelInstallButton.setEnabled(false);
            cartPanelInstallButton.setText("Resolving Cart...");
        }

        List<CompletableFuture<List<InstallTask>>> futures = new ArrayList<>();
        for (ProjectCard card : cartList) {
            futures.add(downloadManager.resolveDependencies(card, verParam, loaderParam));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenAccept(v -> {
                    List<InstallTask> mergedTasks = new ArrayList<>();
                    java.util.Set<String> addedIds = new java.util.HashSet<>();
                    java.util.Set<String> addedFiles = new java.util.HashSet<>();

                    for (CompletableFuture<List<InstallTask>> future : futures) {
                        try {
                            List<InstallTask> tasks = future.get();
                            for (InstallTask t : tasks) {
                                String uniqueId = t.projectId != null ? t.projectId.toLowerCase() : "";
                                String uniqueFile = t.filename != null ? t.filename.toLowerCase() : "";

                                // De-duplicate by mod/project ID and target filename
                                if ((uniqueId.isEmpty() || !addedIds.contains(uniqueId)) &&
                                    (uniqueFile.isEmpty() || !addedFiles.contains(uniqueFile))) {

                                    if (!uniqueId.isEmpty()) addedIds.add(uniqueId);
                                    if (!uniqueFile.isEmpty()) addedFiles.add(uniqueFile);
                                    mergedTasks.add(t);
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error resolving cart item: " + e.getMessage());
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        // Re-enable controls
                        cartModeCheckbox.setEnabled(true);
                        cartButton.setEnabled(true);
                        cartButton.setText("Install Cart (" + cartList.size() + ")");
                        installButton.setEnabled(selectedProject != null);
                        if (cartPanelInstallButton != null) {
                            cartPanelInstallButton.setEnabled(true);
                            cartPanelInstallButton.setText("Install Cart (" + cartList.size() + ")");
                        }

                        if (mergedTasks.isEmpty()) {
                            JOptionPane.showMessageDialog(this,
                                    "Could not find any valid download versions for the selected cart items\n"
                                    + "matching Minecraft version " + verParam + " and loader " + (String) loaderCombo.getSelectedItem() + ".",
                                    "Resolution Failed", JOptionPane.WARNING_MESSAGE);
                            return;
                        }

                        if (hasDatapackTask(mergedTasks)) {
                            String targetWorld = promptForTargetWorld();
                            if (targetWorld == null) {
                                return; // User cancelled
                            }
                            rewriteDatapackFolders(mergedTasks, targetWorld);
                        }

                        // Clear cart list on success
                        cartList.clear();
                        cartContainer.setPreferredSize(new Dimension(38, 0));
                        cartCardLayout.show(cartContainer, "collapsed");
                        refreshCartView();
                        cartModeCheckbox.setSelected(false);
                        toggleCartMode();
                        revalidate();
                        repaint();

                        // Transition to confirmations
                        mainFrame.startDownloads(mergedTasks);
                    });
                });
    }

    private void initCartContainer() {
        cartCardLayout = new CardLayout();
        cartContainer = new JPanel(cartCardLayout);
        cartContainer.setBackground(StyleHelper.COLOR_BG);
        cartContainer.setPreferredSize(new Dimension(38, 0));

        // 1. Collapsed Card
        JPanel collapsedPanel = new JPanel(new BorderLayout());
        collapsedPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        collapsedPanel.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        collapsedPanel.setPreferredSize(new Dimension(38, 0));

        verticalCartButton = new JButton("<html><center>🛒<br><br>C<br>A<br>R<br>T<br><br>(0)<br>◀</center></html>");
        verticalCartButton.setFont(StyleHelper.FONT_MONO_SMALL);
        verticalCartButton.setFocusPainted(false);
        verticalCartButton.setContentAreaFilled(false);
        verticalCartButton.setOpaque(true);
        verticalCartButton.setBackground(StyleHelper.COLOR_BG_PANEL);
        verticalCartButton.setForeground(StyleHelper.COLOR_CYAN);
        verticalCartButton.setBorder(BorderFactory.createEmptyBorder(10, 2, 10, 2));

        verticalCartButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                verticalCartButton.setContentAreaFilled(true);
                verticalCartButton.setBackground(StyleHelper.COLOR_CYAN);
                verticalCartButton.setForeground(StyleHelper.COLOR_BG);
                verticalCartButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                verticalCartButton.setContentAreaFilled(false);
                verticalCartButton.setBackground(StyleHelper.COLOR_BG_PANEL);
                verticalCartButton.setForeground(StyleHelper.COLOR_CYAN);
            }
        });
        verticalCartButton.addActionListener(e -> {
            cartContainer.setPreferredSize(new Dimension(280, 0));
            cartCardLayout.show(cartContainer, "expanded");
            refreshCartView();
            revalidate();
            repaint();
        });
        collapsedPanel.add(verticalCartButton, BorderLayout.CENTER);

        // 2. Expanded Card
        JPanel expandedPanel = new JPanel(new BorderLayout(0, 5));
        expandedPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        expandedPanel.setPreferredSize(new Dimension(280, 0));
        expandedPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, StyleHelper.COLOR_GRID));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));

        JLabel titleLabel = StyleHelper.createLabel("SHOPPING CART", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        JButton closeButton = StyleHelper.createCyberButton("▶", false);
        closeButton.setFont(StyleHelper.FONT_MONO_SMALL);
        closeButton.setPreferredSize(new Dimension(30, 24));
        closeButton.addActionListener(e -> {
            cartContainer.setPreferredSize(new Dimension(38, 0));
            cartCardLayout.show(cartContainer, "collapsed");
            revalidate();
            repaint();
        });

        headerPanel.add(titleLabel, BorderLayout.CENTER);
        headerPanel.add(closeButton, BorderLayout.EAST);

        // Filter Dropdown
        JPanel filterRow = new JPanel(new BorderLayout(5, 0));
        filterRow.setBackground(StyleHelper.COLOR_BG_PANEL);
        filterRow.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JLabel filterLabel = StyleHelper.createLabel("Filter: ", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_SMALL);
        cartFilterCombo = new JComboBox<>(new String[]{"All Types", "Mods", "Resource Packs", "Shaders", "Modpacks", "Datapacks"});
        StyleHelper.styleComboBox(cartFilterCombo);
        cartFilterCombo.setFont(StyleHelper.FONT_MONO_SMALL);
        cartFilterCombo.addActionListener(e -> refreshCartView());

        filterRow.add(filterLabel, BorderLayout.WEST);
        filterRow.add(cartFilterCombo, BorderLayout.CENTER);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        northPanel.add(headerPanel, BorderLayout.NORTH);
        northPanel.add(filterRow, BorderLayout.SOUTH);

        expandedPanel.add(northPanel, BorderLayout.NORTH);

        // Center Panel (List of items scroll pane)
        cartItemsPanel = new JPanel();
        cartItemsPanel.setLayout(new BoxLayout(cartItemsPanel, BoxLayout.Y_AXIS));
        cartItemsPanel.setBackground(StyleHelper.COLOR_BG_PANEL);

        JScrollPane cartScroll = new JScrollPane(cartItemsPanel);
        StyleHelper.styleScrollPane(cartScroll);
        cartScroll.setBorder(BorderFactory.createEmptyBorder());
        expandedPanel.add(cartScroll, BorderLayout.CENTER);

        // South Panel (Action Buttons)
        JPanel southPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        southPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        southPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        cartPanelInstallButton = StyleHelper.createCyberButton("Install Cart (0)", true);
        cartPanelInstallButton.setEnabled(false);
        cartPanelInstallButton.addActionListener(e -> installCart());

        JButton clearCartButton = StyleHelper.createCyberButton("Clear Cart", false);
        clearCartButton.setForeground(StyleHelper.COLOR_AMBER);
        clearCartButton.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_AMBER, 1));
        clearCartButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                clearCartButton.setBackground(StyleHelper.COLOR_AMBER);
                clearCartButton.setForeground(StyleHelper.COLOR_BG);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                clearCartButton.setBackground(StyleHelper.COLOR_BG_PANEL);
                clearCartButton.setForeground(StyleHelper.COLOR_AMBER);
            }
        });
        clearCartButton.addActionListener(e -> {
            if (!cartList.isEmpty()) {
                cartList.clear();
                refreshCartView();
                toggleCartMode(); // Sync details button
            }
        });

        southPanel.add(cartPanelInstallButton);
        southPanel.add(clearCartButton);

        expandedPanel.add(southPanel, BorderLayout.SOUTH);

        cartContainer.add(collapsedPanel, "collapsed");
        cartContainer.add(expandedPanel, "expanded");
        cartCardLayout.show(cartContainer, "collapsed");
    }

    private void refreshCartView() {
        if (cartItemsPanel == null) return;

        cartItemsPanel.removeAll();
        String selectedFilter = (String) cartFilterCombo.getSelectedItem();

        for (ProjectCard card : cartList) {
            // Filter logic
            boolean matches = false;
            if ("All Types".equals(selectedFilter)) {
                matches = true;
            } else if ("Mods".equals(selectedFilter) && "mod".equalsIgnoreCase(card.getProjectType())) {
                matches = true;
            } else if ("Resource Packs".equals(selectedFilter) && "resourcepack".equalsIgnoreCase(card.getProjectType())) {
                matches = true;
            } else if ("Shaders".equals(selectedFilter) && "shader".equalsIgnoreCase(card.getProjectType())) {
                matches = true;
            } else if ("Modpacks".equals(selectedFilter) && "modpack".equalsIgnoreCase(card.getProjectType())) {
                matches = true;
            } else if ("Datapacks".equals(selectedFilter) && "datapack".equalsIgnoreCase(card.getProjectType())) {
                matches = true;
            }

            if (matches) {
                JPanel itemRow = new JPanel(new BorderLayout(5, 0));
                itemRow.setBackground(StyleHelper.COLOR_BG_PANEL);
                itemRow.setMaximumSize(new Dimension(320, 48));
                itemRow.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID),
                        BorderFactory.createEmptyBorder(4, 10, 4, 10)
                ));

                // Left: Info
                JPanel info = new JPanel(new GridLayout(2, 1, 2, 2));
                info.setBackground(StyleHelper.COLOR_BG_PANEL);

                JLabel title = StyleHelper.createLabel(card.getTitle(), StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_SMALL);
                title.setFont(title.getFont().deriveFont(Font.BOLD));
                
                String typeStr = card.getProjectType() != null ? card.getProjectType().toUpperCase() : "";
                JLabel sub = StyleHelper.createLabel("[" + typeStr + "] by " + card.getAuthor(), StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_SMALL);
                sub.setFont(sub.getFont().deriveFont(9f));

                info.add(title);
                info.add(sub);
                itemRow.add(info, BorderLayout.CENTER);

                // Right: Delete button
                JButton removeBtn = new JButton("X");
                removeBtn.setFont(StyleHelper.FONT_MONO_SMALL);
                removeBtn.setFocusPainted(false);
                removeBtn.setContentAreaFilled(false);
                removeBtn.setOpaque(true);
                removeBtn.setBackground(StyleHelper.COLOR_BG_PANEL);
                removeBtn.setForeground(StyleHelper.COLOR_AMBER);
                removeBtn.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_AMBER, 1));
                removeBtn.setPreferredSize(new Dimension(24, 24));

                removeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        removeBtn.setContentAreaFilled(true);
                        removeBtn.setBackground(StyleHelper.COLOR_AMBER);
                        removeBtn.setForeground(StyleHelper.COLOR_BG);
                        removeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        removeBtn.setContentAreaFilled(false);
                        removeBtn.setBackground(StyleHelper.COLOR_BG_PANEL);
                        removeBtn.setForeground(StyleHelper.COLOR_AMBER);
                    }
                });
                removeBtn.addActionListener(e -> {
                    removeFromCart(card);
                    refreshCartView();
                    toggleCartMode(); // Sync details button
                });
                itemRow.add(removeBtn, BorderLayout.EAST);

                cartItemsPanel.add(itemRow);
            }
        }

        cartItemsPanel.add(Box.createVerticalGlue());

        // Update counts
        if (verticalCartButton != null) {
            verticalCartButton.setText("<html><center>🛒<br><br>C<br>A<br>R<br>T<br><br><b>(" + cartList.size() + ")</b><br>◀</center></html>");
        }
        if (cartPanelInstallButton != null) {
            cartPanelInstallButton.setText("Install Cart (" + cartList.size() + ")");
            cartPanelInstallButton.setEnabled(!cartList.isEmpty());
        }
        if (cartButton != null) {
            cartButton.setText("Install Cart (" + cartList.size() + ")");
            cartButton.setEnabled(!cartList.isEmpty());
        }

        cartItemsPanel.revalidate();
        cartItemsPanel.repaint();
    }

    public void updateProfileForDatapacks() {
        typeCombo.setSelectedItem("Datapacks");
        updateLoaderComboOptions("Datapacks");
        selectComboValue(verCombo, currentMcVersion);
        performSearch();
    }

    private boolean hasDatapackTask(List<InstallTask> tasks) {
        for (InstallTask t : tasks) {
            if ("datapacks".equalsIgnoreCase(t.targetFolder)) {
                return true;
            }
        }
        return false;
    }

    private void rewriteDatapackFolders(List<InstallTask> tasks, String worldName) {
        for (InstallTask t : tasks) {
            if ("datapacks".equalsIgnoreCase(t.targetFolder)) {
                t.targetFolder = "saves/" + worldName + "/datapacks";
            }
        }
    }

    private String promptForTargetWorld() {
        String preSelected = mainFrame.getActiveDatapackTargetWorld();
        if (preSelected != null && !preSelected.isEmpty()) {
            return preSelected;
        }

        String path = mainFrame.getTargetMinecraftDir();
        if (path == null || path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No Minecraft instance folder selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        File savesDir = new File(path, "saves");
        if (!savesDir.exists() || !savesDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "The active instance saves directory does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        File[] files = savesDir.listFiles();
        List<String> worlds = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && new File(f, "level.dat").exists()) {
                    worlds.add(f.getName());
                }
            }
        }

        if (worlds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No Minecraft worlds found in this instance saves folder.\nCreate a world first.", "No Worlds Found", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        String[] choices = worlds.toArray(new String[0]);
        String input = (String) JOptionPane.showInputDialog(
                this,
                "Select the target world to install the datapack(s) into:",
                "SELECT TARGET WORLD",
                JOptionPane.QUESTION_MESSAGE,
                null,
                choices,
                choices[0]
        );

        return input;
    }
}
