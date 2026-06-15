# Minestaller (Uplink Wizard)

Minestaller is a cross-platform Java desktop application styled as a retro-futuristic hacker terminal. It helps players scan local Minecraft instances, analyze loaded mods, and download new mods, resource packs, and shaders directly from Modrinth with automated dependency resolution and checksum verification.

---

## Technical Stack
- **Language**: Java 17+
- **Build System**: Maven (Local portable instance included in project)
- **UI Framework**: Swing styled with the **FlatLaf** Dark / Cyberpunk look-and-feel
- **API Client**: Native `HttpClient` utilizing asynchronous CompletableFuture calls
- **JSON Parsing**: Gson

---

## Features

1. **Cold-Boot Sequence**: Simulation of a terminal loading modules before establishing a directory connection.
2. **System Diagnostics (Directory Scan)**:
   - Verifies the selected directory structure (`options.txt`, `mods/`, `resourcepacks/`, `shaderpacks/`).
   - Parses MultiMC/Prism config files (`instance.cfg`, `mmc-pack.json`) and scans mod JARs (`fabric.mod.json`, `mods.toml`) to automatically detect the Minecraft version and active mod loader (Fabric, Forge, NeoForge, Quilt).
   - Provides manual override dropdown selectors in case automatic detection fails.
3. **Matrix Search Panel (Modrinth Database)**:
   - Queries the Modrinth v2 search endpoint.
   - Filters search queries by type (Mods, Resource Packs, Shaders), Minecraft version, and mod loader.
   - Beautiful split-pane view with search hits on the left, and rich details (author, downloads count, icon, description, and dependency prompts) on the right.
4. **Uplink Write Queue (Installation Hub)**:
   - Resolves all recursive required dependencies dynamically.
   - Prompts for authorization with a clear list of all items in the download queue.
   - Sequential background download queue showing individual file progress, transfer speed, and checksum (SHA-1/SHA-512) validation logs.

---

## How to Build and Run

Since Maven is packaged locally inside this directory, you do not need Maven installed on your machine. You only need a Java 17+ JDK.

### Build Executable Fat JAR
Open a terminal in the project directory and run:
```powershell
.\apache-maven-3.9.6\bin\mvn.cmd clean package
```
This compiles the code and builds an executable JAR file containing all dependencies in the `target/` directory:
- `target/minestaller-1.0.0.jar`

### Run the Application
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
