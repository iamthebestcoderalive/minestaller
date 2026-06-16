# Minestaller

Minestaller is a retro-futuristic Minecraft management platform. It consists of:
1. **Java Terminal Client**: A cross-platform Java desktop application styled as a cyber-terminal to browse Modrinth database entries. [NOT THE MAIN PROJECT]
2. **Node.js Companion Web Agent & Dashboard**: A local web companion server and dashboard interface that scans local profile folders, updates world NBT attributes, manages datapacks, toggles shaders/resourcepacks, and downloads Modrinth resources directly into local folders.

---

## Technical Stack

### Java Terminal Client
- **Language**: Java 17+
- **Build System**: Maven (Local portable instance included in project)
- **UI Framework**: Swing styled with **FlatLaf** for a terminal feel, kinda looks pro and silly at the same time.
- **API Client**: Asynchronous `HttpClient`
- **JSON Parsing**: Gson

### Node.js Web Companion Agent
- **Platform**: Node.js & Express
- **Parsing Engines**: Custom binary NBT parser (level.dat modifier)
- **Frontend UI**: Vanilla HTML5, CSS (Multiple Themes), and modular JavaScript
- **Connectors**: Multi-platform installer scripts (`connect.bat`, `connect.sh`, `connect.ps1`)

---

## Features

1. **Retro Terminal Cold-Boot Sequence**: Simulation of a cyber-terminal loading modules before establishing a directory connection.
2. **System Diagnostics (Directory Scan)**:
   - Verifies the selected directory structure (`options.txt`, `mods/`, `resourcepacks/`, `shaderpacks/`).
   - Automatically detects the Minecraft version and active mod loader (Fabric, Forge, NeoForge, Quilt).
3. **Interactive Companion Dashboard**:
   - Modern, responsive web browser-based dashboard interface.
   - **Direct Profile Controls**: Turn shaders and resource packs on/off individually or in bulk.
   - **Profile Migration**: Port configuration files, saves, mods, resourcepacks, and shaders between launcher directories.
   - **Automated Modpack Installer**: Resolves and installs Modrinth modpacks (`.mrpack`) with client overrides.
4. **"Connect With Device" Setup Wizard**:
   - Interactive pop-up tutorial providing custom batch, bash, and powershell setup scripts.
   - Info tooltips explaining the function of each command (e.g. directory changes, server boots, fallback repositories).
5. **Bloxy Theme**:
   - This theme is inspired by the Bloxy Cola (From Roblox).
   - Looks cool in my opinion.

---

## Project Structure

```
minestaller/
│
├── routes/                       # Express router modules
│   ├── config.js                 # Global configs & scanning APIs
│   ├── instance.js               # Active profile metadata & toggles
│   ├── migrater.js               # Folder routing, detection, & migration
│   ├── mods.js                   # Modrinth modpack resolution & downloads daemon
│   └── worlds.js                 # Save files & world NBT/datapack APIs
│
├── utils/                        # Backend utility modules
│   ├── file.js                   # Recursive file actions & folder sizes
│   ├── minecraft.js              # options.txt & iris.properties parser
│   ├── nbt.js                    # Binary level.dat NBT reader/writer engine
│   └── sys.js                    # Core networking, drives & download streams
│
├── public/                       # Companion Dashboard static client files
│   ├── index.html                # Main UI page & scripts
│   └── connect.*                 # Windows / macOS / Linux connection scripts
│
├── src/                          # Java Terminal application source code
├── pom.xml                       # Maven build configuration
├── server.js                     # Node.js backend entry bootstrapper
├── start.bat                     # Windows startup script launcher
└── minestaller_config.json       # Local client configuration cache
```

---

## How to Build and Run

### Running the Node.js Web Companion
The companion dashboard connects to the local computer via Port 5000.

#### On Windows (Double-Click)
Double-click `start.bat` in the root folder. It will install dependencies and launch `http://localhost:5000` automatically.

#### From Console
Open a terminal in the project directory and run:
```bash
npm install
npm start
```

---

### Building the Java Client
Since Maven is packaged locally inside this directory, you do not need Maven installed on your machine. You only need a Java 17+ JDK.

#### Build Executable Fat JAR
Open a terminal in the project directory and run:
```powershell
.\apache-maven-3.9.6\bin\mvn.cmd clean package
```
This compiles the code and builds an executable JAR file containing all dependencies in the `target/` directory:
- `target/minestaller-1.0.0.jar`

#### Run the Application
Run the packaged JAR file using Java:
```powershell
java -jar target/minestaller-1.0.0.jar
```

---

## Verification & Testing

To test directory scanning, you can set up a mock Minecraft instance directory on your desktop:

1. Create a folder named `mock_instance`.
2. Inside `mock_instance`, create an empty file named `options.txt` and folders named `mods`, `resourcepacks`, and `shaderpacks`.
3. Create a file named `instance.cfg` inside `mock_instance` with the following content:
   ```properties
   minecraftVersion=1.20.1
   iconKey=fabric
   name=My Fabric Instance
   ```
4. Start the application, click **Browse**, select the `mock_instance` folder, and click **Connect**.
5. The terminal console will print real-time diagnostic logs, detect Minecraft version `1.20.1` and loader `Fabric`, and render lists of local mods (currently empty).
6. Click **Establish Uplink Database** to transition to the database browser, pre-populated with version `1.20.1` and loader `Fabric` filters.
7. Search for any mod (e.g. `Sodium`), select it, click **Initiate Uplink Download**, authorize dependencies, and watch the installation write queue download them into your mock instance!
