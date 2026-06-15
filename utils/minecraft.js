const fs = require('fs');
const path = require('path');

/**
 * Gets active resourcepacks from options.txt.
 * @param {string} instancePath
 * @returns {Array<string>} List of resourcepack file names
 */
function getActiveResourcePacks(instancePath) {
    const optionsPath = path.join(instancePath, 'options.txt');
    if (!fs.existsSync(optionsPath)) return [];
    try {
        const content = fs.readFileSync(optionsPath, 'utf8');
        const match = content.match(/resourcePacks:(.+)/);
        if (match) {
            const arrStr = match[1].trim();
            try {
                const arr = JSON.parse(arrStr);
                return arr.map(p => p.replace(/^file\//, ''));
            } catch (err) {
                return arrStr.replace(/[\[\]"]/g, '').split(',').map(p => p.trim().replace(/^file\//, ''));
            }
        }
    } catch (e) {
        console.error("Failed to parse options.txt", e);
    }
    return [];
}

/**
 * Gets active shaderpack from iris.properties.
 * @param {string} instancePath
 * @returns {string|null} Active shader name, or null if shaders are disabled or not configured
 */
function getActiveShaderPack(instancePath) {
    const irisProps = path.join(instancePath, 'config', 'iris.properties');
    if (!fs.existsSync(irisProps)) return null;
    try {
        const content = fs.readFileSync(irisProps, 'utf8');

        // Return null if shaders are disabled globally
        const enabledMatch = content.match(/(?:shaderEnabled|enableShaders)=(true|false)/i);
        const isEnabled = enabledMatch ? enabledMatch[1].toLowerCase() === 'true' : true;
        if (!isEnabled) return null;

        const match = content.match(/shaderPack=(.+)/);
        if (match) {
            return match[1].trim();
        }
    } catch (e) {
        console.error("Failed to parse iris.properties", e);
    }
    return null;
}

module.exports = {
    getActiveResourcePacks,
    getActiveShaderPack
};
