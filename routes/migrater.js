const express = require('express');
const router = express.Router();
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { GLOBAL_CONFIG_PATH, getSystemDrives } = require('../utils/sys');
const { deleteRecursive, copyRecursive } = require('../utils/file');

/**
 * GET /api/browse
 * Filesystem navigation helper: list subfolders in directory, and system drives
 */
router.get('/api/browse', (req, res) => {
    let target = req.query.path || process.env.USERPROFILE || 'C:\\';
    target = path.resolve(target);
    
    try {
        if (!fs.existsSync(target) || !fs.statSync(target).isDirectory()) {
            return res.status(400).json({ error: "Directory does not exist" });
        }
        
        const files = fs.readdirSync(target);
        const folders = [];
        for (const file of files) {
            try {
                const full = path.join(target, file);
                if (fs.statSync(full).isDirectory() && !file.startsWith('.')) {
                    folders.push(file);
                }
            } catch (err) {}
        }
        
        const drives = process.platform === 'win32' ? getSystemDrives() : [];
        res.json({
            currentPath: target,
            parentPath: path.dirname(target) !== target ? path.dirname(target) : null,
            folders: folders.sort((a, b) => a.localeCompare(b)),
            drives
        });
    } catch (e) {
        res.status(500).json({ error: "Failed to browse: " + e.message });
    }
});

/**
 * GET /api/detect-instances
 * Automated detection of installed launchers and profile folders (Official, CurseForge, Prism, GDLauncher, etc.)
 */
router.get('/api/detect-instances', (req, res) => {
    const userProfile = process.env.USERPROFILE || 'C:\\Users\\default';
    const appData = process.env.APPDATA || path.join(userProfile, 'AppData', 'Roaming');
    const detected = [];

    const defaultMc = path.join(appData, '.minecraft');
    if (fs.existsSync(defaultMc)) {
        detected.push({ name: "default", path: defaultMc, launcher: "Minecraft Launcher" });

        const profilesPath = path.join(defaultMc, 'launcher_profiles.json');
        if (fs.existsSync(profilesPath)) {
            try {
                const data = JSON.parse(fs.readFileSync(profilesPath, 'utf8'));
                if (data.profiles) {
                    for (const [id, prof] of Object.entries(data.profiles)) {
                        if (prof.gameDir && fs.existsSync(prof.gameDir)) {
                            if (!detected.some(d => d.path.toLowerCase() === prof.gameDir.toLowerCase())) {
                                detected.push({ name: prof.name || `Profile_${id.substring(0,5)}`, path: prof.gameDir, launcher: "Minecraft Profile" });
                            }
                        }
                    }
                }
            } catch (err) {}
        }

        const instancesDir = path.join(defaultMc, 'instances');
        if (fs.existsSync(instancesDir)) {
            try {
                fs.readdirSync(instancesDir).forEach(f => {
                    const full = path.join(instancesDir, f);
                    if (fs.statSync(full).isDirectory() && f.toLowerCase() !== 'minestaller_backups') {
                        if (!detected.some(d => d.path.toLowerCase() === full.toLowerCase())) {
                            detected.push({ name: f, path: full, launcher: "Minecraft Instances" });
                        }
                    }
                });
            } catch (err) {}
        }
    }

    const curseforgeDir = path.join(userProfile, 'curseforge', 'minecraft', 'Instances');
    if (fs.existsSync(curseforgeDir)) {
        try {
            fs.readdirSync(curseforgeDir).forEach(f => {
                const full = path.join(curseforgeDir, f);
                if (fs.statSync(full).isDirectory()) {
                    detected.push({ name: f, path: full, launcher: "CurseForge Launcher" });
                }
            });
        } catch (err) {}
    }
    
    res.json(detected);
});

/**
 * POST /api/port-profile
 * Port/Migrate config files, saves, mods, resourcepacks, shaders from one profile to another
 */
