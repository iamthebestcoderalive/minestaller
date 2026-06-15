package com.minestaller.ui;

import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {

    private final MainFrame mainFrame;
    private JLabel pathValueLabel;
    private JLabel versionValueLabel;
    private JLabel loaderValueLabel;

    public DashboardPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());
        setBackground(StyleHelper.COLOR_BG);

        initUI();
    }

    private void initUI() {
        // Main grid panel for content
        JPanel contentContainer = StyleHelper.createGridPanel("Minecraft Instance Dashboard");
        contentContainer.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title/Welcome
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel welcomeLabel = StyleHelper.createLabel("INSTANCE DETAILS", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_TITLE);
        welcomeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        contentContainer.add(welcomeLabel, gbc);

        // Separator
        gbc.gridy = 1;
        JSeparator sep = new JSeparator();
        sep.setForeground(StyleHelper.COLOR_GRID);
        contentContainer.add(sep, gbc);

        // Instance Path Row
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.3;
        JLabel pathLabel = StyleHelper.createLabel("Instance Path:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_HEADER);
        contentContainer.add(pathLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        pathValueLabel = StyleHelper.createLabel("--", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_BODY);
        contentContainer.add(pathValueLabel, gbc);

        // Minecraft Version Row
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.3;
        JLabel versionLabel = StyleHelper.createLabel("Game Version:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_HEADER);
        contentContainer.add(versionLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        versionValueLabel = StyleHelper.createLabel("--", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_BODY);
        contentContainer.add(versionValueLabel, gbc);

        // Mod Loader Row
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.3;
        JLabel loaderLabel = StyleHelper.createLabel("Mod Loader:", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_HEADER);
        contentContainer.add(loaderLabel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        loaderValueLabel = StyleHelper.createLabel("--", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_BODY);
        contentContainer.add(loaderValueLabel, gbc);

        // Spacer before buttons
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        contentContainer.add(Box.createVerticalStrut(20), gbc);

        // Actions container for primary cyber buttons
        gbc.gridy = 6;
        JPanel actionButtonPanel = new JPanel(new GridLayout(1, 2, 20, 0));
        actionButtonPanel.setBackground(StyleHelper.COLOR_BG_PANEL);

        JButton searchButton = StyleHelper.createCyberButton(
                "<html><center><font face='monospace' size='7'>🔍</font><br><br><font face='monospace' size='5'><b>SEARCH MODRINTH</b></font></center></html>",
                true
        );
        searchButton.setPreferredSize(new Dimension(320, 200));
        searchButton.addActionListener(e -> {
            mainFrame.goToStep("search");
        });

        JButton worldsButton = StyleHelper.createCyberButton(
                "<html><center><font face='monospace' size='7'>🌍</font><br><br><font face='monospace' size='5'><b>MANAGE WORLDS</b></font></center></html>",
                false
        );
        worldsButton.setPreferredSize(new Dimension(320, 200));
        worldsButton.addActionListener(e -> {
            mainFrame.goToStep("worldManager");
        });

        actionButtonPanel.add(searchButton);
        actionButtonPanel.add(worldsButton);
        contentContainer.add(actionButtonPanel, gbc);

        add(contentContainer, BorderLayout.CENTER);

        // Bottom Controls Panel (Back/Cancel)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.setBackground(StyleHelper.COLOR_BG);
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        JButton backButton = StyleHelper.createCyberButton("Go Back", false);
        backButton.addActionListener(e -> {
            mainFrame.goToStep("boot");
        });
        bottomPanel.add(backButton);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void updateDetails() {
        pathValueLabel.setText(mainFrame.getTargetMinecraftDir());
        versionValueLabel.setText(mainFrame.getTargetMcVersion());
        loaderValueLabel.setText(mainFrame.getTargetLoader());
    }
}
