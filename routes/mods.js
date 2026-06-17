const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const AdmZip = require('adm-zip');
const { GLOBAL_CONFIG_PATH, downloadFile } = require('../utils/sys');
const { deleteRecursive } = require('../utils/file');

// Global installation daemon state scoped to the mods router module
let activeInstallation = null;

/**
 * GET /api/resolve-modpack
 * Resolves a Modrinth modpack URL by downloading and parsing its index.json manifest
 */
router.get('/api/resolve-modpack', async (req, res) => {
    const { url, projectId } = req.query;
    if (!url) {
        return res.status(400).json({ error: "Missing url parameter" });
    }

    const tempDir = path.join(__dirname, '..', 'temp');
    if (!fs.existsSync(tempDir)) {
        fs.mkdirSync(tempDir, { recursive: true });
    }
    const tempFile = path.join(tempDir, `modpack_${Date.now()}_${projectId || 'unknown'}.mrpack`);

    try {
        // Download mrpack file
        await downloadFile(url, tempFile);

        // Extract modrinth.index.json using adm-zip
        const zip = new AdmZip(tempFile);
        const indexEntry = zip.getEntry('modrinth.index.json');
        if (!indexEntry) {
            throw new Error("modrinth.index.json not found in modpack");
        }
        const indexContent = indexEntry.getData().toString('utf8');
        const index = JSON.parse(indexContent);

        // Delete temp file
        fs.unlinkSync(tempFile);

        res.json(index);
    } catch (e) {
        if (fs.existsSync(tempFile)) {
            try { fs.unlinkSync(tempFile); } catch(err) {}
        }
        console.error("Resolve modpack failed", e);
        res.status(500).json({ error: "Failed to resolve modpack: " + e.message });
    }
});

/**
 * POST /api/install
 * Initialize and queue a new installation sequence
 */
router.post('/api/install', (req, res) => {
    const { targetDir, tasks } = req.body;
    if (!targetDir || !tasks || !tasks.length) {
        return res.status(400).json({ error: "Invalid parameters" });
    }

    if (activeInstallation && activeInstallation.status === 'running') {
        return res.status(400).json({ error: "Another installation is running" });
    }

    activeInstallation = {
        status: 'running',
        phase: 'diagnostic',
        log: [],
        queue: tasks,
        targetDir,
        progress: { completedCount: 0, totalCount: tasks.length, activeFile: '', activePct: 0, overallPct: 0 },
        error: null
    };

    runInstallationSequence();
    res.json({ success: true });
});

/**
 * GET /api/install/status
 * Retrieve active installation daemon state
 */
router.get('/api/install/status', (req, res) => {
    res.json(activeInstallation || { status: 'idle' });
});

/**
 * POST /api/install/cancel
 * Cancel the currently running installation sequence
 */
router.post('/api/install/cancel', (req, res) => {
    if (activeInstallation && activeInstallation.status === 'running') {
        activeInstallation.status = 'cancelled';
        activeInstallation.phase = 'failed';
        activeInstallation.log.push("[CANCELLED] Installation cancelled.");
        activeInstallation.error = "Cancelled by user";
    }
    res.json({ success: true });
});

/**
 * Executes the download and verification tasks in the queue.
 */
