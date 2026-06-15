package com.minestaller.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minestaller.model.ProjectCard;
import com.minestaller.service.ModrinthClient.ModrinthDependency;
import com.minestaller.service.ModrinthClient.ProjectVersion;
import com.minestaller.service.ModrinthClient.VersionFile;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DownloadManager {

    private static final Gson gson = new Gson();
    private final ModrinthClient modrinthClient = new ModrinthClient();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public interface DownloadListener {
        void onDownloadStarted(String filename);
        void onDownloadProgress(String filename, long bytesRead, long totalBytes, double speedKbps);
        void onDownloadFinished(String filename, boolean success, String errorMsg);
    }

    public static class InstallTask {
        public String projectId;
        public String title;
        public String filename;
        public String downloadUrl;
        public String sha1;
        public String sha512;
        public String targetFolder; // "mods", "resourcepacks", "shaderpacks"
    }

    /**
     * Recursively resolve all required dependencies for a project.
     * Returns a list of InstallTasks (including the project itself).
     */
    public CompletableFuture<List<InstallTask>> resolveDependencies(
            ProjectCard project, String gameVersion, String loader) {
        return CompletableFuture.supplyAsync(() -> {
            List<InstallTask> tasks = new ArrayList<>();
            Set<String> resolvedProjectIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
            resolveRecursively(project.getId(), project.getTitle(), project.getProjectType(), gameVersion, loader, resolvedProjectIds, tasks);
            return tasks;
        });
    }

    private void resolveRecursively(String projectId, String fallbackTitle, String projectType,
                                    String gameVersion, String loader, Set<String> resolvedIds, List<InstallTask> tasks) {
        if (resolvedIds.contains(projectId)) {
            return;
        }
        resolvedIds.add(projectId);

        try {
            // Get version info for version/platform
            List<ProjectVersion> versions = modrinthClient.getVersions(projectId, gameVersion, loader).get();
            if (versions.isEmpty()) {
                // Try getting without loader (sometimes libraries are generic Java/Kotlin jars that don't tag a loader)
                versions = modrinthClient.getVersions(projectId, gameVersion, "Any").get();
            }

            if (!versions.isEmpty()) {
                ProjectVersion bestVersion = versions.get(0); // Pick latest release
                VersionFile fileToDownload = null;

                // Pick primary file, or first file
                for (VersionFile file : bestVersion.files) {
                    if (file.primary) {
                        fileToDownload = file;
                        break;
                    }
                }
                if (fileToDownload == null && !bestVersion.files.isEmpty()) {
                    fileToDownload = bestVersion.files.get(0);
                }

                if (fileToDownload != null) {
                    if ("modpack".equalsIgnoreCase(projectType)) {
                        try {
                            Path tempMrpack = Files.createTempFile("minestaller_", ".mrpack");
                            HttpRequest req = HttpRequest.newBuilder()
                                    .uri(URI.create(fileToDownload.url))
                                    .header("User-Agent", "Minestaller/1.0.0")
                                    .GET()
                                    .build();
                            HttpResponse<Path> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofFile(tempMrpack));
                            if (resp.statusCode() == 200) {
                                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tempMrpack.toFile())) {
                                    java.util.zip.ZipEntry entry = zip.getEntry("modrinth.index.json");
                                    if (entry != null) {
                                        try (InputStream is = zip.getInputStream(entry)) {
                                            JsonObject indexObj = gson.fromJson(new InputStreamReader(is), JsonObject.class);
                                            if (indexObj.has("files")) {
                                                JsonArray filesArr = indexObj.getAsJsonArray("files");
                                                for (JsonElement fEl : filesArr) {
                                                    JsonObject fObj = fEl.getAsJsonObject();
                                                    String filePath = fObj.get("path").getAsString();
                                                    JsonArray downloadsArr = fObj.getAsJsonArray("downloads");
                                                    if (downloadsArr.size() > 0) {
                                                        String downloadUrl = downloadsArr.get(0).getAsString();
                                                        String sha1 = "";
                                                        String sha512 = "";
                                                        if (fObj.has("hashes")) {
                                                            JsonObject hashObj = fObj.getAsJsonObject("hashes");
                                                            if (hashObj.has("sha1")) sha1 = hashObj.get("sha1").getAsString();
                                                            if (hashObj.has("sha512")) sha512 = hashObj.get("sha512").getAsString();
                                                        }
                                                        
                                                        InstallTask t = new InstallTask();
                                                        t.projectId = projectId;
                                                        t.title = filePath;
                                                        t.filename = filePath;
                                                        t.downloadUrl = downloadUrl;
                                                        t.sha1 = sha1;
                                                        t.sha512 = sha512;
                                                        t.targetFolder = "";
                                                        tasks.add(t);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Files.deleteIfExists(tempMrpack);
                        } catch (Exception e) {
                            System.err.println("Error downloading/parsing modpack index: " + e.getMessage());
                        }

                        InstallTask overrideTask = new InstallTask();
                        overrideTask.projectId = projectId;
                        overrideTask.title = fallbackTitle + " (OVERRIDES & CONFIGS)";
                        overrideTask.filename = "minestaller_temp_" + projectId + ".mrpack";
                        overrideTask.downloadUrl = fileToDownload.url;
                        overrideTask.sha1 = fileToDownload.sha1;
                        overrideTask.sha512 = fileToDownload.sha512;
                        overrideTask.targetFolder = "";
                        tasks.add(overrideTask);
                        return;
                    }

                    InstallTask task = new InstallTask();
                    task.projectId = projectId;
                    task.title = fallbackTitle;
                    task.filename = fileToDownload.filename;
                    task.downloadUrl = fileToDownload.url;
                    task.sha1 = fileToDownload.sha1;
                    task.sha512 = fileToDownload.sha512;

                    // Decide folder
                    if ("resourcepack".equalsIgnoreCase(projectType)) {
                        task.targetFolder = "resourcepacks";
                    } else if ("shader".equalsIgnoreCase(projectType)) {
                        task.targetFolder = "shaderpacks";
                    } else if ("datapack".equalsIgnoreCase(projectType)) {
                        task.targetFolder = "datapacks";
                    } else {
                        task.targetFolder = "mods";
                    }

                    tasks.add(task);

                    // Resolve dependencies
                    for (ModrinthDependency dep : bestVersion.dependencies) {
                        if ("required".equalsIgnoreCase(dep.dependencyType) && dep.projectId != null) {
                            // Fetch project name/slug for better UX logs
                            String depTitle = dep.projectId;
                            try {
                                JsonObject projObj = modrinthClient.getProject(dep.projectId).get();
                                if (projObj != null && projObj.has("title")) {
                                    depTitle = projObj.get("title").getAsString();
                                }
                            } catch (Exception ignored) {}

                            resolveRecursively(dep.projectId, depTitle, "mod", gameVersion, loader, resolvedIds, tasks);
                        }
                    }
                }
            } else {
                System.err.println("No matching version found for Modrinth project: " + fallbackTitle + " (" + projectId + ")");
            }
        } catch (Exception e) {
            System.err.println("Dependency resolution failed for project " + fallbackTitle + ": " + e.getMessage());
        }
    }

    /**
     * Download an install task to the targeted Minecraft folder directory.
     */
    public CompletableFuture<Boolean> downloadTask(String minecraftDir, InstallTask task, DownloadListener listener) {
        return CompletableFuture.supplyAsync(() -> {
            Path targetDir = Paths.get(minecraftDir).resolve(task.targetFolder);
            Path finalFile = targetDir.resolve(task.filename);
            Path tempFile = targetDir.resolve(task.filename + ".part");

            try {
                // Ensure directories exist
                Files.createDirectories(finalFile.getParent());

                if (listener != null) {
                    listener.onDownloadStarted(task.filename);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(task.downloadUrl))
                        .header("User-Agent", "Minestaller/1.0.0")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    throw new Exception("HTTP server responded with code: " + response.statusCode());
                }

                long totalBytes = response.headers().firstValueAsLong("content-length").orElse(-1L);
                long bytesRead = 0;

                long startTime = System.nanoTime();
                long lastUpdateTime = startTime;
                long bytesSinceLastUpdate = 0;
                double speedKbps = 0;

                try (InputStream is = response.body();
                     OutputStream os = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) {
                            throw new Exception("Download cancelled by user.");
                        }
                        os.write(buffer, 0, read);
                        bytesRead += read;
                        bytesSinceLastUpdate += read;

                        long now = System.nanoTime();
                        long duration = now - lastUpdateTime;
                        if (duration >= 200_000_000L) { // Update progress UI every 200ms
                            double seconds = duration / 1_000_000_000.0;
                            speedKbps = (bytesSinceLastUpdate / 1024.0) / seconds;
                            if (listener != null) {
                                listener.onDownloadProgress(task.filename, bytesRead, totalBytes, speedKbps);
                            }
                            lastUpdateTime = now;
                            bytesSinceLastUpdate = 0;
                        }
                    }
                }

                // Verify Checksums
                boolean checkValid = true;
                if (task.sha512 != null && !task.sha512.isEmpty()) {
                    String localSha512 = calculateHash(tempFile, "SHA-512");
                    if (!task.sha512.equalsIgnoreCase(localSha512)) {
                        checkValid = false;
                        throw new Exception("SHA-512 hash mismatch. Download might be corrupted.");
                    }
                } else if (task.sha1 != null && !task.sha1.isEmpty()) {
                    String localSha1 = calculateHash(tempFile, "SHA-1");
                    if (!task.sha1.equalsIgnoreCase(localSha1)) {
                        checkValid = false;
                        throw new Exception("SHA-1 hash mismatch. Download might be corrupted.");
                    }
                }

                if (checkValid) {
                    // Overwrite existing mod file if it exists, otherwise rename
                    if (Files.exists(finalFile)) {
                        Files.delete(finalFile);
                    }
                    Files.move(tempFile, finalFile);

                    if (listener != null) {
                        listener.onDownloadFinished(task.filename, true, null);
                    }
                    return true;
                }
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}

                if (listener != null) {
                    listener.onDownloadFinished(task.filename, false, e.getMessage());
                }
            }
            return false;
        });
    }

    private static String calculateHash(Path file, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static final List<String> extractedFilesList = Collections.synchronizedList(new ArrayList<>());

    private void extractModpackOverrides(String minecraftDir, Path mrpackPath, String projectId) {
        extractedFilesList.clear();
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(mrpackPath.toFile())) {
            Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
            Path root = Paths.get(minecraftDir);
            
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                
                String targetPathStr = null;
                if (name.startsWith("overrides/")) {
                    targetPathStr = name.substring("overrides/".length());
                } else if (name.startsWith("client-overrides/")) {
                    targetPathStr = name.substring("client-overrides/".length());
                }
                
                if (targetPathStr != null && !targetPathStr.isEmpty()) {
                    Path destPath = root.resolve(targetPathStr);
                    if (entry.isDirectory()) {
                        Files.createDirectories(destPath);
                    } else {
                        Files.createDirectories(destPath.getParent());
                        try (InputStream is = zip.getInputStream(entry);
                             OutputStream os = Files.newOutputStream(destPath)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = is.read(buffer)) != -1) {
                                os.write(buffer, 0, read);
                            }
                        }
                        String relPath = targetPathStr.replace("\\", "/");
                        extractedFilesList.add(relPath);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting modpack overrides: " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(mrpackPath);
            } catch (Exception ignored) {}
        }
    }
}
