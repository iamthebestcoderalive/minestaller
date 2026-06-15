const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const { readLevelDat, writeLevelDat } = require('../utils/nbt');
const { deleteRecursive } = require('../utils/file');

/**
 * GET /api/worlds
 * Retrieve list of worlds with level.dat metadata (game type, difficulty, hardcore, cheats enabled, corruption checks)
 */
router.get('/api/worlds', (req, res) => {
    const { instancePath } = req.query;
    if (!instancePath || !fs.existsSync(instancePath)) {
        return res.status(400).json({ error: "Active instance path missing" });
    }

    const savesDir = path.join(instancePath, 'saves');
    if (!fs.existsSync(savesDir)) {
        return res.json([]);
    }

    try {
        const worldsList = [];
        const files = fs.readdirSync(savesDir);
        for (const f of files) {
            const worldDir = path.join(savesDir, f);
            if (fs.statSync(worldDir).isDirectory() && fs.existsSync(path.join(worldDir, 'level.dat'))) {
                const wi = {
                    directoryName: f,
                    levelName: f,
                    gameType: 0,
                    difficulty: 2,
                    hardcore: false,
                    allowCommands: false,
                    isCorrupted: false
                };

                try {
                    const rootNbt = readLevelDat(worldDir);
                    const data = rootNbt.value.Data;
                    if (data) {
                        wi.levelName = data.value.LevelName ? data.value.LevelName.value : f;
                        wi.gameType = data.value.GameType ? data.value.GameType.value : 0;
                        wi.difficulty = data.value.Difficulty ? data.value.Difficulty.value : 2;
                        wi.hardcore = data.value.hardcore ? data.value.hardcore.value === 1 : false;
                        wi.allowCommands = data.value.allowCommands ? data.value.allowCommands.value === 1 : false;
                    }
                } catch (err) {
                    wi.levelName = f + " (Load Error)";
                    wi.isCorrupted = true;
                    console.error("Failed parsing level.dat for: " + f, err);
                }
                worldsList.push(wi);
            }
        }
        res.json(worldsList);
    } catch (e) {
        res.status(500).json({ error: "Failed to list worlds: " + e.message });
    }
});

/**
 * POST /api/worlds/settings
 * Update world properties inside level.dat binary NBT
 */
router.post('/api/worlds/settings', (req, res) => {
    const { instancePath, worldName, levelName, gameType, difficulty, hardcore, allowCommands } = req.body;
    if (!instancePath || !worldName) {
        return res.status(400).json({ error: "Missing required properties" });
    }

    const worldDir = path.join(instancePath, 'saves', worldName);
    try {
        const rootNbt = readLevelDat(worldDir);
        const data = rootNbt.value.Data;
        if (data) {
            if (data.value.LevelName) data.value.LevelName.value = levelName;
            if (data.value.GameType) data.value.GameType.value = Number(gameType);
            if (data.value.Difficulty) data.value.Difficulty.value = Number(difficulty);
            if (data.value.hardcore) data.value.hardcore.value = hardcore ? 1 : 0;
            if (data.value.allowCommands) data.value.allowCommands.value = allowCommands ? 1 : 0;

            writeLevelDat(worldDir, rootNbt);
            res.json({ success: true });
        } else {
            res.status(500).json({ error: "Data tag not found in level.dat" });
        }
    } catch (e) {
        res.status(500).json({ error: "Failed to update world settings: " + e.message });
    }
});

/**
 * GET /api/worlds/datapacks
 * Retrieve list of datapacks installed in a specific world
 */
router.get('/api/worlds/datapacks', (req, res) => {
    const { instancePath, worldName } = req.query;
    if (!instancePath || !worldName) {
        return res.status(400).json({ error: "Missing parameters" });
    }

    const datapacksDir = path.join(instancePath, 'saves', worldName, 'datapacks');
    if (!fs.existsSync(datapacksDir)) {
        return res.json([]);
    }

    try {
        const items = fs.readdirSync(datapacksDir).map(name => {
            const isDir = fs.statSync(path.join(datapacksDir, name)).isDirectory();
            return { name, type: isDir ? 'Folder' : 'ZIP' };
        });
        res.json(items);
    } catch (e) {
        res.status(500).json({ error: "Failed to list datapacks: " + e.message });
    }
});

/**
 * POST /api/worlds/datapacks/delete
 * Delete a specific datapack folder/ZIP in a world
 */
router.post('/api/worlds/datapacks/delete', (req, res) => {
    const { instancePath, worldName, datapackName } = req.body;
    if (!instancePath || !worldName || !datapackName) {
        return res.status(400).json({ error: "Missing parameters" });
    }

    const target = path.join(instancePath, 'saves', worldName, 'datapacks', datapackName);
    try {
        deleteRecursive(target);
        res.json({ success: true });
    } catch (e) {
        res.status(500).json({ error: "Delete failed: " + e.message });
    }
});

/**
 * POST /api/worlds/:worldName/datapacks/upload
 * Stream a raw binary datapack file upload into target folder
 */
router.post('/api/worlds/:worldName/datapacks/upload', (req, res) => {
    const { worldName } = req.params;
    const { instancePath } = req.query;
    const filename = req.headers['x-filename'];

    if (!instancePath || !filename) {
        return res.status(400).json({ error: "Missing parameters" });
    }

    const destDir = path.join(instancePath, 'saves', worldName, 'datapacks');
    if (!fs.existsSync(destDir)) {
        fs.mkdirSync(destDir, { recursive: true });
    }

    const dest = path.join(destDir, filename);
    const writeStream = fs.createWriteStream(dest);

    req.pipe(writeStream);
    writeStream.on('finish', () => res.json({ success: true }));
    writeStream.on('error', (err) => res.status(500).json({ error: "Upload failed: " + err.message }));
});

module.exports = router;
