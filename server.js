/**
 * MINESTALLER AGENT // Node.js Companion Server
 * Main Bootstrapper Entry Point
 */
const express = require('express');
const path = require('path');

const app = express();
const PORT = 5000;

// ==========================================
// Middleware Configuration
// ==========================================
app.use((req, res, next) => {
    res.header('Access-Control-Allow-Origin', '*');
    res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept, x-filename');
    if (req.method === 'OPTIONS') {
        return res.sendStatus(200);
    }
    next();
});

app.use(express.json({ limit: '50mb' }));
app.use(express.static(path.join(__dirname, 'public')));

// ==========================================
// Mount Categorized Route Handlers
// ==========================================
app.use(require('./routes/config'));
app.use(require('./routes/instance'));
app.use(require('./routes/worlds'));
app.use(require('./routes/mods'));
app.use(require('./routes/migrater'));
app.use(require('./routes/backup'));

// ==========================================
// Start Web Companion Server
// ==========================================
app.listen(PORT, () => {
    console.log(`Backend server running at http://localhost:${PORT}`);
});
