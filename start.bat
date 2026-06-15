@echo off
if not exist node_modules (
    echo Installing dependencies...
    call npm install
)
echo Starting Minestaller Agent...
start http://localhost:5000
node server.js
pause