router.post('/api/port-profile', (req, res) => {
    const { sourcePath, destPath, options } = req.body;
    if (!sourcePath || !destPath) {
        return res.status(400).json({ error: "Missing source or destination paths." });
    }

    if (!fs.existsSync(sourcePath)) {
        return res.status(400).json({ error: `Source path does not exist: ${sourcePath}` });
    }

    // Ensure destPath exists
    if (!fs.existsSync(destPath)) {
        try {
            fs.mkdirSync(destPath, { recursive: true });
        } catch (e) {
            return res.status(500).json({ error: `Failed to create destination folder: ${e.message}` });
        }
    }

    const log = [];
    try {
        const foldersToSync = [];
        if (options.mods) foldersToSync.push({ name: 'mods' });
        if (options.config) foldersToSync.push({ name: 'config' });
        if (options.resourcepacks) foldersToSync.push({ name: 'resourcepacks' });
        if (options.shaders) foldersToSync.push({ name: 'shaderpacks' });
        if (options.saves) foldersToSync.push({ name: 'saves' });

        const filesToSync = [];
        if (options.settings) {
            filesToSync.push('options.txt');
            filesToSync.push('optionsof.txt');
            filesToSync.push('optionsshaders.txt');
        }

        log.push(`[INFO] Starting profile migration from "${sourcePath}" to "${destPath}"`);

        // 1. Process directories
        for (const item of foldersToSync) {
            const srcDir = path.join(sourcePath, item.name);
            const dstDir = path.join(destPath, item.name);

            if (fs.existsSync(srcDir)) {
                if (options.clearDest && fs.existsSync(dstDir)) {
                    log.push(`[INFO] Clearing existing folder: ${item.name}`);
                    deleteRecursive(dstDir);
                }
                log.push(`[INFO] Copying folder: ${item.name}`);
                fs.mkdirSync(dstDir, { recursive: true });
                copyRecursive(srcDir, dstDir);
                log.push(`[SUCCESS] Ported folder: ${item.name}`);
            } else {
                log.push(`[WARNING] Source folder not found, skipping: ${item.name}`);
            }
        }

        // 2. Process files
        for (const file of filesToSync) {
            const srcFile = path.join(sourcePath, file);
            const dstFile = path.join(destPath, file);

            if (fs.existsSync(srcFile)) {
                log.push(`[INFO] Copying config file: ${file}`);
                fs.mkdirSync(path.dirname(dstFile), { recursive: true });
                fs.copyFileSync(srcFile, dstFile);
                log.push(`[SUCCESS] Ported file: ${file}`);
            } else {
                log.push(`[WARNING] Source file not found, skipping: ${file}`);
            }
        }

        log.push(`[SUCCESS] Migration completed successfully.`);
        res.json({ success: true, log });
    } catch (err) {
        console.error("Profile port failed", err);
        log.push(`[ERROR] Sync failed: ${err.message}`);
        res.status(500).json({ error: "Migration failed: " + err.message, log });
    }
});

/**
 * POST /api/open-folder
 * Opens the active Minecraft instance folder in the native OS file explorer
 */
router.post('/api/open-folder', (req, res) => {
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
        const cmd = process.platform === 'win32' ? `start "" "${instancePath}"` :
                    process.platform === 'darwin' ? `open "${instancePath}"` :
                    `xdg-open "${instancePath}"`;

        exec(cmd, (err) => {
            if (err) {
                console.error("Failed to open folder", err);
                return res.status(500).json({ error: "Failed to open folder: " + err.message });
            }
            res.json({ success: true });
        });
    } catch (e) {
        res.status(500).json({ error: "Failed to open folder: " + e.message });
    }
});

/**
 * POST /api/launch
 * Start the associated launcher pointing directly to the active profile instance
 */
