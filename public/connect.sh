#!/bin/bash
# connect.sh
echo "=== Minestaller Agent Connection Setup ==="
AGENT_DIR="$HOME/minestaller-agent"
mkdir -p "$AGENT_DIR"
cd "$AGENT_DIR"

echo "Downloading lightweight companion files from GitHub..."

BASE_URL="https://raw.githubusercontent.com/iamthebestcoderalive/minestaller/main"
FILES=(
    "server.js"
    "package.json"
    "public/index.html"
    "routes/config.js"
    "routes/instance.js"
    "routes/worlds.js"
    "routes/mods.js"
    "routes/migrater.js"
    "utils/nbt.js"
    "utils/file.js"
    "utils/minecraft.js"
    "utils/sys.js"
)

for f in "${FILES[@]}"; do
    dest="$AGENT_DIR/$f"
    mkdir -p "$(dirname "$dest")"
    curl -sSL "$BASE_URL/$f" -o "$dest"
done

if command -v node &> /dev/null; then
    echo "Node.js detected. Starting Minestaller companion agent..."
    npm install
    node server.js
else
    echo "Node.js not detected."
    echo "Please install Node.js (https://nodejs.org) to run the Minestaller local helper agent."
    sleep 10
    exit 1
fi