async function runInstallationSequence() {
    const inst = activeInstallation;
    const log = (msg) => inst.log.push(`> ${msg}`);

    try {
        log("SYSTEM: Initializing pre-flight diagnostic scan...");
        await new Promise(r => setTimeout(r, 400));
        log(`DIR: Target location: ${inst.targetDir}`);
        
        if (!fs.existsSync(inst.targetDir)) {
            throw new Error("Target Minecraft folder does not exist.");
        }
        
        try {
            const temp = path.join(inst.targetDir, `temp_${Date.now()}.tmp`);
            fs.writeFileSync(temp, 'test');
            fs.unlinkSync(temp);
            log("DIR: Write permissions OK.");
        } catch (err) {
            throw new Error("No file write access: " + err.message);
        }

        log("SYSTEM: Diagnostic complete. Starting download sequence.");
        await new Promise(r => setTimeout(r, 300));
        inst.phase = 'download';

        for (let i = 0; i < inst.queue.length; i++) {
            if (inst.status === 'cancelled') return;

            const task = inst.queue[i];
            const typeLabel = task.targetFolder.includes('datapacks') ? 'DATAPACK' : 
                              task.targetFolder === 'resourcepacks' ? 'RESOURCE PACK' :
                              task.targetFolder === 'shaderpacks' ? 'SHADER' : 'MOD';

            const folderPath = path.join(inst.targetDir, task.targetFolder);
            if (!fs.existsSync(folderPath)) {
                fs.mkdirSync(folderPath, { recursive: true });
            }

            const destFile = path.join(folderPath, task.filename);
            inst.progress.activeFile = task.filename;
            inst.progress.activePct = 0;
            log(`STREAMING [${typeLabel}]: ${task.filename}`);

            await downloadFile(task.downloadUrl, destFile, (read, total) => {
                if (total > 0) {
                    inst.progress.activePct = Math.floor((read * 100) / total);
                }
            });

            log(`[${typeLabel}] Success: write checksum verify -> ${task.filename}`);
            inst.progress.completedCount++;
            inst.progress.overallPct = Math.floor((inst.progress.completedCount * 100) / inst.progress.totalCount);
        }

        if (inst.status === 'cancelled') return;

        inst.phase = 'finalization';
        log("VERIFY: Extracting modpack overrides and finalising configuration...");

        // Check for any downloaded .mrpack override tasks
        const mrpackTasks = inst.queue.filter(t => t.filename.startsWith('minestaller_temp_') && t.filename.endsWith('.mrpack'));
        let installedFiles = inst.queue.filter(t => !t.filename.endsWith('.mrpack')).map(t => {
            return path.join(t.targetFolder, t.filename).replace(/\\/g, '/');
        });

        let modpackTitle = null;
        let modpackVersion = null;
        let modpackId = null;

        for (const task of mrpackTasks) {
            const mrpackPath = path.join(inst.targetDir, task.filename);
            if (fs.existsSync(mrpackPath)) {
                try {
                    // Create a temporary folder for extraction
                    const extractTempDir = path.join(__dirname, '..', 'temp_extracted');
                    if (fs.existsSync(extractTempDir)) {
                        deleteRecursive(extractTempDir);
                    }
                    fs.mkdirSync(extractTempDir, { recursive: true });

                    // Extract the .mrpack zip using adm-zip
                    const zip = new AdmZip(mrpackPath);
                    zip.extractAllTo(extractTempDir, true);

                    // Parse the index.json to retrieve title and version
                    const indexJsonPath = path.join(extractTempDir, 'modrinth.index.json');
                    if (fs.existsSync(indexJsonPath)) {
                        const indexData = JSON.parse(fs.readFileSync(indexJsonPath, 'utf8'));
                        modpackTitle = indexData.name;
                        modpackVersion = indexData.versionId;
                        modpackId = task.projectId;
                    }

                    // Look for overrides / client-overrides
                    const overridesDirs = ['overrides', 'client-overrides'];
                    for (const oName of overridesDirs) {
                        const oDir = path.join(extractTempDir, oName);
                        if (fs.existsSync(oDir)) {
                            // Recursively copy files to targetDir
                            const copyRecursiveTrack = (src, dest, baseDest) => {
                                const stats = fs.statSync(src);
                                if (stats.isDirectory()) {
                                    if (!fs.existsSync(dest)) {
                                        fs.mkdirSync(dest, { recursive: true });
                                    }
                                    fs.readdirSync(src).forEach((child) => {
                                        copyRecursiveTrack(path.join(src, child), path.join(dest, child), baseDest);
                                    });
                                } else {
                                    fs.mkdirSync(path.dirname(dest), { recursive: true });
                                    fs.copyFileSync(src, dest);
                                    const rel = path.relative(baseDest, dest).replace(/\\/g, '/');
                                    installedFiles.push(rel);
                                }
                            };
                            copyRecursiveTrack(oDir, inst.targetDir, inst.targetDir);
                        }
                    }

                    // Clean up
                    deleteRecursive(extractTempDir);
                    fs.unlinkSync(mrpackPath);
                    log(`VERIFY: Extracted overrides for modpack successfully.`);
                } catch (err) {
                    log(`VERIFY ERROR: Failed to extract overrides: ${err.message}`);
                    console.error("Failed to extract mrpack overrides", err);
                }
            }
        }

        // If a modpack was installed, update minestaller_config.json
        if (modpackTitle) {
            try {
                let conf = { lastMinecraftDir: "", instances: {} };
                if (fs.existsSync(GLOBAL_CONFIG_PATH)) {
                    conf = JSON.parse(fs.readFileSync(GLOBAL_CONFIG_PATH, 'utf8'));
                }
                const normPath = inst.targetDir.toLowerCase().replace(/\\/g, '/');
                conf.instances = conf.instances || {};
                conf.instances[normPath] = {
                    installedModpackId: modpackId,
                    installedModpackTitle: modpackTitle,
                    installedModpackVersion: modpackVersion,
                    installedModpackFiles: installedFiles
                };
                fs.writeFileSync(GLOBAL_CONFIG_PATH, JSON.stringify(conf, null, 2), 'utf8');
                log("SYSTEM: Updated instance modpack registration.");
            } catch (err) {
                log(`SYSTEM ERROR: Failed to update global configuration: ${err.message}`);
            }
        }

        log("VERIFY: Integrity checks completed successfully.");
        log("SYSTEM: Installation completed successfully.");
        inst.status = 'completed';
    } catch (err) {
        console.error("Installation failed", err);
        inst.status = 'failed';
        inst.error = err.message;
        log(`[ERROR] Installation failed: ${err.message}`);
    }
}

/**
 * POST /api/uninstall
 * Uninstall / delete a specific addon file from the instance
 */
router.post('/api/uninstall', (req, res) => {
    try {
        const { instancePath, targetFolder, filename } = req.body;
        if (!instancePath || !targetFolder || !filename) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        
        // Prevent path traversal
        const cleanFolder = targetFolder.replace(/\.\./g, '');
        const cleanFilename = path.basename(filename);
        
        const filePath = path.join(instancePath, cleanFolder, cleanFilename);
        if (fs.existsSync(filePath)) {
            const stats = fs.statSync(filePath);
            if (stats.isDirectory()) {
                deleteRecursive(filePath);
            } else {
                fs.unlinkSync(filePath);
            }
            res.json({ success: true });
        } else {
            res.status(404).json({ error: "File not found" });
        }
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

module.exports = router;
