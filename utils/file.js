const fs = require('fs');
const path = require('path');

/**
 * Recursively copies a file or directory from src to dest.
 * @param {string} src
 * @param {string} dest
 */
function copyRecursive(src, dest) {
    const exists = fs.existsSync(src);
    const stats = exists && fs.statSync(src);
    const isDirectory = exists && stats.isDirectory();
    if (isDirectory) {
        if (!fs.existsSync(dest)) {
            fs.mkdirSync(dest, { recursive: true });
        }
        fs.readdirSync(src).forEach((child) => {
            copyRecursive(path.join(src, child), path.join(dest, child));
        });
    } else {
        fs.copyFileSync(src, dest);
    }
}

/**
 * Recursively deletes a file or directory.
 * @param {string} target
 */
function deleteRecursive(target) {
    if (fs.existsSync(target)) {
        if (fs.statSync(target).isDirectory()) {
            fs.readdirSync(target).forEach((file) => {
                deleteRecursive(path.join(target, file));
            });
            fs.rmdirSync(target);
        } else {
            fs.unlinkSync(target);
        }
    }
}

/**
 * Recursively sums folder size in bytes.
 * @param {string} dirPath
 * @returns {number} Size in bytes
 */
function getFolderSize(dirPath) {
    let size = 0;
    if (!fs.existsSync(dirPath)) return 0;
    try {
        const stats = fs.statSync(dirPath);
        if (stats.isFile()) {
            return stats.size;
        } else if (stats.isDirectory()) {
            const files = fs.readdirSync(dirPath);
            for (const file of files) {
                size += getFolderSize(path.join(dirPath, file));
            }
        }
    } catch (e) {
        // Ignore files we cannot access
    }
    return size;
}

/**
 * Calculates active Minecraft folders size footprint in Megabytes (MB).
 * @param {string} instancePath
 * @returns {number} Size in MB (rounded)
 */
function calculateInstanceSizeMB(instancePath) {
    const folders = ['mods', 'resourcepacks', 'saves', 'shaderpacks'];
    let totalBytes = 0;
    for (const f of folders) {
        totalBytes += getFolderSize(path.join(instancePath, f));
    }
    return Math.round(totalBytes / (1024 * 1024));
}

module.exports = {
    copyRecursive,
    deleteRecursive,
    getFolderSize,
    calculateInstanceSizeMB
};
