const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const AdmZip = require('adm-zip');
const { deleteRecursive } = require('../utils/file');

/**
 * POST /api/backup/create
 * Create a new timestamped ZIP backup of chosen components
 */
router.post('/api/backup/create', (req, res) => {
    try {
        const { instancePath, options } = req.body;
        if (!instancePath || !fs.existsSync(instancePath)) {
            return res.status(400).json({ error: "Invalid instance path" });
        }
        if (!options) {
            return res.status(400).json({ error: "Missing options" });
        }

        const backupsDir = path.join(path.dirname(instancePath), 'minestaller_backups');
        if (!fs.existsSync(backupsDir)) {
            fs.mkdirSync(backupsDir, { recursive: true });
        }

        const instanceName = path.basename(instancePath);
        const dateStr = new Date().toISOString().split('T')[0];
        const timeStr = new Date().toTimeString().split(' ')[0].replace(/:/g, '-');
        const filename = `backup_${instanceName}_${dateStr}_${timeStr}.zip`;
        const zipPath = path.join(backupsDir, filename);

        const zip = new AdmZip();

        // Add selected folders
        const foldersToBackup = [];
        if (options.mods) foldersToBackup.push('mods');
        if (options.saves) foldersToBackup.push('saves');
        if (options.resourcepacks) foldersToBackup.push('resourcepacks');
        if (options.shaders) foldersToBackup.push('shaderpacks');

        for (const folder of foldersToBackup) {
            const folderPath = path.join(instancePath, folder);
            if (fs.existsSync(folderPath)) {
                try {
                    const files = fs.readdirSync(folderPath);
                    if (files.length > 0) {
                        zip.addLocalFolder(folderPath, folder);
                    }
                } catch (err) {
                    console.error(`Error zipping folder ${folder}:`, err.message);
                }
            }
        }

        // Add config files
        if (options.config) {
            const configDir = path.join(instancePath, 'config');
            if (fs.existsSync(configDir)) {
                try {
                    const files = fs.readdirSync(configDir);
                    if (files.length > 0) {
                        zip.addLocalFolder(configDir, 'config');
                    }
                } catch (err) {
                    console.error("Error zipping config folder:", err.message);
                }
            }
            const optionsFile = path.join(instancePath, 'options.txt');
            if (fs.existsSync(optionsFile)) {
                zip.addLocalFile(optionsFile);
            }
        }

        // Write the ZIP file
        zip.writeZip(zipPath);

        const stats = fs.statSync(zipPath);
        const sizeMB = (stats.size / (1024 * 1024)).toFixed(2);

        res.json({
            success: true,
            filename,
            sizeMB,
            createdAt: stats.birthtime || new Date()
        });
    } catch (e) {
        console.error("Backup creation failed:", e);
        res.status(500).json({ error: "Failed to create backup: " + e.message });
    }
});

/**
 * GET /api/backup/list
 * Retrieve list of backups in minestaller_backups/ next to the instance
 */
router.get('/api/backup/list', (req, res) => {
    try {
        const { instancePath } = req.query;
        if (!instancePath || !fs.existsSync(instancePath)) {
            return res.status(400).json({ error: "Invalid instance path" });
        }

        const backupsDir = path.resolve(path.dirname(instancePath), 'minestaller_backups');
        if (!fs.existsSync(backupsDir)) {
            return res.json({ backupsDir, backups: [] });
        }

        const files = fs.readdirSync(backupsDir);
        const backups = [];

        for (const file of files) {
            if (file.toLowerCase().endsWith('.zip') && file.startsWith('backup_')) {
                const filePath = path.join(backupsDir, file);
                const stats = fs.statSync(filePath);
                backups.push({
                    filename: file,
                    sizeMB: (stats.size / (1024 * 1024)).toFixed(2),
                    createdAt: stats.birthtime || stats.mtime
                });
            }
        }

        // Sort by newest first
        backups.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

        res.json({ backupsDir, backups });
    } catch (e) {
        res.status(500).json({ error: "Failed to list backups: " + e.message });
    }
});

/**
 * POST /api/backup/restore
 * Extract a ZIP backup over the active instance directory
 */
router.post('/api/backup/restore', (req, res) => {
    try {
        const { instancePath, backupFile, clearFirst } = req.body;
        if (!instancePath || !fs.existsSync(instancePath)) {
            return res.status(400).json({ error: "Invalid instance path" });
        }
        if (!backupFile) {
            return res.status(400).json({ error: "Missing backup file name" });
        }

        const cleanBackupFile = path.basename(backupFile);
        const backupsDir = path.join(path.dirname(instancePath), 'minestaller_backups');
        const zipPath = path.join(backupsDir, cleanBackupFile);

        if (!fs.existsSync(zipPath)) {
            return res.status(404).json({ error: "Backup file not found" });
        }

        const zip = new AdmZip(zipPath);
        const zipEntries = zip.getEntries();

        if (clearFirst) {
            const foldersToClear = new Set();
            zipEntries.forEach(entry => {
                const parts = entry.entryName.split('/');
                if (parts[0]) {
                    foldersToClear.add(parts[0]);
                }
            });

            foldersToClear.forEach(item => {
                const targetPath = path.join(instancePath, item);
                if (fs.existsSync(targetPath)) {
                    deleteRecursive(targetPath);
                }
            });
        }

        // Extract all files
        zip.extractAllTo(instancePath, true);

        res.json({ success: true });
    } catch (e) {
        console.error("Backup restore failed:", e);
        res.status(500).json({ error: "Failed to restore backup: " + e.message });
    }
});

/**
 * POST /api/backup/delete
 * Delete a specific backup file
 */
router.post('/api/backup/delete', (req, res) => {
    try {
        const { instancePath, backupFile } = req.body;
        if (!instancePath || !backupFile) {
            return res.status(400).json({ error: "Missing parameters" });
        }

        const cleanBackupFile = path.basename(backupFile);
        const backupsDir = path.join(path.dirname(instancePath), 'minestaller_backups');
        const zipPath = path.join(backupsDir, cleanBackupFile);

        if (fs.existsSync(zipPath)) {
            fs.unlinkSync(zipPath);
            res.json({ success: true });
        } else {
            res.status(404).json({ error: "Backup file not found" });
        }
    } catch (e) {
        res.status(500).json({ error: "Failed to delete backup: " + e.message });
    }
});

module.exports = router;
