const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const os = require('os');
const { GLOBAL_CONFIG_PATH } = require('../utils/sys');

/**
 * GET /api/config
 * Load global config file
 */
router.get('/api/config', (req, res) => {
    try {
        if (fs.existsSync(GLOBAL_CONFIG_PATH)) {
            res.json(JSON.parse(fs.readFileSync(GLOBAL_CONFIG_PATH, 'utf8')));
        } else {
            res.json({ lastMinecraftDir: "", instances: {} });
        }
    } catch (e) {
        res.status(500).json({ error: "Failed to load config: " + e.message });
    }
});

/**
 * POST /api/config
 * Save global config file
 */
router.post('/api/config', (req, res) => {
    try {
        fs.writeFileSync(GLOBAL_CONFIG_PATH, JSON.stringify(req.body, null, 2), 'utf8');
        res.json({ success: true });
    } catch (e) {
        res.status(500).json({ error: "Failed to save config: " + e.message });
    }
});

/**
 * POST /api/scan
 * Analyze a target directory to identify loaders, saves, and mods presence
 */
router.post('/api/scan', (req, res) => {
    const { dirPath } = req.body;
    if (!dirPath || !fs.existsSync(dirPath)) {
        return res.status(400).json({ error: "Target directory does not exist" });
    }

    try {
        const details = {
            minecraftVersion: "1.20.1",
            loaderType: "Vanilla",
            hasSaves: false,
            hasMods: false,
            modCount: 0
        };

        if (fs.existsSync(path.join(dirPath, 'saves'))) details.hasSaves = true;
        if (fs.existsSync(path.join(dirPath, 'mods'))) details.hasMods = true;

        const modsDir = path.join(dirPath, 'mods');
        if (fs.existsSync(modsDir)) {
            const modFiles = fs.readdirSync(modsDir);
            details.modCount = modFiles.filter(f => f.toLowerCase().endsWith('.jar')).length;
            let hasFabricApi = false;
            let hasForge = false;
            for (const file of modFiles) {
                const lower = file.toLowerCase();
                if (lower.includes('fabric-api') || lower.includes('fabric-language-kotlin')) {
                    hasFabricApi = true;
                }
                if (lower.includes('forge') && !lower.includes('fabric')) {
                    hasForge = true;
                }
            }
            if (hasFabricApi) details.loaderType = "Fabric";
            else if (hasForge) details.loaderType = "Forge";
        }

        res.json(details);
    } catch (e) {
        res.status(500).json({ error: "Scan failed: " + e.message });
    }
});

/**
 * GET /api/network-ips
 * Lists available local network interfaces IPv4 addresses
 */
router.get('/api/network-ips', (req, res) => {
    try {
        const interfaces = os.networkInterfaces();
        const detected = [];
        
        for (const [name, netInterface] of Object.entries(interfaces)) {
            for (const details of netInterface) {
                if (details.family === 'IPv4' && !details.internal) {
                    detected.push({
                        name: name,
                        ip: details.address,
                        url: `http://${details.address}:5000`
                    });
                }
            }
        }
        res.json(detected);
    } catch (e) {
        res.status(500).json({ error: "Failed to detect network IPs: " + e.message });
    }
});

module.exports = router;
