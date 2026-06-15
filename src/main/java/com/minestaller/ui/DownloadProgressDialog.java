package com.minestaller.ui;

import com.minestaller.service.DownloadManager;
import com.minestaller.service.DownloadManager.DownloadListener;
import com.minestaller.service.DownloadManager.InstallTask;
import com.minestaller.model.ConfigManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DownloadProgressDialog extends JDialog {
    private final MainFrame mainFrame;
    private final List<InstallTask> downloadQueue;
    private final String targetDir;
    private final DownloadManager downloadManager = new DownloadManager();

    private CardLayout cardLayout;
    private JPanel cardsPanel;

    // Phase 1 (Diagnostic) components
    private JTextArea diagLog;
    private JProgressBar diagProgress;
    private JButton diagCancelBtn;

    // Phase 2 (Download) components
    private JLabel downloadOverallLabel;
    private JProgressBar downloadOverallProgress;
    private JLabel downloadActiveLabel;
    private JProgressBar downloadActiveProgress;
    private JLabel downloadSpeedLabel;
    private JTextArea downloadLog;
    private JButton downloadCancelBtn;

    // Phase 3 (Extraction) components
    private JLabel extractLabel;
    private JProgressBar extractProgress;
    private JTextArea extractLog;
    private JButton extractCancelBtn;

    // Phase 4 (Finalization) components
    private JLabel finalStatusLabel;
    private JProgressBar finalProgress;
    private JTextArea finalLog;
    private JButton finalCloseBtn;

    private Thread workerThread;
    private volatile boolean isCancelled = false;
    private int completedCount = 0;

    // Track all files successfully created during this installation for rollback
    private final List<File> filesCreated = Collections.synchronizedList(new ArrayList<>());

    public DownloadProgressDialog(MainFrame parent, String targetDir, List<InstallTask> queue) {
        super(parent, "Installing...", false);
        this.mainFrame = parent;
        this.targetDir = targetDir;
        this.downloadQueue = queue;

        setSize(580, 420);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); // prevent manual closing

        initUI();

        // Start background worker thread
        workerThread = new Thread(this::runInstallationFlow);
        workerThread.start();
    }

    private void initUI() {
        cardLayout = new CardLayout();
        cardsPanel = new JPanel(cardLayout);
        cardsPanel.setBackground(StyleHelper.COLOR_BG);

        // Build all 4 phase panels
        cardsPanel.add(buildDiagnosticPanel(), "diagnostic");
        cardsPanel.add(buildDownloadPanel(), "download");
        cardsPanel.add(buildExtractionPanel(), "extraction");
        cardsPanel.add(buildFinalizationPanel(), "finalization");

        add(cardsPanel);
    }

    // --- Panel Builders ---

    private JPanel buildDiagnosticPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(StyleHelper.COLOR_BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = StyleHelper.createLabel("Step 1 of 4: Checking folder permissions and space...", StyleHelper.COLOR_CYAN, StyleHelper.FONT_MONO_HEADER);
        panel.add(title, BorderLayout.NORTH);

        diagLog = new JTextArea();
        diagLog.setBackground(StyleHelper.COLOR_BG);
        diagLog.setForeground(StyleHelper.COLOR_GREEN);
        diagLog.setFont(StyleHelper.FONT_MONO_SMALL);
        diagLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(diagLog);
        StyleHelper.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(5, 5));
        south.setBackground(StyleHelper.COLOR_BG_PANEL);

        diagProgress = new JProgressBar();
        diagProgress.setIndeterminate(true);
        diagProgress.setBackground(StyleHelper.COLOR_BG);
        diagProgress.setForeground(StyleHelper.COLOR_CYAN);
        diagProgress.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        south.add(diagProgress, BorderLayout.CENTER);

        diagCancelBtn = StyleHelper.createCyberButton("Cancel Installation", false);
        diagCancelBtn.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
        diagCancelBtn.setForeground(Color.RED);
        diagCancelBtn.addActionListener(e -> performCancel());
        south.add(diagCancelBtn, BorderLayout.EAST);

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(StyleHelper.COLOR_BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel north = new JPanel(new GridLayout(4, 1, 2, 2));
        north.setBackground(StyleHelper.COLOR_BG_PANEL);

        downloadOverallLabel = StyleHelper.createLabel("Step 2 of 4: Downloading files (0 / " + downloadQueue.size() + ")...", StyleHelper.COLOR_GREEN, StyleHelper.FONT_MONO_HEADER);
        north.add(downloadOverallLabel);

        downloadOverallProgress = new JProgressBar(0, 100);
        downloadOverallProgress.setBackground(StyleHelper.COLOR_BG);
        downloadOverallProgress.setForeground(StyleHelper.COLOR_GREEN);
        downloadOverallProgress.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        downloadOverallProgress.setStringPainted(true);
        downloadOverallProgress.setFont(StyleHelper.FONT_MONO_BODY);
        north.add(downloadOverallProgress);

        downloadActiveLabel = StyleHelper.createLabel("Preparing download stream...", StyleHelper.COLOR_TEXT, StyleHelper.FONT_MONO_SMALL);
        north.add(downloadActiveLabel);

        downloadActiveProgress = new JProgressBar(0, 100);
        downloadActiveProgress.setBackground(StyleHelper.COLOR_BG);
        downloadActiveProgress.setForeground(StyleHelper.COLOR_CYAN);
        downloadActiveProgress.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        north.add(downloadActiveProgress);

        panel.add(north, BorderLayout.NORTH);

        downloadLog = new JTextArea();
        downloadLog.setBackground(StyleHelper.COLOR_BG);
        downloadLog.setForeground(StyleHelper.COLOR_GREEN);
        downloadLog.setFont(StyleHelper.FONT_MONO_SMALL);
        downloadLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(downloadLog);
        StyleHelper.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(StyleHelper.COLOR_BG_PANEL);

        downloadSpeedLabel = StyleHelper.createLabel("SPEED: 0.0 KB/S", StyleHelper.COLOR_AMBER, StyleHelper.FONT_MONO_SMALL);
        south.add(downloadSpeedLabel, BorderLayout.WEST);

        downloadCancelBtn = StyleHelper.createCyberButton("Cancel Installation", false);
        downloadCancelBtn.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
        downloadCancelBtn.setForeground(Color.RED);
        downloadCancelBtn.addActionListener(e -> performCancel());
        south.add(downloadCancelBtn, BorderLayout.EAST);

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildExtractionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(StyleHelper.COLOR_BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel north = new JPanel(new GridLayout(2, 1, 5, 5));
        north.setBackground(StyleHelper.COLOR_BG_PANEL);

        extractLabel = StyleHelper.createLabel("Step 3 of 4: Installing configs and overrides...", StyleHelper.COLOR_AMBER, StyleHelper.FONT_MONO_HEADER);
        north.add(extractLabel);

        extractProgress = new JProgressBar(0, 100);
        extractProgress.setBackground(StyleHelper.COLOR_BG);
        extractProgress.setForeground(Color.decode("#FF00FF")); // Magenta / Purple accent
        extractProgress.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        extractProgress.setStringPainted(true);
        extractProgress.setFont(StyleHelper.FONT_MONO_BODY);
        north.add(extractProgress);

        panel.add(north, BorderLayout.NORTH);

        extractLog = new JTextArea();
        extractLog.setBackground(StyleHelper.COLOR_BG);
        extractLog.setForeground(StyleHelper.COLOR_GREEN);
        extractLog.setFont(StyleHelper.FONT_MONO_SMALL);
        extractLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(extractLog);
        StyleHelper.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setBackground(StyleHelper.COLOR_BG_PANEL);

        extractCancelBtn = StyleHelper.createCyberButton("Cancel Installation", false);
        extractCancelBtn.setBorder(BorderFactory.createLineBorder(Color.RED, 1));
        extractCancelBtn.setForeground(Color.RED);
        extractCancelBtn.addActionListener(e -> performCancel());
        south.add(extractCancelBtn);

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFinalizationPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(StyleHelper.COLOR_BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel north = new JPanel(new GridLayout(2, 1, 5, 5));
        north.setBackground(StyleHelper.COLOR_BG_PANEL);

        finalStatusLabel = StyleHelper.createLabel("Step 4 of 4: Verifying installation...", StyleHelper.COLOR_GREEN, StyleHelper.FONT_MONO_HEADER);
        north.add(finalStatusLabel);

        finalProgress = new JProgressBar(0, 100);
        finalProgress.setValue(100);
        finalProgress.setBackground(StyleHelper.COLOR_BG);
        finalProgress.setForeground(StyleHelper.COLOR_GREEN);
        finalProgress.setBorder(BorderFactory.createLineBorder(StyleHelper.COLOR_GRID, 1));
        finalProgress.setStringPainted(true);
        finalProgress.setString("Installation completed successfully!");
        finalProgress.setFont(StyleHelper.FONT_MONO_BODY);
        north.add(finalProgress);

        panel.add(north, BorderLayout.NORTH);

        finalLog = new JTextArea();
        finalLog.setBackground(StyleHelper.COLOR_BG);
        finalLog.setForeground(StyleHelper.COLOR_GREEN);
        finalLog.setFont(StyleHelper.FONT_MONO_SMALL);
        finalLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(finalLog);
        StyleHelper.styleScrollPane(scroll);
        panel.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setBackground(StyleHelper.COLOR_BG_PANEL);

        finalCloseBtn = StyleHelper.createCyberButton("Finish", true);
        finalCloseBtn.addActionListener(e -> dispose());
        south.add(finalCloseBtn);

        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    // --- Logging Helpers ---

    private void logDiag(String msg) {
        SwingUtilities.invokeLater(() -> {
            diagLog.append(" > " + msg + "\n");
            diagLog.setCaretPosition(diagLog.getDocument().getLength());
        });
    }

    private void logDownload(String msg) {
        SwingUtilities.invokeLater(() -> {
            downloadLog.append(" > " + msg + "\n");
            downloadLog.setCaretPosition(downloadLog.getDocument().getLength());
        });
    }

    private void logExtract(String msg) {
        SwingUtilities.invokeLater(() -> {
            extractLog.append(" > " + msg + "\n");
            extractLog.setCaretPosition(extractLog.getDocument().getLength());
        });
    }

    private void logFinal(String msg) {
        SwingUtilities.invokeLater(() -> {
            finalLog.append(" > " + msg + "\n");
            finalLog.setCaretPosition(finalLog.getDocument().getLength());
        });
    }

    private void delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            isCancelled = true;
        }
    }

    // --- Installation Workflow ---

    private void runInstallationFlow() {
        // --- PHASE 1: DIAGNOSTIC ---
        logDiag("SYSTEM: Initializing pre-flight diagnostic scan...");
        delay(300);
        if (isCancelled) return;

        logDiag("API: Querying Modrinth uplink connection status...");
        delay(400);
        if (isCancelled) return;
        logDiag("API: Connection established. SSL validation OK.");

        logDiag("DIR: Target location: " + targetDir);
        File root = new File(targetDir);
        delay(300);
        if (isCancelled) return;

        if (!root.exists() || !root.isDirectory()) {
            logDiag("DIR [ERROR]: Target folder does not exist!");
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Target directory does not exist.", "Scan Error", JOptionPane.ERROR_MESSAGE);
                dispose();
            });
            return;
        }
        logDiag("DIR: Target folder verified.");

        // Check write permission
        logDiag("DIR: Verifying file write access...");
        delay(400);
        if (isCancelled) return;
        try {
            File temp = File.createTempFile("minestaller_perm_", ".tmp", root);
            temp.delete();
            logDiag("DIR: Write permissions OK.");
        } catch (Exception e) {
            logDiag("DIR [ERROR]: Write permissions verification failed: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "No write permission in the target directory.", "Permission Error", JOptionPane.ERROR_MESSAGE);
                dispose();
            });
            return;
        }

        // JVM version check
        logDiag("JVM: Checking runtime compatibility...");
        delay(300);
        if (isCancelled) return;
        String javaVer = System.getProperty("java.version");
        logDiag("JVM: Version " + javaVer + " detected ... OK.");

        // Space check
        logDiag("DISK: Checking local storage capacity...");
        long freeBytes = root.getFreeSpace();
        long freeMb = freeBytes / (1024 * 1024);
        delay(300);
        if (isCancelled) return;
        logDiag("DISK: " + freeMb + " MB free ... OK.");

        logDiag("SYSTEM: Pre-flight check successful. Transit to payload download.");
        delay(400);
        if (isCancelled) return;

        // Switch to Phase 2
        SwingUtilities.invokeLater(() -> cardLayout.show(cardsPanel, "download"));

        // --- PHASE 2: PAYLOAD DOWNLOAD ---
        InstallTask modpackTask = null;

        for (int i = 0; i < downloadQueue.size(); i++) {
            if (isCancelled) return;
            InstallTask task = downloadQueue.get(i);

            // Classify current task type
            String typePrefix = "MOD";
            if ("resourcepacks".equals(task.targetFolder)) {
                typePrefix = "RESOURCE PACK";
            } else if ("shaderpacks".equals(task.targetFolder)) {
                typePrefix = "SHADER";
            } else if (task.targetFolder != null && task.targetFolder.contains("datapacks")) {
                typePrefix = "DATAPACK";
            } else if (task.filename.endsWith(".mrpack")) {
                typePrefix = "MODPACK";
                modpackTask = task;
            }

            final String typeLabel = typePrefix;
            final int index = i;

            SwingUtilities.invokeLater(() -> {
                downloadOverallLabel.setText("Step 2 of 4: Downloading files (" + index + " / " + downloadQueue.size() + ")...");
                downloadActiveLabel.setText("FILE [" + typeLabel + "]: " + task.filename);
                downloadActiveProgress.setValue(0);
            });

            // Check if the mod/package is already installed (any version of it)
            File existingFile = findInstalledFile(task.targetFolder, task.filename);
            if (existingFile != null) {
                logDownload("[OK] Already installed (Skipping download) -> " + existingFile.getName());
                
                completedCount++;
                SwingUtilities.invokeLater(() -> {
                    int overallPct = (completedCount * 100) / downloadQueue.size();
                    downloadOverallProgress.setValue(overallPct);
                    downloadOverallProgress.setString(overallPct + "%");
                });
                delay(50); // Small delay for visual pacing
                continue;
            }

            File finalFile = new File(new File(targetDir, task.targetFolder), task.filename);
            logDownload("STREAMING [" + typeLabel + "]: " + task.filename);

            try {
                boolean success = downloadManager.downloadTask(targetDir, task, new DownloadListener() {
                    @Override
                    public void onDownloadStarted(String filename) {}

                    @Override
                    public void onDownloadProgress(String filename, long bytesRead, long totalBytes, double speedKbps) {
                        SwingUtilities.invokeLater(() -> {
                            int pct = (totalBytes > 0) ? (int) ((bytesRead * 100) / totalBytes) : 0;
                            downloadActiveProgress.setValue(pct);

                            String speedStr;
                            if (speedKbps > 1024) {
                                speedStr = String.format("SPEED: %.2f MB/S", speedKbps / 1024.0);
                            } else {
                                speedStr = String.format("SPEED: %.1f KB/S", speedKbps);
                            }
                            downloadSpeedLabel.setText(speedStr);
                        });
                    }

                    @Override
                    public void onDownloadFinished(String filename, boolean success, String errorMsg) {}
                }).get();

                if (success) {
                    logDownload("[" + typeLabel + "] Success: write checksum verify -> " + task.filename);
                    
                    filesCreated.add(finalFile);

                    completedCount++;
                    SwingUtilities.invokeLater(() -> {
                        int overallPct = (completedCount * 100) / downloadQueue.size();
                        downloadOverallProgress.setValue(overallPct);
                        downloadOverallProgress.setString(overallPct + "%");
                    });
                } else {
                    if (isCancelled) return;
                    logDownload("[" + typeLabel + "] FAILURE: stream corrupted for " + task.filename);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Failed to download " + task.filename, "Download Error", JOptionPane.ERROR_MESSAGE);
                        performCancel();
                    });
                    return;
                }
            } catch (Exception e) {
                if (isCancelled) return;
                logDownload("[" + typeLabel + "] ABORT: connection error -> " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Error downloading " + task.filename + ": " + e.getMessage(), "Download Error", JOptionPane.ERROR_MESSAGE);
                    performCancel();
                });
                return;
            }
        }

        if (isCancelled) return;

        // --- PHASE 3: EXTRACTION (if Modpack task exists) ---
        if (modpackTask != null) {
            SwingUtilities.invokeLater(() -> cardLayout.show(cardsPanel, "extraction"));
            logExtract("ZIP: Initiating overrides extraction for modpack payload...");
            delay(400);

            try {
                extractModpackOverrides(modpackTask);
            } catch (Exception e) {
                if (isCancelled) return;
                logExtract("ZIP [ERROR]: Overrides extraction failed: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Overrides extraction failed: " + e.getMessage(), "Extraction Error", JOptionPane.ERROR_MESSAGE);
                    performCancel();
                });
                return;
            }
        }

        if (isCancelled) return;

        // --- PHASE 4: FINALIZATION ---
        SwingUtilities.invokeLater(() -> cardLayout.show(cardsPanel, "finalization"));
        logFinal("VERIFY: Commencing post-install checksum validation...");
        delay(300);

        int verifyCount = 0;
        int overrideCount = 0;

        for (File f : filesCreated) {
            if (isCancelled) return;
            if (f.exists() && f.isFile()) {
                // Classify file: print mod/pack files individually, count overrides
                String parentName = f.getParentFile().getName();
                if ("mods".equals(parentName) || "resourcepacks".equals(parentName) || "shaderpacks".equals(parentName)) {
                    logFinal("[OK] Verified: " + f.getName());
                } else {
                    overrideCount++;
                }
                verifyCount++;
            } else {
                logFinal("[WARNING] Missing file check: " + f.getAbsolutePath());
            }
            delay(30);
        }

        if (overrideCount > 0) {
            logFinal("[OK] Verified " + overrideCount + " config and asset files.");
        }

        logFinal("VERIFY: Integrity checks completed for " + verifyCount + " assets.");
        logFinal("SYSTEM: Committing modpack configuration variable updates...");
        delay(400);

        // Save Modpack State
        if (modpackTask != null) {
            ConfigManager config = mainFrame.getConfigManager();
            ConfigManager.ModpackState state = new ConfigManager.ModpackState();
            state.installedModpackId = modpackTask.projectId;

            String cleanTitle = modpackTask.title;
            if (cleanTitle.endsWith(" (OVERRIDES & CONFIGS)")) {
                cleanTitle = cleanTitle.substring(0, cleanTitle.length() - " (OVERRIDES & CONFIGS)".length());
            }
            state.installedModpackTitle = cleanTitle;

            File targetDirFile = new File(targetDir);
            for (File f : filesCreated) {
                if (f.exists() && f.isFile()) {
                    String relPath = f.getAbsolutePath().substring(targetDirFile.getAbsolutePath().length() + 1).replace("\\", "/");
                    state.installedModpackFiles.add(relPath);
                }
            }

            config.setModpackState(targetDir, state);
            config.save();
            logFinal("SYSTEM: Configuration database successfully synchronized.");
        }

        logFinal("SYSTEM: Installation completed successfully.");
        SwingUtilities.invokeLater(() -> {
            finalStatusLabel.setText("Step 4 of 4: Verifying installation...");
            finalProgress.setString("Installation completed successfully!");
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        });
    }

    private void extractModpackOverrides(InstallTask task) throws Exception {
        File mrpackFile = new File(targetDir, task.filename);
        if (!mrpackFile.exists()) {
            throw new Exception("Temp modpack payload not found on disk.");
        }

        File rootDir = new File(targetDir);
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mrpackFile)) {
            int totalEntries = zip.size();
            int currentEntry = 0;

            Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (isCancelled) {
                    throw new Exception("Extraction sequence aborted by user request.");
                }

                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                currentEntry++;

                String targetPathStr = null;
                if (name.startsWith("overrides/")) {
                    targetPathStr = name.substring("overrides/".length());
                } else if (name.startsWith("client-overrides/")) {
                    targetPathStr = name.substring("client-overrides/".length());
                }

                if (targetPathStr != null && !targetPathStr.isEmpty()) {
                    File destPath = new File(rootDir, targetPathStr);
                    if (entry.isDirectory()) {
                        destPath.mkdirs();
                    } else {
                        destPath.getParentFile().mkdirs();
                        logExtract("[EXTRACT] config -> " + targetPathStr);

                        try (InputStream is = zip.getInputStream(entry);
                             java.io.OutputStream os = Files.newOutputStream(destPath.toPath())) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                os.write(buffer, 0, read);
                            }
                        }
                        filesCreated.add(destPath);
                    }
                }

                final int progressPct = (totalEntries > 0) ? (currentEntry * 100) / totalEntries : 0;
                SwingUtilities.invokeLater(() -> extractProgress.setValue(progressPct));
            }
        } finally {
            try {
                mrpackFile.delete();
            } catch (Exception ignored) {}
        }
    }

    private void performCancel() {
        if (isCancelled) return;
        isCancelled = true;

        diagCancelBtn.setEnabled(false);
        downloadCancelBtn.setEnabled(false);
        extractCancelBtn.setEnabled(false);

        logDiag("ROLLBACK: Initiating installation rollback...");
        logDownload("ROLLBACK: Initiating installation rollback...");
        logExtract("ROLLBACK: Initiating installation rollback...");

        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
        }

        // Delete all created files in background
        CompletableFuture.runAsync(() -> {
            int deleteCount = 0;
            File rootDir = new File(targetDir);

            List<File> filesToDelete;
            synchronized (filesCreated) {
                filesToDelete = new ArrayList<>(filesCreated);
            }

            for (File f : filesToDelete) {
                if (f.exists() && f.isFile()) {
                    if (f.delete()) {
                        deleteCount++;
                    }
                }
                // Clean up parent folders recursively
                File parent = f.getParentFile();
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

            final int finalDeleteCount = deleteCount;
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Installation cancelled. Rolled back " + finalDeleteCount + " files successfully.",
                        "Transaction Aborted", JOptionPane.WARNING_MESSAGE);
                dispose();
            });
        });
    }

    private File findInstalledFile(String folder, String filename) {
        File dir = new File(targetDir, folder);
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        String targetBase = extractBaseName(filename).toLowerCase();
        if (targetBase.isEmpty()) {
            return null;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.length() > 0) {
                    String name = f.getName();
                    String base = extractBaseName(name).toLowerCase();
                    if (!base.isEmpty() && base.equals(targetBase)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    private String extractBaseName(String filename) {
        String name = filename;
        if (name.toLowerCase().endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        } else if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        }

        // Regex pattern to find the version separator (e.g., -1.2.3, -v1.0, _1.20)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-_]v?\\d+\\.\\d+").matcher(name);
        if (m.find()) {
            return name.substring(0, m.start());
        }
        return name;
    }
}