router.post('/api/launch', (req, res) => {
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
        const userProfile = process.env.USERPROFILE || 'C:\\Users\\default';
        const localAppData = process.env.LOCALAPPDATA || path.join(userProfile, 'AppData', 'Local');
        const appData = process.env.APPDATA || path.join(userProfile, 'AppData', 'Roaming');
        const programFiles = process.env.PROGRAMFILES || 'C:\\Program Files';
        const programFilesX86 = process.env['PROGRAMFILES(X86)'] || 'C:\\Program Files (x86)';

        const launchersList = [
            {
                id: "prismlauncher",
                name: "Prism Launcher",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'PrismLauncher', 'PrismLauncher.exe'),
                    path.join(programFiles, 'Prism Launcher', 'prismlauncher.exe'),
                    path.join(appData, 'PrismLauncher', 'PrismLauncher.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "curseforge",
                name: "CurseForge Launcher",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'curseforge', 'CurseForge.exe'),
                    path.join(appData, 'CurseForge')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "modrinth",
                name: "Modrinth App",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'modrinth-app', 'ModrinthApp.exe'),
                    path.join(localAppData, 'Programs', 'modrinth-app', 'Modrinth App.exe'),
                    path.join(appData, 'ModrinthApp', 'ModrinthApp.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "gdlauncher",
                name: "GDLauncher",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'gdlauncher', 'GDLauncher.exe'),
                    path.join(appData, 'gdlauncher')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "tlauncher",
                name: "TLauncher",
                detectPaths: [
                    path.join(appData, '.tlauncher', 'TLauncher.exe'),
                    path.join(userProfile, 'Desktop', 'TLauncher.exe'),
                    path.join(userProfile, 'Downloads', 'TLauncher.exe'),
                    path.join(programFiles, 'TLauncher', 'TLauncher.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "lunarclient",
                name: "Lunar Client",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'lunarclient', 'Lunar Client.exe'),
                    path.join(programFiles, 'Lunar Client', 'Lunar Client.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "badlion",
                name: "Badlion Client",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'badlion-client', 'Badlion Client.exe'),
                    path.join(programFiles, 'Badlion Client', 'Badlion Client.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "feather",
                name: "Feather Client",
                detectPaths: [
                    path.join(localAppData, 'Programs', 'feather-updater', 'Feather Launcher.exe'),
                    path.join(programFiles, 'Feather Launcher', 'Feather Launcher.exe')
                ],
                launchCmd: '',
                hasInstance: false,
                installed: false
            },
            {
                id: "minecraft",
                name: "Official Minecraft Launcher",
                detectPaths: [
                    path.join(programFilesX86, 'Minecraft Launcher', 'MinecraftLauncher.exe'),
                    path.join(programFiles, 'Minecraft Launcher', 'MinecraftLauncher.exe')
                ],
                launchCmd: 'start minecraft://',
                hasInstance: false,
                installed: false
            }
        ];

        launchersList.forEach(launcher => {
            let isInstalled = false;
            let foundPath = "";

            for (const p of launcher.detectPaths) {
                if (fs.existsSync(p)) {
                    isInstalled = true;
                    if (fs.statSync(p).isFile()) {
                        foundPath = p;
                    }
                    break;
                }
            }

            // Additional store package check for official Minecraft launcher
            if (launcher.id === 'minecraft' && !isInstalled) {
                const storePackageDir = path.join(localAppData, 'Packages', 'Microsoft.4297127D64ECE_8wekyb3d8bbwe');
                if (fs.existsSync(storePackageDir)) {
                    isInstalled = true;
                }
            }

            launcher.installed = isInstalled;

            // Match active instance path
            const normPath = instancePath.toLowerCase().replace(/\\/g, '/');
            if (launcher.id === 'minecraft' || launcher.id === 'tlauncher' || launcher.id === 'lunarclient' || launcher.id === 'badlion' || launcher.id === 'feather') {
                if (normPath.includes('appdata/roaming/.minecraft')) {
                    launcher.hasInstance = true;
                }
            } else if (launcher.id === 'curseforge') {
                if (normPath.includes('curseforge')) {
                    launcher.hasInstance = true;
                }
            } else if (launcher.id === 'prismlauncher') {
                if (normPath.includes('prismlauncher')) {
                    launcher.hasInstance = true;
                }
            } else if (launcher.id === 'modrinth') {
                if (normPath.includes('modrinth')) {
                    launcher.hasInstance = true;
                }
            } else if (launcher.id === 'gdlauncher') {
                if (normPath.includes('gdlauncher')) {
                    launcher.hasInstance = true;
                }
            }

            // Build launcher start command
            if (foundPath) {
                if (launcher.id === 'prismlauncher' && launcher.hasInstance) {
                    const instName = path.basename(instancePath);
                    launcher.launchCmd = `start "" "${foundPath}" --launch "${instName}"`;
                } else {
                    launcher.launchCmd = `start "" "${foundPath}"`;
                }
            } else if (launcher.id === 'minecraft' && isInstalled) {
                launcher.launchCmd = 'start minecraft://';
            }
        });

        // Find installed launcher that contains the active instance folder
        let targetLauncher = launchersList.find(l => l.installed && l.hasInstance);

        // Fallback to first detected launcher if none has it (or outside normal launcher directories)
        if (!targetLauncher) {
            targetLauncher = launchersList.find(l => l.installed);
        }

        if (!targetLauncher) {
            return res.status(400).json({ error: "Could not find any installed Minecraft launcher on your PC. Please open your launcher manually." });
        }

        const cmd = targetLauncher.launchCmd;
        const name = targetLauncher.name;

        exec(cmd, (err) => {
            if (err) {
                console.error("Launch command failed", err);
                return res.status(500).json({ error: `Failed to launch ${name}: ` + err.message });
            }
            res.json({ success: true, message: `Successfully launched ${name}!` });
        });
    } catch (e) {
        res.status(500).json({ error: "Failed to launch: " + e.message });
    }
});

module.exports = router;
