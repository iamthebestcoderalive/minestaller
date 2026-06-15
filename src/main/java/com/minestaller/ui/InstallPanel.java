package com.minestaller.ui;

import com.minestaller.service.DownloadManager.InstallTask;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class InstallPanel extends JPanel {

    private final MainFrame mainFrame;
    private JTextPane fileListArea;
    private JButton authorizeButton;
    private List<InstallTask> currentTasks;
    private String targetDir;

    private JToggleButton btnAll;
    private JToggleButton btnMods;
    private JToggleButton btnPacks;
    private JToggleButton btnShaders;
    private JPanel filterBar;

    public InstallPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout(0, 10));
        setBackground(StyleHelper.COLOR_BG);

        initUI();
    }

    private void initUI() {
        // Friendly Header
        JPanel headerPanel = StyleHelper.createGridPanel("Confirm Installation");
        JLabel headerLabel = StyleHelper.createLabel("The following files will be added to your Minecraft folder:", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        headerPanel.add(headerLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Center Panel containing filters and text pane
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBackground(StyleHelper.COLOR_BG);

        // Filter Toggle Bar
        filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        filterBar.setBackground(StyleHelper.COLOR_BG_PANEL);
        filterBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, StyleHelper.COLOR_GRID));

        JLabel filterLabel = StyleHelper.createLabel("Filter List: ", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_BODY);
        filterBar.add(filterLabel);

        btnAll = new JToggleButton("Show All");
        btnMods = new JToggleButton("Mods");
        btnPacks = new JToggleButton("Resource Packs");
        btnShaders = new JToggleButton("Shaders");

        ButtonGroup group = new ButtonGroup();
        group.add(btnAll);
        group.add(btnMods);
        group.add(btnPacks);
        group.add(btnShaders);

        btnAll.setSelected(true);

        styleToggleButton(btnAll, "all");
        styleToggleButton(btnMods, "mods");
        styleToggleButton(btnPacks, "resourcepacks");
        styleToggleButton(btnShaders, "shaderpacks");

        filterBar.add(btnAll);
        filterBar.add(btnMods);
        filterBar.add(btnPacks);
        filterBar.add(btnShaders);

        centerPanel.add(filterBar, BorderLayout.NORTH);

        // Scrollable html text pane
        fileListArea = new JTextPane();
        fileListArea.setContentType("text/html");
        fileListArea.setBackground(StyleHelper.COLOR_BG_PANEL);
        fileListArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(fileListArea);
        StyleHelper.styleScrollPane(scrollPane);
        
        JPanel listFrame = StyleHelper.createGridPanel("Files to Install");
        listFrame.add(scrollPane, BorderLayout.CENTER);
        centerPanel.add(listFrame, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // Controls bar
        JPanel controls = new JPanel(new BorderLayout());
        controls.setBackground(StyleHelper.COLOR_BG);
        controls.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        JButton backButton = StyleHelper.createCyberButton("Go Back", false);
        backButton.addActionListener(e -> mainFrame.goToStep("search"));
        controls.add(backButton, BorderLayout.WEST);
        
        authorizeButton = StyleHelper.createCyberButton("Install Now", true);
        authorizeButton.addActionListener(e -> authorizeDownloads());
        controls.add(authorizeButton, BorderLayout.EAST);

        add(controls, BorderLayout.SOUTH);
    }

    private void styleToggleButton(JToggleButton btn, String filterType) {
        btn.setFont(StyleHelper.FONT_MONO_SMALL);
        btn.setBackground(StyleHelper.COLOR_BG_PANEL);
        btn.setForeground(StyleHelper.COLOR_CYAN);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
        ));

        btn.addActionListener(e -> updateFileList(filterType));
    }

    public void showConfirmation(String targetDir, List<InstallTask> tasks) {
        this.targetDir = targetDir;
        this.currentTasks = tasks;

        // Hide filter bar if it's a single file download
        if (tasks != null && tasks.size() <= 1) {
            filterBar.setVisible(false);
        } else {
            filterBar.setVisible(true);
        }

        // Reset filter selection to All
        btnAll.setSelected(true);
        updateFileList("all");
    }

    private void updateFileList(String filter) {
        if (currentTasks == null) return;
        boolean showType = (currentTasks.size() > 1);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:monospace; font-size:12px; color:#D1D9E6; background-color:#070A0F; margin:5px;'>");

        for (InstallTask t : currentTasks) {
            String category = "other";
            if ("mods".equalsIgnoreCase(t.targetFolder) || t.filename.endsWith(".jar")) {
                category = "mods";
            } else if ("resourcepacks".equalsIgnoreCase(t.targetFolder)) {
                category = "resourcepacks";
            } else if ("shaderpacks".equalsIgnoreCase(t.targetFolder)) {
                category = "shaderpacks";
            }

            // Apply filter
            if (!"all".equalsIgnoreCase(filter) && !category.equalsIgnoreCase(filter)) {
                continue;
            }

            // Categorized details
            String prefix = "";
            String color = "#8892B0"; // gray
            if ("mods".equals(category)) {
                if (showType) prefix = "<span style='color:#00F0FF; font-weight:bold;'>[MOD]</span> ";
                color = "#00F0FF"; // cyan
            } else if ("resourcepacks".equals(category)) {
                if (showType) prefix = "<span style='color:#FFB300; font-weight:bold;'>[RESOURCE PACK]</span> ";
                color = "#FFB300"; // amber
            } else if ("shaderpacks".equals(category)) {
                if (showType) prefix = "<span style='color:#FF00FF; font-weight:bold;'>[SHADER]</span> ";
                color = "#FF00FF"; // magenta
            } else {
                if (showType) prefix = "<span style='color:#8892B0; font-weight:bold;'>[CONFIG]</span> ";
            }

            sb.append("<div style='margin-bottom:4px; line-height: 1.2;'> &gt; ")
              .append(prefix)
              .append("<span style='color:").append(showType ? "#D1D9E6" : color).append(";'>").append(t.title)
              .append(" (").append(t.filename).append(")</span></div>");
        }

        sb.append("</body></html>");
        fileListArea.setText(sb.toString());
        fileListArea.setCaretPosition(0);
    }

    private void authorizeDownloads() {
        if (currentTasks == null || currentTasks.isEmpty()) return;

        // 1. Go back to search step (Modpack page)
        mainFrame.goToStep("search");

        // 2. Open the draggable popup dialog in the middle
        DownloadProgressDialog progressDialog = new DownloadProgressDialog(mainFrame, targetDir, currentTasks);
        progressDialog.setVisible(true);
    }
}
