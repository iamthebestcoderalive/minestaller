#!/bin/bash
# connect.sh
echo "=== Minestaller Agent Connection Setup ==="
AGENT_DIR="$HOME/minestaller-agent"
mkdir -p "$AGENT_DIR"

# Check if agent is already installed — skip download if it is
if [ -f "$AGENT_DIR/server.js" ] && [ -f "$AGENT_DIR/package.json" ]; then
    echo "Minestaller agent already installed. Skipping download."
else
    echo "Downloading Minestaller companion files from GitHub..."

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

    echo "Download complete."
fi

cd "$AGENT_DIR"

if command -v node &> /dev/null; then
    echo "Starting Minestaller agent..."
    npm install
    node server.js
else
    echo "Node.js not detected."
    echo "Please install Node.js from https://nodejs.org then run this command again."
    sleep 10
    exit 1
fi
