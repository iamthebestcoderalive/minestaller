#!/bin/bash
# connect.sh
echo "=== Minestaller Agent Connection Setup ==="
AGENT_DIR="$HOME/minestaller-agent"
mkdir -p "$AGENT_DIR"
cd "$AGENT_DIR"

echo "Downloading agent files from GitHub..."
curl -sSL "https://raw.githubusercontent.com/barho/minestaller/main/server.js" -o server.js
curl -sSL "https://raw.githubusercontent.com/barho/minestaller/main/package.json" -o package.json

if command -v node &> /dev/null; then
    echo "Node.js detected. Installing dependencies and starting agent..."
    npm install
    node server.js
else
    echo "Node.js not detected."
    echo "Please install Node.js (https://nodejs.org) to run the Minestaller local helper agent."
    sleep 10
    exit 1
fi
