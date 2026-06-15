package com.minestaller.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {
    private static final String CONFIG_FILENAME = "minestaller_config.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String lastMinecraftDir = "";
    
    public static class ModpackState {
        public String installedModpackId = "";
        public String installedModpackTitle = "";
        public java.util.List<String> installedModpackFiles = new java.util.ArrayList<>();
    }

    private java.util.Map<String, ModpackState> instances = new java.util.HashMap<>();

    public java.util.Map<String, ModpackState> getInstances() {
        if (instances == null) {
            instances = new java.util.HashMap<>();
        }
        return instances;
    }

    public ModpackState getModpackState(String instancePath) {
        if (instancePath == null) return null;
        String key = new java.io.File(instancePath).getAbsolutePath().replace("\\", "/").toLowerCase();
        return getInstances().get(key);
    }

    public void setModpackState(String instancePath, ModpackState state) {
        if (instancePath == null) return;
        String key = new java.io.File(instancePath).getAbsolutePath().replace("\\", "/").toLowerCase();
        if (state == null) {
            getInstances().remove(key);
        } else {
            getInstances().put(key, state);
        }
    }

    public String getLastMinecraftDir() {
        return lastMinecraftDir;
    }

    public void setLastMinecraftDir(String lastMinecraftDir) {
        this.lastMinecraftDir = lastMinecraftDir;
    }

    public static ConfigManager load() {
        Path configPath = getConfigPath();
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ConfigManager config = gson.fromJson(reader, ConfigManager.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                System.err.println("Error reading config: " + e.getMessage());
            }
        }
        return new ConfigManager();
    }

    public void save() {
        Path configPath = getConfigPath();
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(this, writer);
            }
        } catch (Exception e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }

    private static Path getConfigPath() {
        // First try local directory, if not, write to user home
        Path localPath = Paths.get(CONFIG_FILENAME);
        try {
            // Test write permission in local dir
            Files.writeString(localPath, Files.exists(localPath) ? Files.readString(localPath) : "");
            return localPath;
        } catch (Exception e) {
            // Fallback to user home
            return Paths.get(System.getProperty("user.home"), ".minestaller", CONFIG_FILENAME);
        }
    }
}
