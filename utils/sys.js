const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

// Path to the global config in the root workspace
const GLOBAL_CONFIG_PATH = path.join(__dirname, '..', 'minestaller_config.json');

/**
 * Lists system drives on Windows using PowerShell.
 * @returns {Array<string>} List of root drive paths (e.g. ["C:\\"])
 */
function getSystemDrives() {
    try {
        const stdout = execSync('powershell -Command "Get-PSDrive -PSProvider FileSystem | Select-Object -ExpandProperty Name"').toString();
        return stdout.split('\n')
            .map(line => line.trim())
            .filter(line => /^[A-Z]$/.test(line))
            .map(drive => drive + ':\\');
    } catch (e) {
        return ['C:\\'];
    }
}

/**
 * Fetches JSON from a URL with user-agent headers.
 * @param {string} url
 * @returns {Promise<object>} Parsed JSON object
 */
function fetchJson(url) {
    return new Promise((resolve, reject) => {
        https.get(url, { headers: { 'User-Agent': 'Minestaller/1.0.0' } }, (response) => {
            if (response.statusCode !== 200) {
                reject(new Error(`Status ${response.statusCode}`));
                return;
            }
            let data = '';
            response.on('data', chunk => data += chunk);
            response.on('end', () => {
                try {
                    resolve(JSON.parse(data));
                } catch (e) {
                    reject(e);
                }
            });
        }).on('error', reject);
    });
}

/**
 * Downloads a file with redirect support and progress callbacks.
 * @param {string} url
 * @param {string} destPath
 * @param {Function} onProgress (readBytes, totalBytes)
 * @returns {Promise<void>}
 */
function downloadFile(url, destPath, onProgress) {
    return new Promise((resolve, reject) => {
        const request = (currentUrl) => {
            https.get(currentUrl, { headers: { 'User-Agent': 'Minestaller/1.0.0' } }, (response) => {
                if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
                    request(response.headers.location);
                    return;
                }
                if (response.statusCode !== 200) {
                    reject(new Error(`Server returned status code: ${response.statusCode}`));
                    return;
                }
                const file = fs.createWriteStream(destPath);
                const totalBytes = parseInt(response.headers['content-length'], 10) || -1;
                let bytesRead = 0;

                response.on('data', (chunk) => {
                    bytesRead += chunk.length;
                    if (onProgress) onProgress(bytesRead, totalBytes);
                });

                response.pipe(file);

                file.on('finish', () => {
                    file.close();
                    resolve();
                });
            }).on('error', (err) => {
                fs.unlink(destPath, () => {});
                reject(err);
            });
        };
        request(url);
    });
}

module.exports = {
    GLOBAL_CONFIG_PATH,
    getSystemDrives,
    fetchJson,
    downloadFile
};
