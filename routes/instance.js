const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const { GLOBAL_CONFIG_PATH, fetchJson } = require('../utils/sys');
const { calculateInstanceSizeMB } = require('../utils/file');
const { getActiveResourcePacks, getActiveShaderPack } = require('../utils/minecraft');

/**
 * GET /api/instance
 * Retrieve metadata and list of mods/worlds/resourcepacks/shaders in the active instance
 */
router.get('/api/instance', async (req, res) => {
    let instancePath = "";
    try {
        if (fs.existsSync(GLOBAL_CONFIG_PATH)) {
            const conf = JSON.parse(fs.readFileSync(GLOBAL_CONFIG_PATH, 'utf8'));
            instancePath = conf.lastMinecraftDir || "";
        }
    } catch (e) {}

    if (!instancePath || !fs.existsSync(instancePath)) {
        return res.status(400).json({ error: "No active instance configured" });
    }

    try {
        const instanceName = path.basename(instancePath);
        
        // mods count
        const modsDir = path.join(instancePath, 'mods');
        let modsCount = 0;
        let modsList = [];
        let loaderType = "Unknown";
        if (fs.existsSync(modsDir)) {
            const modFiles = fs.readdirSync(modsDir);
            const jars = modFiles.filter(f => f.toLowerCase().endsWith('.jar'));
            modsCount = jars.length;
            modsList = jars;
            
            let hasFabricApi = false;
            let hasForge = false;
            let hasNeoForge = false;
            for (const file of jars) {
                const lower = file.toLowerCase();
                if (lower.includes('fabric-api') || lower.includes('fabric-language-kotlin')) {
                    hasFabricApi = true;
                }
                if (lower.includes('neoforge') || lower.includes('neoforged')) {
                    hasNeoForge = true;
                }
                if (lower.includes('forge') && !lower.includes('fabric') && !lower.includes('neoforge')) {
                    hasForge = true;
                }
            }
            if (hasNeoForge) loaderType = "NeoForge";
            else if (hasFabricApi) loaderType = "Fabric";
            else if (hasForge) loaderType = "Forge";
        }

        // worlds count
        const savesDir = path.join(instancePath, 'saves');
        let worldsCount = 0;
        if (fs.existsSync(savesDir)) {
            const files = fs.readdirSync(savesDir);
            worldsCount = files.filter(f => {
                const worldDir = path.join(savesDir, f);
                return fs.statSync(worldDir).isDirectory() && fs.existsSync(path.join(worldDir, 'level.dat'));
            }).length;
        }

        // resourcepacks active flags
        const rpDir = path.join(instancePath, 'resourcepacks');
        const activeRps = getActiveResourcePacks(instancePath);
        const resourcepacks = [];
        if (fs.existsSync(rpDir)) {
            const files = fs.readdirSync(rpDir);
            for (const f of files) {
                if (f.startsWith('.')) continue;
                const isActive = activeRps.some(activeName => activeName.toLowerCase() === f.toLowerCase());
                resourcepacks.push({
                    name: f,
                    active: isActive
                });
            }
        }

        // shaders active flags
        const spDir = path.join(instancePath, 'shaderpacks');
        const activeShader = getActiveShaderPack(instancePath);
        const shaders = [];
        if (fs.existsSync(spDir)) {
            const files = fs.readdirSync(spDir);
            for (const f of files) {
                if (f.startsWith('.')) continue;
                const isActive = activeShader && activeShader.toLowerCase() === f.toLowerCase();
                shaders.push({
                    name: f,
                    active: !!isActive
                });
            }
        }

        // launcher profile details
        let loaderVersion = "Unknown";
        let mcVersion = "Unknown";
        let allocatedRam = "Unknown";
        let javaVersion = "Unknown";

        const defaultMc = path.join(instancePath, '..', '..');
        const profilesPath = path.join(defaultMc, 'launcher_profiles.json');
        if (fs.existsSync(profilesPath)) {
            try {
                const data = JSON.parse(fs.readFileSync(profilesPath, 'utf8'));
                if (data.profiles) {
                    for (const [id, prof] of Object.entries(data.profiles)) {
                        if (prof.gameDir && path.resolve(prof.gameDir).toLowerCase() === path.resolve(instancePath).toLowerCase()) {
                            if (prof.lastVersionId) {
                                const verParts = prof.lastVersionId.split('-');
                                if (verParts.includes('neoforge')) {
                                    loaderType = "NeoForge";
                                    const loaderIdx = verParts.indexOf('neoforge');
                                    if (loaderIdx !== -1 && verParts[loaderIdx + 1]) {
                                        loaderVersion = verParts[loaderIdx + 1];
                                    }
                                } else if (verParts.includes('fabric')) {
                                    loaderType = "Fabric";
                                    const loaderIdx = verParts.indexOf('loader');
                                    if (loaderIdx !== -1 && verParts[loaderIdx + 1]) {
                                        loaderVersion = verParts[loaderIdx + 1];
                                    }
                                } else if (verParts.includes('forge')) {
                                    loaderType = "Forge";
                                } else {
                                    loaderType = "Vanilla";
                                }
                                const lastPart = verParts[verParts.length - 1];
                                if (/^\d+\.\d+(\.\d+)?$/.test(lastPart)) {
                                    mcVersion = lastPart;
                                }
                            }
                            if (prof.javaArgs) {
                                const xmxMatch = prof.javaArgs.match(/-Xmx(\d+)([gGmM])/);
                                if (xmxMatch) {
                                    allocatedRam = `${xmxMatch[1]} ${xmxMatch[2].toUpperCase()}`;
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (err) {}
        }

        // Fallback 1: Parse Minecraft version from latest.log if still Unknown
        if (mcVersion === "Unknown") {
            const logPath = path.join(instancePath, 'logs', 'latest.log');
            if (fs.existsSync(logPath)) {
                try {
                    const content = fs.readFileSync(logPath, 'utf8');
                    // Fabric signature: "Loading Minecraft 1.20.1 with Fabric Loader"
                    const fabricMatch = content.match(/Loading Minecraft ([0-9\.]+) with Fabric Loader/i);
                    if (fabricMatch) {
                        mcVersion = fabricMatch[1];
                        if (loaderType === "Unknown") loaderType = "Fabric";
                    }
                    
                    // NeoForge signatures
                    const neoforgeMatch = content.match(/NeoForge version ([0-9\.\-a-zA-Z]+)/i);
                    if (neoforgeMatch) {
                        if (loaderType === "Unknown") loaderType = "NeoForge";
                        if (loaderVersion === "Unknown") loaderVersion = neoforgeMatch[1];
                    }
                    const neoforgeMcMatch = content.match(/Minecraft Version: ([0-9\.]+)/i);
                    if (neoforgeMcMatch && content.toLowerCase().includes('neoforge')) {
                        mcVersion = neoforgeMcMatch[1];
                        if (loaderType === "Unknown") loaderType = "NeoForge";
                    }
                    
                    // Forge/General signatures
                    if (mcVersion === "Unknown") {
                        const forgeMcMatch = content.match(/Minecraft Version: ([0-9\.]+)/i);
                        if (forgeMcMatch) {
                            mcVersion = forgeMcMatch[1];
                        }
                    }
                    if (mcVersion === "Unknown") {
                        const genericMatch = content.match(/Loading Minecraft ([0-9\.]+)/i);
                        if (genericMatch) {
                            mcVersion = genericMatch[1];
                        }
                    }
                } catch (e) {}
            }
        }

        // Fallback 2: Parse Minecraft version from the folder name itself if still Unknown
        if (mcVersion === "Unknown") {
            const dirName = path.basename(instancePath);
            const versionMatch = dirName.match(/\b(1\.\d+(\.\d+)?)\b/);
            if (versionMatch) {
                mcVersion = versionMatch[1];
            }
        }
        if (loaderType === "Unknown") {
            const dirName = path.basename(instancePath).toLowerCase();
            if (dirName.includes('neoforge')) loaderType = "NeoForge";
            else if (dirName.includes('fabric')) loaderType = "Fabric";
            else if (dirName.includes('forge')) loaderType = "Forge";
        }

        // modpack checking & dynamic icon resolving
        let modpack = { detected: false };
        if (fs.existsSync(GLOBAL_CONFIG_PATH)) {
            const conf = JSON.parse(fs.readFileSync(GLOBAL_CONFIG_PATH, 'utf8'));
            const normPath = instancePath.toLowerCase().replace(/\\/g, '/');
            if (conf.instances && conf.instances[normPath]) {
                const inst = conf.instances[normPath];
                if (inst.installedModpackTitle) {
                    modpack = {
                        detected: true,
                        name: inst.installedModpackTitle,
                        version: inst.installedModpackVersion || "1.0.0",
                        source: "Modrinth",
                        iconUrl: ""
                    };
                    
                    // Fetch icon from Modrinth if Id exists
                    if (inst.installedModpackId) {
                        try {
                            const projectData = await fetchJson(`https://api.modrinth.com/v2/project/${inst.installedModpackId}`);
                            if (projectData && projectData.icon_url) {
                                modpack.iconUrl = projectData.icon_url;
                            }
                        } catch (err) {
                            console.error("Failed fetching modpack icon: ", err.message);
                        }
                    }
                }
            }
        }

        const irisProps = path.join(instancePath, 'config', 'iris.properties');
        let shadersEnabled = false;
        if (fs.existsSync(irisProps)) {
            try {
                const content = fs.readFileSync(irisProps, 'utf8');
                const enabledMatch = content.match(/(?:shaderEnabled|enableShaders)=(true|false)/i);
                shadersEnabled = enabledMatch ? enabledMatch[1].toLowerCase() === 'true' : true;
            } catch(e) {}
        }

        const instanceSizeMB = calculateInstanceSizeMB(instancePath);

        res.json({
            instanceName,
            instancePath,
            mcVersion,
            loader: loaderType,
            loaderVersion,
            javaVersion,
            allocatedRam,
            instanceSizeMB,
            status: "ready",
            modrinthOnline: true,
            mods: { total: modsCount, list: modsList },
            worlds: { total: worldsCount },
            resourcepacks,
            shaders,
            shadersEnabled,
            modpack
        });

    } catch (e) {
        res.status(500).json({ error: "Failed to load instance: " + e.message });
    }
});

/**
 * POST /api/resourcepacks/toggle
 * Enable or disable a resourcepack in options.txt
 */
router.post('/api/resourcepacks/toggle', (req, res) => {
    try {
        const { instancePath, packName, active } = req.body;
        if (!instancePath || !packName) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        const optionsPath = path.join(instancePath, 'options.txt');
        if (!fs.existsSync(optionsPath)) {
            fs.writeFileSync(optionsPath, `resourcePacks:[${active ? JSON.stringify("file/" + packName) : ""}]\n`, 'utf8');
            return res.json({ success: true });
        }

        let content = fs.readFileSync(optionsPath, 'utf8');
        let lines = content.split(/\r?\n/);
        let rpIndex = lines.findIndex(l => l.startsWith('resourcePacks:'));
        
        let arr = [];
        if (rpIndex !== -1) {
            const arrStr = lines[rpIndex].substring('resourcePacks:'.length).trim();
            try {
                arr = JSON.parse(arrStr);
            } catch(e) {
                arr = [];
            }
        }

        const fileTarget = "file/" + packName;
        arr = arr.filter(item => item !== fileTarget && item !== packName);

        if (active) {
            arr.push(fileTarget);
        }

        const newLine = `resourcePacks:${JSON.stringify(arr)}`;
        if (rpIndex !== -1) {
            lines[rpIndex] = newLine;
        } else {
            lines.push(newLine);
        }

        fs.writeFileSync(optionsPath, lines.join('\n'), 'utf8');
        res.json({ success: true });
    } catch(e) {
        res.status(500).json({ error: e.message });
    }
});

/**
 * POST /api/resourcepacks/toggle-all
 * Enable or disable all resourcepacks in options.txt
 */
router.post('/api/resourcepacks/toggle-all', (req, res) => {
    try {
        const { instancePath, active } = req.body;
        if (!instancePath) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        const optionsPath = path.join(instancePath, 'options.txt');
        
        let allPacks = [];
        if (active) {
            const rpDir = path.join(instancePath, 'resourcepacks');
            if (fs.existsSync(rpDir)) {
                allPacks = fs.readdirSync(rpDir).filter(f => f.endsWith('.zip') || fs.statSync(path.join(rpDir, f)).isDirectory());
            }
        }

        if (!fs.existsSync(optionsPath)) {
            const arrStr = JSON.stringify(allPacks.map(p => "file/" + p));
            fs.writeFileSync(optionsPath, `resourcePacks:${arrStr}\n`, 'utf8');
            return res.json({ success: true });
        }

        let content = fs.readFileSync(optionsPath, 'utf8');
        let lines = content.split(/\r?\n/);
        let rpIndex = lines.findIndex(l => l.startsWith('resourcePacks:'));

        const arr = active ? allPacks.map(p => "file/" + p) : [];
        const newLine = `resourcePacks:${JSON.stringify(arr)}`;

        if (rpIndex !== -1) {
            lines[rpIndex] = newLine;
        } else {
            lines.push(newLine);
        }

        fs.writeFileSync(optionsPath, lines.join('\n'), 'utf8');
        res.json({ success: true });
    } catch(e) {
        res.status(500).json({ error: e.message });
    }
});

/**
 * POST /api/shaders/toggle
 * Toggle active shader pack in iris.properties config
 */
router.post('/api/shaders/toggle', (req, res) => {
    try {
        const { instancePath, shaderName, active } = req.body;
        if (!instancePath || !shaderName) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        const irisProps = path.join(instancePath, 'config', 'iris.properties');
        const configDir = path.dirname(irisProps);
        if (!fs.existsSync(configDir)) {
            fs.mkdirSync(configDir, { recursive: true });
        }

        let lines = [];
        if (fs.existsSync(irisProps)) {
            lines = fs.readFileSync(irisProps, 'utf8').split(/\r?\n/);
        }

        let packIdx = lines.findIndex(l => l.startsWith('shaderPack=') || l.startsWith('shaderpack='));
        const newLine = `shaderPack=${active ? shaderName : ""}`;

        if (packIdx !== -1) {
            lines[packIdx] = newLine;
        } else {
            lines.push(newLine);
        }

        let enabledIdx = lines.findIndex(l => l.startsWith('shaderEnabled=') || l.startsWith('shaderenabled='));
        const enabledLine = `shaderEnabled=${active ? "true" : "false"}`;
        if (enabledIdx !== -1) {
            lines[enabledIdx] = enabledLine;
        } else {
            lines.push(enabledLine);
        }

        let enableShadersIdx = lines.findIndex(l => l.startsWith('enableShaders=') || l.startsWith('enableshaders='));
        const enableShadersLine = `enableShaders=${active ? "true" : "false"}`;
        if (enableShadersIdx !== -1) {
            lines[enableShadersIdx] = enableShadersLine;
        } else {
            lines.push(enableShadersLine);
        }

        fs.writeFileSync(irisProps, lines.join('\n'), 'utf8');
        res.json({ success: true });
    } catch(e) {
        res.status(500).json({ error: e.message });
    }
});

/**
 * POST /api/shaders/toggle-all
 * Deactivate shaders in iris.properties config
 */
router.post('/api/shaders/toggle-all', (req, res) => {
    try {
        const { instancePath, active } = req.body;
        if (!instancePath) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        const irisProps = path.join(instancePath, 'config', 'iris.properties');
        const configDir = path.dirname(irisProps);
        if (!fs.existsSync(configDir)) {
            fs.mkdirSync(configDir, { recursive: true });
        }

        let lines = [];
        if (fs.existsSync(irisProps)) {
            lines = fs.readFileSync(irisProps, 'utf8').split(/\r?\n/);
        }

        let packIdx = lines.findIndex(l => l.startsWith('shaderPack=') || l.startsWith('shaderpack='));
        const newLine = `shaderPack=`;
        if (packIdx !== -1) {
            lines[packIdx] = newLine;
        } else {
            lines.push(newLine);
        }

        let enabledIdx = lines.findIndex(l => l.startsWith('shaderEnabled=') || l.startsWith('shaderenabled='));
        const enabledLine = `shaderEnabled=false`;
        if (enabledIdx !== -1) {
            lines[enabledIdx] = enabledLine;
        } else {
            lines.push(enabledLine);
        }

        let enableShadersIdx = lines.findIndex(l => l.startsWith('enableShaders=') || l.startsWith('enableshaders='));
        const enableShadersLine = `enableShaders=false`;
        if (enableShadersIdx !== -1) {
            lines[enableShadersIdx] = enableShadersLine;
        } else {
            lines.push(enableShadersLine);
        }

        fs.writeFileSync(irisProps, lines.join('\n'), 'utf8');
        res.json({ success: true });
    } catch(e) {
        res.status(500).json({ error: e.message });
    }
});

/**
 * POST /api/shaders/toggle-system
 * Enable/disable Iris shaders system globally
 */
router.post('/api/shaders/toggle-system', (req, res) => {
    try {
        const { instancePath, active } = req.body;
        if (!instancePath) {
            return res.status(400).json({ error: "Missing parameters" });
        }
        const irisProps = path.join(instancePath, 'config', 'iris.properties');
        const configDir = path.dirname(irisProps);
        if (!fs.existsSync(configDir)) {
            fs.mkdirSync(configDir, { recursive: true });
        }

        let lines = [];
        if (fs.existsSync(irisProps)) {
            lines = fs.readFileSync(irisProps, 'utf8').split(/\r?\n/);
        }

        let enabledIdx = lines.findIndex(l => l.startsWith('shaderEnabled=') || l.startsWith('shaderenabled='));
        const enabledLine = `shaderEnabled=${active ? "true" : "false"}`;
        if (enabledIdx !== -1) {
            lines[enabledIdx] = enabledLine;
        } else {
            lines.push(enabledLine);
        }

        let enableShadersIdx = lines.findIndex(l => l.startsWith('enableShaders=') || l.startsWith('enableshaders='));
        const enableShadersLine = `enableShaders=${active ? "true" : "false"}`;
        if (enableShadersIdx !== -1) {
            lines[enableShadersIdx] = enableShadersLine;
        } else {
            lines.push(enableShadersLine);
        }

        fs.writeFileSync(irisProps, lines.join('\n'), 'utf8');
        res.json({ success: true });
    } catch(e) {
        res.status(500).json({ error: e.message });
    }
});

module.exports = router;
