package com.minestaller.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstanceScanner {

    public static class ScanResult {
        public boolean isValid = false;
        public String path = "";
        public String minecraftVersion = "Unknown";
        public String loader = "Unknown";
        public List<String> mods = new ArrayList<>();
        public List<String> resourcePacks = new ArrayList<>();
        public List<String> shaders = new ArrayList<>();
        public List<String> logs = new ArrayList<>(); // Verbose log messages for real-time console feedback
    }

    public static ScanResult scan(String dirPath) {
        ScanResult result = new ScanResult();
        result.path = dirPath;

        if (dirPath == null || dirPath.trim().isEmpty()) {
            result.logs.add("ERROR: Target path is empty.");
            return result;
        }

        Path root = Paths.get(dirPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            result.logs.add("ERROR: Specified path does not exist or is not a directory: " + dirPath);
            return result;
        }

        result.logs.add("SYSTEM: Initializing diagnostic scan on " + root.toAbsolutePath());

        // 1. Validation Checks
        boolean hasOptionsTxt = Files.exists(root.resolve("options.txt"));
        boolean hasMods = Files.exists(root.resolve("mods"));
        boolean hasResourcePacks = Files.exists(root.resolve("resourcepacks"));
        boolean hasShaderPacks = Files.exists(root.resolve("shaderpacks"));

        if (!hasOptionsTxt && !hasMods && !hasResourcePacks && !hasShaderPacks) {
            result.logs.add("WARNING: Directory does not contain typical Minecraft structure (options.txt, mods, resourcepacks, shaderpacks).");
            result.logs.add("SYSTEM: Manual override might be required, but will proceed scanning files.");
        } else {
            result.isValid = true;
            result.logs.add("SUCCESS: Minecraft directory structure validated.");
        }

        // 2. Discover Platform & Version Heuristics
        // Try Prism/MultiMC instance.cfg
        boolean detected = detectFromInstanceCfg(root, result);
        if (!detected) {
            // Try mmc-pack.json
            detected = detectFromMmcPackJson(root, result);
        }
        if (!detected) {
            // Try launcher_profiles.json
            detected = detectFromLauncherProfiles(root, result);
        }
        if (!detected) {
            // Try folder name fallback
            detected = detectFromFolderName(root, result);
        }
        if (!detected) {
            result.logs.add("DIAGNOSTIC: Auto-configs not found. Scanning mods folder fallback...");
            detectFromModJars(root, result);
        }

        // 3. Scan Mods Folder
        scanModsFolder(root, result);

        // 4. Scan Resource Packs
        scanResourcePacks(root, result);

        // 5. Scan Shaders
        scanShaderPacks(root, result);

        result.logs.add("SYSTEM: Scan completed. Version: " + result.minecraftVersion + ", Loader: " + result.loader);
        result.logs.add("SYSTEM: Found " + result.mods.size() + " mods, " + result.resourcePacks.size() + " resource packs, and " + result.shaders.size() + " shaders.");

        return result;
    }

    private static boolean detectFromLauncherProfiles(Path root, ScanResult result) {
        // Look up parent directories for launcher_profiles.json
        Path current = root;
        Path profilesPath = null;
        for (int i = 0; i < 4; i++) {
            if (current == null) break;
            Path test = current.resolve("launcher_profiles.json");
            if (Files.exists(test)) {
                profilesPath = test;
                break;
            }
            current = current.getParent();
        }

        // If not found, fallback to default AppData path
        if (profilesPath == null) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                Path test = Paths.get(appData, ".minecraft", "launcher_profiles.json");
                if (Files.exists(test)) {
                    profilesPath = test;
                }
            }
        }

        if (profilesPath != null) {
            result.logs.add("DIAGNOSTIC: Found launcher_profiles.json. Scanning profiles...");
            try (Reader reader = Files.newBufferedReader(profilesPath)) {
                Gson gson = new Gson();
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                if (obj.has("profiles")) {
                    JsonObject profiles = obj.getAsJsonObject("profiles");
                    String absoluteTarget = root.toAbsolutePath().toString().replace("\\", "/").toLowerCase();

                    for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
                        JsonObject profile = entry.getValue().getAsJsonObject();
                        if (profile.has("gameDir")) {
                            String gameDir = profile.get("gameDir").getAsString().replace("\\", "/").toLowerCase();
                            // If directories match
                            if (gameDir.equals(absoluteTarget) || absoluteTarget.endsWith("/" + gameDir) || gameDir.endsWith("/" + absoluteTarget)) {
                                if (profile.has("lastVersionId")) {
                                    String verId = profile.get("lastVersionId").getAsString().toLowerCase();
                                    result.logs.add("DIAGNOSTIC: Profile matched gameDir with versionId: " + verId);
                                    
                                    // Extract Version & Loader
                                    String loaderType = "Vanilla";
                                    if (verId.contains("fabric")) loaderType = "Fabric";
                                    else if (verId.contains("neoforge")) loaderType = "NeoForge";
                                    else if (verId.contains("forge")) loaderType = "Forge";
                                    else if (verId.contains("quilt")) loaderType = "Quilt";

                                    String mcVer = null;
                                    Matcher m = Pattern.compile("1\\.\\d+(\\.\\d+)?").matcher(verId);
                                    if (m.find()) {
                                        mcVer = m.group();
                                    }

                                    if (mcVer != null) {
                                        result.minecraftVersion = mcVer;
                                        result.loader = loaderType;
                                        result.logs.add("SUCCESS: Detected version " + mcVer + " and loader " + loaderType + " from launcher_profiles.json");
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                result.logs.add("WARNING: Failed to parse launcher_profiles.json: " + e.getMessage());
            }
        }
        return false;
    }

    private static boolean detectFromFolderName(Path root, ScanResult result) {
        String name = root.getFileName().toString().toLowerCase();
        
        String detectedVersion = null;
        Matcher m = Pattern.compile("1\\.\\d+(\\.\\d+)?").matcher(name);
        if (m.find()) {
            detectedVersion = m.group();
        }

        String detectedLoader = null;
        if (name.contains("fabric")) detectedLoader = "Fabric";
        else if (name.contains("neoforge")) detectedLoader = "NeoForge";
        else if (name.contains("forge")) detectedLoader = "Forge";
        else if (name.contains("quilt")) detectedLoader = "Quilt";

        boolean detected = false;
        if (detectedVersion != null && result.minecraftVersion.equals("Unknown")) {
            result.minecraftVersion = detectedVersion;
            result.logs.add("SUCCESS: Extracted version guess [" + detectedVersion + "] from folder name: " + root.getFileName());
            detected = true;
        }
        if (detectedLoader != null && result.loader.equals("Unknown")) {
            result.loader = detectedLoader;
            result.logs.add("SUCCESS: Extracted loader guess [" + detectedLoader + "] from folder name: " + root.getFileName());
            detected = true;
        }
        return detected;
    }

    private static boolean detectFromInstanceCfg(Path root, ScanResult result) {
        Path cfgPath = root.resolve("instance.cfg");
        if (!Files.exists(cfgPath)) {
            // Check parent directory in case we are inside the .minecraft folder of a Prism instance
            cfgPath = root.getParent() != null ? root.getParent().resolve("instance.cfg") : cfgPath;
        }

        if (Files.exists(cfgPath)) {
            result.logs.add("DIAGNOSTIC: Found instance.cfg. Parsing metadata...");
            try {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(cfgPath)) {
                    props.load(is);
                }

                String mcVer = props.getProperty("minecraftVersion");
                if (mcVer == null) {
                    mcVer = props.getProperty("IntendedVersion");
                }

                String loaderType = "Vanilla";
                String icon = props.getProperty("iconKey");
                String name = props.getProperty("name");

                // Check for Fabric/Forge/NeoForge
                if (icon != null) {
                    if (icon.toLowerCase().contains("fabric")) loaderType = "Fabric";
                    else if (icon.toLowerCase().contains("forge")) loaderType = "Forge";
                    else if (icon.toLowerCase().contains("neoforge")) loaderType = "NeoForge";
                    else if (icon.toLowerCase().contains("quilt")) loaderType = "Quilt";
                }

                // Look through all keys for loader info
                for (String key : props.stringPropertyNames()) {
                    if (key.toLowerCase().contains("loader") || key.toLowerCase().contains("addon")) {
                        String val = props.getProperty(key);
                        if (val != null) {
                            if (val.toLowerCase().contains("fabric")) loaderType = "Fabric";
                            else if (val.toLowerCase().contains("forge")) loaderType = "Forge";
                            else if (val.toLowerCase().contains("neoforge")) loaderType = "NeoForge";
                            else if (val.toLowerCase().contains("quilt")) loaderType = "Quilt";
                        }
                    }
                }

                if (mcVer != null) {
                    result.minecraftVersion = mcVer;
                    result.loader = loaderType;
                    result.logs.add("SUCCESS: Detected version " + mcVer + " and loader " + loaderType + " from instance.cfg");
                    return true;
                }
            } catch (Exception e) {
                result.logs.add("WARNING: Failed to read instance.cfg: " + e.getMessage());
            }
        }
        return false;
    }

    private static boolean detectFromMmcPackJson(Path root, ScanResult result) {
        Path jsonPath = root.resolve("mmc-pack.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = root.getParent() != null ? root.getParent().resolve("mmc-pack.json") : jsonPath;
        }

        if (Files.exists(jsonPath)) {
            result.logs.add("DIAGNOSTIC: Found mmc-pack.json. Parsing components...");
            try (Reader reader = Files.newBufferedReader(jsonPath)) {
                Gson gson = new Gson();
                JsonObject obj = gson.fromJson(reader, JsonObject.class);
                if (obj.has("components")) {
                    JsonArray components = obj.getAsJsonArray("components");
                    String mcVer = null;
                    String loader = "Vanilla";

                    for (JsonElement compEl : components) {
                        JsonObject comp = compEl.getAsJsonObject();
                        if (comp.has("uid")) {
                            String uid = comp.get("uid").getAsString();
                            String version = comp.has("version") ? comp.get("version").getAsString() : "";

                            if ("net.minecraft".equals(uid)) {
                                mcVer = version;
                            } else if ("net.fabricmc.fabric-loader".equals(uid)) {
                                loader = "Fabric";
                            } else if ("net.minecraftforge".equals(uid)) {
                                loader = "Forge";
                            } else if ("org.quiltmc.quilt-loader".equals(uid)) {
                                loader = "Quilt";
                            } else if ("net.neoforged".equals(uid)) {
                                loader = "NeoForge";
                            }
                        }
                    }

                    if (mcVer != null) {
                        result.minecraftVersion = mcVer;
                        result.loader = loader;
                        result.logs.add("SUCCESS: Detected version " + mcVer + " and loader " + loader + " from mmc-pack.json");
                        return true;
                    }
                }
            } catch (Exception e) {
                result.logs.add("WARNING: Failed to read mmc-pack.json: " + e.getMessage());
            }
        }
        return false;
    }

    private static void detectFromModJars(Path root, ScanResult result) {
        Path modsPath = root.resolve("mods");
        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            return;
        }

        result.logs.add("DIAGNOSTIC: Scanning JAR metadata in /mods to detect version/platform...");

        int fabricCount = 0;
        int forgeCount = 0;
        int quiltCount = 0;
        int neoForgeCount = 0;
        Set<String> potentialMcVersions = new HashSet<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath, "*.jar")) {
            for (Path jarPath : stream) {
                try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
                    ZipEntry fabricJson = zipFile.getEntry("fabric.mod.json");
                    ZipEntry modsToml = zipFile.getEntry("META-INF/mods.toml");
                    ZipEntry quiltJson = zipFile.getEntry("quilt.mod.json");

                    if (quiltJson != null) {
                        quiltCount++;
                    } else if (fabricJson != null) {
                        fabricCount++;
                        // Try to parse fabric.mod.json for Minecraft dependency
                        try (InputStream is = zipFile.getInputStream(fabricJson)) {
                            Gson gson = new Gson();
                            JsonObject obj = gson.fromJson(new InputStreamReader(is), JsonObject.class);
                            if (obj.has("depends")) {
                                JsonObject depends = obj.getAsJsonObject("depends");
                                if (depends.has("minecraft")) {
                                    String dep = depends.get("minecraft").getAsString();
                                    // Extract simple version numbers e.g. 1.20.1
                                    Matcher m = Pattern.compile("1\\.\\d+(\\.\\d+)?").matcher(dep);
                                    if (m.find()) {
                                        potentialMcVersions.add(m.group());
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (modsToml != null) {
                        // Check if it's NeoForge or Forge
                        boolean isNeoForge = false;
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(modsToml)))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.toLowerCase().contains("neoforge")) {
                                    isNeoForge = true;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}

                        if (isNeoForge) {
                            neoForgeCount++;
                        } else {
                            forgeCount++;
                        }
                    }
                } catch (Exception e) {
                    // Ignore corrupted or invalid ZIP files
                }
            }
        } catch (Exception e) {
            result.logs.add("WARNING: Error iterating mods directory: " + e.getMessage());
        }

        // Determine Loader
        if (quiltCount > 0) {
            result.loader = "Quilt";
        } else if (fabricCount > 0) {
            result.loader = "Fabric";
        } else if (neoForgeCount > 0) {
            result.loader = "NeoForge";
        } else if (forgeCount > 0) {
            result.loader = "Forge";
        }

        // Determine Version if we found any guesses
        if (!potentialMcVersions.isEmpty()) {
            // Grab the most common or just first guess
            result.minecraftVersion = potentialMcVersions.iterator().next();
            result.logs.add("SUCCESS: Extracted version guess [" + result.minecraftVersion + "] from Fabric mod dependencies.");
        }
    }

    private static void scanModsFolder(Path root, ScanResult result) {
        Path modsPath = root.resolve("mods");
        if (!Files.exists(modsPath) || !Files.isDirectory(modsPath)) {
            return;
        }

        result.logs.add("SYSTEM: Scanning mods folder...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsPath, "*.jar")) {
            for (Path jarPath : stream) {
                String displayName = jarPath.getFileName().toString();
                // Attempt to read display name from inside the jar
                try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
                    ZipEntry fabricJson = zipFile.getEntry("fabric.mod.json");
                    ZipEntry modsToml = zipFile.getEntry("META-INF/mods.toml");

                    if (fabricJson != null) {
                        try (InputStream is = zipFile.getInputStream(fabricJson)) {
                            JsonObject obj = new Gson().fromJson(new InputStreamReader(is), JsonObject.class);
                            if (obj.has("name")) {
                                String name = obj.get("name").getAsString();
                                String version = obj.has("version") ? obj.get("version").getAsString() : "";
                                displayName = name + " (" + version + ")";
                            }
                        }
                    } else if (modsToml != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(modsToml)))) {
                            String line;
                            String name = null;
                            String version = null;
                            while ((line = reader.readLine()) != null) {
                                line = line.trim();
                                if (line.startsWith("displayName")) {
                                    name = getValueFromTomlLine(line);
                                } else if (line.startsWith("version")) {
                                    version = getValueFromTomlLine(line);
                                }
                            }
                            if (name != null) {
                                displayName = name + (version != null ? " (" + version + ")" : "");
                            }
                        }
                    }
                } catch (Exception ignored) {}

                result.mods.add(displayName);
            }
        } catch (Exception e) {
            result.logs.add("WARNING: Failed to read mods directory: " + e.getMessage());
        }
    }

    private static String getValueFromTomlLine(String line) {
        int idx = line.indexOf('=');
        if (idx != -1) {
            String val = line.substring(idx + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"")) {
                return val.substring(1, val.length() - 1);
            }
            return val;
        }
        return null;
    }

    private static void scanResourcePacks(Path root, ScanResult result) {
        Path rpPath = root.resolve("resourcepacks");
        if (!Files.exists(rpPath) || !Files.isDirectory(rpPath)) {
            return;
        }

        result.logs.add("SYSTEM: Scanning resourcepacks folder...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rpPath)) {
            for (Path path : stream) {
                String packName = path.getFileName().toString();
                String desc = "";
                if (Files.isDirectory(path)) {
                    Path metaPath = path.resolve("pack.mcmeta");
                    if (Files.exists(metaPath)) {
                        desc = parsePackMcMeta(metaPath);
                    }
                } else if (packName.toLowerCase().endsWith(".zip")) {
                    try (ZipFile zipFile = new ZipFile(path.toFile())) {
                        ZipEntry entry = zipFile.getEntry("pack.mcmeta");
                        if (entry != null) {
                            try (InputStream is = zipFile.getInputStream(entry)) {
                                desc = parsePackMcMetaFromStream(is);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                result.resourcePacks.add(packName + (desc.isEmpty() ? "" : " - " + desc));
            }
        } catch (Exception e) {
            result.logs.add("WARNING: Failed to read resource packs: " + e.getMessage());
        }
    }

    private static void scanShaderPacks(Path root, ScanResult result) {
        Path shaderPath = root.resolve("shaderpacks");
        if (!Files.exists(shaderPath) || !Files.isDirectory(shaderPath)) {
            return;
        }

        result.logs.add("SYSTEM: Scanning shaderpacks folder...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderPath)) {
            for (Path path : stream) {
                result.shaders.add(path.getFileName().toString());
            }
        } catch (Exception e) {
            result.logs.add("WARNING: Failed to read shader packs: " + e.getMessage());
        }
    }

    private static String parsePackMcMeta(Path metaPath) {
        try (InputStream is = Files.newInputStream(metaPath)) {
            return parsePackMcMetaFromStream(is);
        } catch (Exception e) {
            return "";
        }
    }

    private static String parsePackMcMetaFromStream(InputStream is) {
        try {
            JsonObject obj = new Gson().fromJson(new InputStreamReader(is), JsonObject.class);
            if (obj.has("pack")) {
                JsonObject packObj = obj.getAsJsonObject("pack");
                if (packObj.has("description")) {
                    JsonElement descEl = packObj.get("description");
                    if (descEl.isJsonObject()) {
                        // Sometimes description is a raw text component
                        JsonObject descObj = descEl.getAsJsonObject();
                        if (descObj.has("text")) {
                            return descObj.get("text").getAsString();
                        }
                    } else {
                        return descEl.getAsString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }
}
