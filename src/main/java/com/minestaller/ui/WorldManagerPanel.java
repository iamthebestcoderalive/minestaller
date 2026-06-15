package com.minestaller.ui;

import com.minestaller.model.Nbt;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WorldManagerPanel extends JPanel {

    private final MainFrame mainFrame;
    private JList<WorldInfo> worldsList;
    private DefaultListModel<WorldInfo> listModel;

    // Right Details components
    private JPanel detailsContainer;
    private JLabel detailTitleLabel;
    private JLabel detailPathLabel;
    private JTabbedPane tabbedPane;

    // Settings Tab components
    private JTextField nameField;
    private JComboBox<String> modeCombo;
    private JComboBox<String> difficultyCombo;
    private JCheckBox cheatsCheckbox;
    private JCheckBox hardcoreCheckbox;
    private JButton saveSettingsButton;

    // Datapacks Tab components
    private JList<String> installedDatapacksList;
    private DefaultListModel<String> installedModel;
    private JButton deleteDatapackButton;

    private JList<File> cartList;
    private DefaultListModel<File> cartModel;
    private JButton queueDatapacksButton;
    private JButton clearCartButton;
    private JButton installCartButton;
    private JButton addFromModrinthButton;

    private WorldInfo selectedWorld;

    public WorldManagerPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(StyleHelper.COLOR_BG);

        initUI();
    }

    private void initUI() {
        // Left Side: Worlds List
        JPanel leftPanel = StyleHelper.createGridPanel("Local Worlds");
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.setLayout(new BorderLayout());

        listModel = new DefaultListModel<>();
        worldsList = new JList<>(listModel);
        worldsList.setBackground(StyleHelper.COLOR_BG_PANEL);
        worldsList.setForeground(StyleHelper.COLOR_TEXT);
        worldsList.setFont(StyleHelper.FONT_MONO_BODY);
        worldsList.setSelectionBackground(StyleHelper.COLOR_CYAN);
        worldsList.setSelectionForeground(StyleHelper.COLOR_BG);
        worldsList.setCellRenderer(new WorldCellRenderer());

        worldsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectWorld(worldsList.getSelectedValue());
            }
        });

        JScrollPane scrollPane = new JScrollPane(worldsList);
        StyleHelper.styleScrollPane(scrollPane);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // Right Side: Details & Configuration
        detailsContainer = StyleHelper.createGridPanel("World Configuration");
        detailsContainer.setLayout(new BorderLayout());

        // Header Panel (World Name & Path)
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        detailTitleLabel = StyleHelper.createLabel("SELECT A WORLD", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_TITLE);
        detailPathLabel = StyleHelper.createLabel("Path: --", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_SMALL);

        headerPanel.add(detailTitleLabel, BorderLayout.NORTH);
        headerPanel.add(detailPathLabel, BorderLayout.SOUTH);
        detailsContainer.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Pane for Settings vs Datapacks
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(StyleHelper.COLOR_BG_PANEL);
        tabbedPane.setForeground(StyleHelper.COLOR_TEXT);
        tabbedPane.setFont(StyleHelper.FONT_MONO_HEADER);

        // Tab 1: Settings
        JPanel settingsTab = new JPanel(new GridBagLayout());
        settingsTab.setBackground(StyleHelper.COLOR_BG_PANEL);
        settingsTab.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Name
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.3;
        settingsTab.add(StyleHelper.createLabel("World Name:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        nameField = new JTextField();
        StyleHelper.styleTextField(nameField);
        settingsTab.add(nameField, gbc);

        // Game Mode
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.3;
        settingsTab.add(StyleHelper.createLabel("Game Mode:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        modeCombo = new JComboBox<>(new String[]{"Survival", "Creative", "Adventure", "Spectator"});
        StyleHelper.styleComboBox(modeCombo);
        settingsTab.add(modeCombo, gbc);

        // Difficulty
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.3;
        settingsTab.add(StyleHelper.createLabel("Difficulty:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        difficultyCombo = new JComboBox<>(new String[]{"Peaceful", "Easy", "Normal", "Hard"});
        StyleHelper.styleComboBox(difficultyCombo);
        settingsTab.add(difficultyCombo, gbc);

        // Cheats checkbox
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weightx = 1.0;
        cheatsCheckbox = new JCheckBox("Allow Cheats / Commands");
        cheatsCheckbox.setFont(StyleHelper.FONT_MONO_BODY);
        cheatsCheckbox.setForeground(StyleHelper.COLOR_CYAN);
        cheatsCheckbox.setBackground(StyleHelper.COLOR_BG_PANEL);
        cheatsCheckbox.setFocusPainted(false);
        settingsTab.add(cheatsCheckbox, gbc);

        // Hardcore checkbox
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        hardcoreCheckbox = new JCheckBox("Hardcore Mode (One Life, Locked Difficulty)");
        hardcoreCheckbox.setFont(StyleHelper.FONT_MONO_BODY);
        hardcoreCheckbox.setForeground(StyleHelper.COLOR_CYAN);
        hardcoreCheckbox.setBackground(StyleHelper.COLOR_BG_PANEL);
        hardcoreCheckbox.setFocusPainted(false);
        hardcoreCheckbox.addActionListener(e -> {
            if (hardcoreCheckbox.isSelected()) {
                difficultyCombo.setSelectedItem("Hard");
                difficultyCombo.setEnabled(false);
            } else {
                difficultyCombo.setEnabled(true);
            }
        });
        settingsTab.add(hardcoreCheckbox, gbc);

        // Spacer
        gbc.gridy = 5; gbc.gridwidth = 2; gbc.weighty = 1.0;
        settingsTab.add(Box.createGlue(), gbc);

        // Save Button Row
        gbc.gridy = 6; gbc.gridwidth = 2; gbc.weighty = 0.0;
        saveSettingsButton = StyleHelper.createCyberButton("Save Settings", true);
        saveSettingsButton.addActionListener(e -> saveWorldSettings());
        settingsTab.add(saveSettingsButton, gbc);

        tabbedPane.addTab("Settings", settingsTab);

        // Tab 2: Datapacks
        JPanel datapacksTab = new JPanel(new GridLayout(1, 2, 15, 0));
        datapacksTab.setBackground(StyleHelper.COLOR_BG_PANEL);
        datapacksTab.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Installed Datapacks (Left)
        JPanel installedPanel = new JPanel(new BorderLayout(0, 5));
        installedPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        installedPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1),
                " Installed Datapacks ",
                0, 0, StyleHelper.FONT_MONO_HEADER, StyleHelper.COLOR_CYAN
        ));

        installedModel = new DefaultListModel<>();
        installedDatapacksList = new JList<>(installedModel);
        installedDatapacksList.setBackground(StyleHelper.COLOR_BG_PANEL);
        installedDatapacksList.setForeground(StyleHelper.COLOR_TEXT);
        installedDatapacksList.setFont(StyleHelper.FONT_MONO_BODY);
        installedDatapacksList.setSelectionBackground(StyleHelper.COLOR_CYAN);
        installedDatapacksList.setSelectionForeground(StyleHelper.COLOR_BG);

        JScrollPane installedScroll = new JScrollPane(installedDatapacksList);
        StyleHelper.styleScrollPane(installedScroll);
        installedPanel.add(installedScroll, BorderLayout.CENTER);

        deleteDatapackButton = StyleHelper.createCyberButton("Delete Selected", false);
        deleteDatapackButton.setForeground(StyleHelper.COLOR_AMBER);
        deleteDatapackButton.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_AMBER, 1));
        deleteDatapackButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                deleteDatapackButton.setBackground(StyleHelper.COLOR_AMBER);
                deleteDatapackButton.setForeground(StyleHelper.COLOR_BG);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                deleteDatapackButton.setBackground(StyleHelper.COLOR_BG_PANEL);
                deleteDatapackButton.setForeground(StyleHelper.COLOR_AMBER);
            }
        });
        deleteDatapackButton.addActionListener(e -> deleteSelectedDatapack());
        installedPanel.add(deleteDatapackButton, BorderLayout.SOUTH);

        // Datapacks Cart (Right)
        JPanel cartPanel = new JPanel(new BorderLayout(0, 5));
        cartPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
        cartPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1),
                " Datapacks Installation Cart ",
                0, 0, StyleHelper.FONT_MONO_HEADER, StyleHelper.COLOR_GREEN
        ));

        cartModel = new DefaultListModel<>();
        cartList = new JList<>(cartModel);
        cartList.setBackground(StyleHelper.COLOR_BG_PANEL);
        cartList.setForeground(StyleHelper.COLOR_TEXT);
        cartList.setFont(StyleHelper.FONT_MONO_BODY);
        cartList.setSelectionBackground(StyleHelper.COLOR_CYAN);
        cartList.setSelectionForeground(StyleHelper.COLOR_BG);
        cartList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof File) {
                    l.setText(((File) value).getName() + " (" + (((File) value).isDirectory() ? "Folder" : "ZIP") + ")");
                }
                l.setForeground(isSelected ? StyleHelper.COLOR_BG : StyleHelper.COLOR_GREEN);
                return l;
            }
        });

        JScrollPane cartScroll = new JScrollPane(cartList);
        StyleHelper.styleScrollPane(cartScroll);
        cartPanel.add(cartScroll, BorderLayout.CENTER);

        JPanel cartActions = new JPanel(new GridLayout(1, 4, 3, 0));
        cartActions.setBackground(StyleHelper.COLOR_BG_PANEL);

        queueDatapacksButton = StyleHelper.createCyberButton("Queue Packs", false);
        queueDatapacksButton.setFont(StyleHelper.FONT_MONO_SMALL);
        queueDatapacksButton.addActionListener(e -> queueDatapacks());

        clearCartButton = StyleHelper.createCyberButton("Clear", false);
        clearCartButton.setFont(StyleHelper.FONT_MONO_SMALL);
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
            cartModel.clear();
            updateCartButtonState();
        });

        addFromModrinthButton = StyleHelper.createCyberButton("Modrinth", false);
        addFromModrinthButton.setFont(StyleHelper.FONT_MONO_SMALL);
        addFromModrinthButton.addActionListener(e -> {
            if (selectedWorld != null) {
                mainFrame.goToDatapacksSearch(selectedWorld.directory.getName());
            }
        });

        installCartButton = StyleHelper.createCyberButton("Install (0)", true);
        installCartButton.setFont(StyleHelper.FONT_MONO_SMALL);
        installCartButton.setEnabled(false);
        installCartButton.addActionListener(e -> installCart());

        cartActions.add(queueDatapacksButton);
        cartActions.add(clearCartButton);
        cartActions.add(addFromModrinthButton);
        cartActions.add(installCartButton);
        cartPanel.add(cartActions, BorderLayout.SOUTH);

        datapacksTab.add(installedPanel);
        datapacksTab.add(cartPanel);

        tabbedPane.addTab("Datapacks", datapacksTab);
        detailsContainer.add(tabbedPane, BorderLayout.CENTER);

        // Main Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, detailsContainer);
        splitPane.setBackground(StyleHelper.COLOR_BG);
        splitPane.setDividerLocation(250);
        splitPane.setDividerSize(5);
        add(splitPane, BorderLayout.CENTER);

        // Bottom Navigation
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBackground(StyleHelper.COLOR_BG);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JButton backButton = StyleHelper.createCyberButton("Go Back", false);
        backButton.addActionListener(e -> {
            cartModel.clear();
            mainFrame.goToStep("dashboard");
        });
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Initial state
        setEditingEnabled(false);
    }

    public void startScan() {
        listModel.clear();
        cartModel.clear();
        updateCartButtonState();
        selectWorld(null);

        String path = mainFrame.getTargetMinecraftDir();
        if (path == null || path.isEmpty()) return;

        File savesDir = new File(path, "saves");
        if (!savesDir.exists() || !savesDir.isDirectory()) {
            return;
        }

        File[] files = savesDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && new File(f, "level.dat").exists()) {
                    WorldInfo wi = new WorldInfo(f);
                    try {
                        wi.load();
                        listModel.addElement(wi);
                    } catch (Exception e) {
                        // Show corrupted tag load
                        wi.levelName = f.getName() + " (Load Error)";
                        listModel.addElement(wi);
                        System.err.println("Failed to load world data: " + f.getName() + " - " + e.getMessage());
                    }
                }
            }
        }

        if (!listModel.isEmpty()) {
            worldsList.setSelectedIndex(0);
        }
    }

    private void selectWorld(WorldInfo world) {
        this.selectedWorld = world;
        if (world == null) {
            detailTitleLabel.setText("SELECT A WORLD");
            detailPathLabel.setText("Path: --");
            setEditingEnabled(false);
            installedModel.clear();
            cartModel.clear();
            updateCartButtonState();
            return;
        }

        detailTitleLabel.setText(world.levelName.toUpperCase());
        detailPathLabel.setText("Path: " + world.directory.getAbsolutePath());
        setEditingEnabled(world.rawNbt != null);

        if (world.rawNbt != null) {
            nameField.setText(world.levelName);
            selectGameMode(world.gameType);
            selectDifficulty(world.difficulty);
            cheatsCheckbox.setSelected(world.allowCommands);
            hardcoreCheckbox.setSelected(world.hardcore);

            // Handle initial hardcore lock
            if (world.hardcore) {
                difficultyCombo.setSelectedItem("Hard");
                difficultyCombo.setEnabled(false);
            } else {
                difficultyCombo.setEnabled(true);
            }
        }

        // Load Datapacks
        loadInstalledDatapacks();
        cartModel.clear();
        updateCartButtonState();
    }

    private void setEditingEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        modeCombo.setEnabled(enabled);
        difficultyCombo.setEnabled(enabled && (selectedWorld == null || !selectedWorld.hardcore));
        cheatsCheckbox.setEnabled(enabled);
        hardcoreCheckbox.setEnabled(enabled);
        saveSettingsButton.setEnabled(enabled);

        installedDatapacksList.setEnabled(enabled);
        deleteDatapackButton.setEnabled(enabled);
        cartList.setEnabled(enabled);
        queueDatapacksButton.setEnabled(enabled);
        clearCartButton.setEnabled(enabled);
        addFromModrinthButton.setEnabled(enabled);
        installCartButton.setEnabled(enabled && !cartModel.isEmpty());
    }

    private void selectGameMode(int type) {
        switch (type) {
            case 0: modeCombo.setSelectedItem("Survival"); break;
            case 1: modeCombo.setSelectedItem("Creative"); break;
            case 2: modeCombo.setSelectedItem("Adventure"); break;
            case 3: modeCombo.setSelectedItem("Spectator"); break;
            default: modeCombo.setSelectedIndex(-1); break;
        }
    }

    private void selectDifficulty(byte diff) {
        switch (diff) {
            case 0: difficultyCombo.setSelectedItem("Peaceful"); break;
            case 1: difficultyCombo.setSelectedItem("Easy"); break;
            case 2: difficultyCombo.setSelectedItem("Normal"); break;
            case 3: difficultyCombo.setSelectedItem("Hard"); break;
            default: difficultyCombo.setSelectedIndex(-1); break;
        }
    }

    private void saveWorldSettings() {
        if (selectedWorld == null || selectedWorld.rawNbt == null) return;

        selectedWorld.levelName = nameField.getText().trim();
        if (selectedWorld.levelName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "World Name cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Gamemode
        String modeStr = (String) modeCombo.getSelectedItem();
        if ("Survival".equals(modeStr)) selectedWorld.gameType = 0;
        else if ("Creative".equals(modeStr)) selectedWorld.gameType = 1;
        else if ("Adventure".equals(modeStr)) selectedWorld.gameType = 2;
        else if ("Spectator".equals(modeStr)) selectedWorld.gameType = 3;

        // Difficulty
        String diffStr = (String) difficultyCombo.getSelectedItem();
        if ("Peaceful".equals(diffStr)) selectedWorld.difficulty = 0;
        else if ("Easy".equals(diffStr)) selectedWorld.difficulty = 1;
        else if ("Normal".equals(diffStr)) selectedWorld.difficulty = 2;
        else if ("Hard".equals(diffStr)) selectedWorld.difficulty = 3;

        selectedWorld.allowCommands = cheatsCheckbox.isSelected();
        selectedWorld.hardcore = hardcoreCheckbox.isSelected();

        saveSettingsButton.setEnabled(false);
        saveSettingsButton.setText("Saving...");

        // Save in background
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                selectedWorld.save();
                return null;
            }

            @Override
            protected void done() {
                saveSettingsButton.setEnabled(true);
                saveSettingsButton.setText("Save Settings");
                try {
                    get();
                    detailTitleLabel.setText(selectedWorld.levelName.toUpperCase());
                    worldsList.repaint();
                    JOptionPane.showMessageDialog(WorldManagerPanel.this, "World metadata saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(WorldManagerPanel.this, "Failed to save world metadata:\n" + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void loadInstalledDatapacks() {
        installedModel.clear();
        if (selectedWorld == null) return;

        File datapacksDir = new File(selectedWorld.directory, "datapacks");
        if (datapacksDir.exists() && datapacksDir.isDirectory()) {
            File[] files = datapacksDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    installedModel.addElement(f.getName());
                }
            }
        }
    }

    private void deleteSelectedDatapack() {
        String selected = installedDatapacksList.getSelectedValue();
        if (selected == null || selectedWorld == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to permanently delete the datapack '" + selected + "'?",
                "CONFIRM DATAPACK DELETION",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            File packFile = new File(new File(selectedWorld.directory, "datapacks"), selected);
            deleteFileRecursive(packFile);
            loadInstalledDatapacks();
            JOptionPane.showMessageDialog(this, "Datapack deleted.", "Deletion Successful", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteFileRecursive(File file) {
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteFileRecursive(k);
                }
            }
        }
        file.delete();
    }

    private void queueDatapacks() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("Select Datapack ZIP files or Folders to Install");

        int option = chooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            for (File f : selectedFiles) {
                if (!cartModel.contains(f)) {
                    cartModel.addElement(f);
                }
            }
            updateCartButtonState();
        }
    }

    private void updateCartButtonState() {
        int size = cartModel.size();
        installCartButton.setText("Install (" + size + ")");
        installCartButton.setEnabled(size > 0 && selectedWorld != null && selectedWorld.rawNbt != null);
    }

    private void installCart() {
        if (selectedWorld == null || cartModel.isEmpty()) return;

        File datapacksDir = new File(selectedWorld.directory, "datapacks");
        if (!datapacksDir.exists()) {
            datapacksDir.mkdirs();
        }

        installCartButton.setEnabled(false);
        installCartButton.setText("Installing...");

        List<File> pendingFiles = new ArrayList<>();
        for (int i = 0; i < cartModel.size(); i++) {
            pendingFiles.add(cartModel.getElementAt(i));
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (File f : pendingFiles) {
                    File dest = new File(datapacksDir, f.getName());
                    copyRecursive(f, dest);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    cartModel.clear();
                    updateCartButtonState();
                    loadInstalledDatapacks();
                    JOptionPane.showMessageDialog(WorldManagerPanel.this, "Datapacks installed successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(WorldManagerPanel.this, "Error installing datapacks:\n" + ex.getMessage(), "Installation Error", JOptionPane.ERROR_MESSAGE);
                    updateCartButtonState();
                }
            }
        };
        worker.execute();
    }

    private void copyRecursive(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);
                    copyRecursive(srcFile, destFile);
                }
            }
        } else {
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }
    }

    // World Data Wrapper Class
    public static class WorldInfo {
        public final File directory;
        public String levelName;
        public int gameType;
        public byte difficulty;
        public boolean hardcore;
        public boolean allowCommands;
        public Nbt.NamedTag rawNbt;

        public WorldInfo(File directory) {
            this.directory = directory;
            this.levelName = directory.getName();
        }

        public void load() throws Exception {
            File levelDat = new File(directory, "level.dat");
            if (levelDat.exists()) {
                this.rawNbt = Nbt.readFile(levelDat);
                Nbt.TagCompound root = rawNbt.tag;
                Nbt.TagCompound data = (Nbt.TagCompound) root.get("Data");
                if (data != null) {
                    this.levelName = data.getString("LevelName", directory.getName());
                    this.gameType = data.getInt("GameType", 0);
                    this.difficulty = data.getByte("Difficulty", (byte) 2);
                    this.hardcore = data.getByte("hardcore", (byte) 0) == 1;
                    this.allowCommands = data.getByte("allowCommands", (byte) 0) == 1;
                }
            }
        }

        public void save() throws Exception {
            File levelDat = new File(directory, "level.dat");
            if (rawNbt != null) {
                Nbt.TagCompound root = rawNbt.tag;
                Nbt.TagCompound data = (Nbt.TagCompound) root.get("Data");
                if (data != null) {
                    data.putString("LevelName", levelName);
                    data.putInt("GameType", gameType);
                    data.putByte("Difficulty", difficulty);
                    data.putByte("hardcore", (byte) (hardcore ? 1 : 0));
                    data.putByte("allowCommands", (byte) (allowCommands ? 1 : 0));

                    Nbt.writeFile(rawNbt, levelDat);
                }
            }
        }
    }

    // Cell Renderer for World Info List
    private static class WorldCellRenderer extends DefaultListCellRenderer {
        private final JPanel cellPanel;
        private final JLabel nameLabel;
        private final JLabel detailsLabel;

        public WorldCellRenderer() {
            cellPanel = new JPanel(new GridLayout(2, 1, 2, 2));
            cellPanel.setOpaque(true);
            cellPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(4, 8, 4, 8),
                    BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID)
            ));

            nameLabel = new JLabel();
            nameLabel.setFont(StyleHelper.FONT_MONO_HEADER);

            detailsLabel = new JLabel();
            detailsLabel.setFont(StyleHelper.FONT_MONO_SMALL);

            cellPanel.add(nameLabel);
            cellPanel.add(detailsLabel);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            WorldInfo wi = (WorldInfo) value;

            if (isSelected) {
                cellPanel.setBackground(StyleHelper.COLOR_CYAN);
                nameLabel.setForeground(StyleHelper.COLOR_BG);
                detailsLabel.setForeground(StyleHelper.COLOR_BG);
            } else {
                cellPanel.setBackground(StyleHelper.COLOR_BG_PANEL);
                nameLabel.setForeground(StyleHelper.COLOR_CYAN);
                detailsLabel.setForeground(StyleHelper.COLOR_TEXT);
            }

            nameLabel.setText(wi.levelName);

            String modeStr = "Unknown Mode";
            if (wi.rawNbt == null) {
                modeStr = "Load Failure";
            } else {
                switch (wi.gameType) {
                    case 0: modeStr = "Survival"; break;
                    case 1: modeStr = "Creative"; break;
                    case 2: modeStr = "Adventure"; break;
                    case 3: modeStr = "Spectator"; break;
                }
                if (wi.hardcore) {
                    modeStr += " (Hardcore)";
                }
            }
            detailsLabel.setText(wi.directory.getName() + " | " + modeStr);

            return cellPanel;
        }
    }
}
