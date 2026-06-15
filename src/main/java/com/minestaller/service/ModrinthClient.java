package com.minestaller.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minestaller.model.ProjectCard;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModrinthClient {

    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "Minestaller/1.0.0 (admin@minestaller.org)";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new Gson();

    public static class VersionFile {
        public String url;
        public String filename;
        public String sha1;
        public String sha512;
        public boolean primary;
    }

    public static class ModrinthDependency {
        public String projectId;
        public String versionId;
        public String dependencyType; // "required", "optional", "embedded", "incompatible"
    }

    public static class ProjectVersion {
        public String id;
        public String name;
        public String versionNumber;
        public List<VersionFile> files = new ArrayList<>();
        public List<ModrinthDependency> dependencies = new ArrayList<>();
    }

    /**
     * Search Modrinth projects concurrently.
     */
    public CompletableFuture<List<ProjectCard>> search(String query, String gameVersion, String loader, String type, String category) {
        return CompletableFuture.supplyAsync(() -> {
            List<ProjectCard> results = new ArrayList<>();
            try {
                StringBuilder urlBuilder = new StringBuilder(BASE_URL).append("/search");
                List<String> params = new ArrayList<>();

                if (query != null && !query.trim().isEmpty()) {
                    params.add("query=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8));
                }

                // Build facets array
                // Format: [["versions:1.20.1"], ["categories:fabric"], ["project_type:mod"]]
                List<String> facetList = new ArrayList<>();
                if (gameVersion != null && !gameVersion.equalsIgnoreCase("Unknown") && !gameVersion.equalsIgnoreCase("Any")) {
                    facetList.add("[\"versions:" + gameVersion + "\"]");
                }
                if (loader != null && !loader.equalsIgnoreCase("Unknown") && !loader.equalsIgnoreCase("Vanilla") && !loader.equalsIgnoreCase("Any")) {
                    facetList.add("[\"categories:" + loader.toLowerCase() + "\"]");
                }
                if (type != null && !type.isEmpty()) {
                    facetList.add("[\"project_type:" + type.toLowerCase() + "\"]");
                }
                if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("Any") && !category.equalsIgnoreCase("Any Category")) {
                    facetList.add("[\"categories:" + category.trim().toLowerCase().replace(" ", "") + "\"]");
                }

                if (!facetList.isEmpty()) {
                    String facetsJson = "[" + String.join(",", facetList) + "]";
                    params.add("facets=" + URLEncoder.encode(facetsJson, StandardCharsets.UTF_8));
                }

                params.add("limit=20");

                if (!params.isEmpty()) {
                    urlBuilder.append("?").append(String.join("&", params));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlBuilder.toString()))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject responseObj = gson.fromJson(response.body(), JsonObject.class);
                    if (responseObj.has("hits")) {
                        JsonArray hits = responseObj.getAsJsonArray("hits");
                        for (JsonElement hitEl : hits) {
                            JsonObject hit = hitEl.getAsJsonObject();
                            ProjectCard card = new ProjectCard();
                            card.setId(hit.get("project_id").getAsString());
                            card.setSlug(hit.get("slug").getAsString());
                            card.setTitle(hit.get("title").getAsString());
                            card.setDescription(hit.has("description") ? hit.get("description").getAsString() : "");
                            card.setAuthor(hit.has("author") ? hit.get("author").getAsString() : "Unknown");
                            card.setIconUrl(hit.has("icon_url") && !hit.get("icon_url").isJsonNull() ? hit.get("icon_url").getAsString() : null);
                            card.setProjectType(hit.get("project_type").getAsString());
                            card.setDownloads(hit.has("downloads") ? hit.get("downloads").getAsLong() : 0);

                            if (hit.has("categories")) {
                                for (JsonElement cat : hit.getAsJsonArray("categories")) {
                                    card.getCategories().add(cat.getAsString());
                                }
                            }
                            if (hit.has("versions")) {
                                for (JsonElement ver : hit.getAsJsonArray("versions")) {
                                    card.getVersions().add(ver.getAsString());
                                }
                            }
                            results.add(card);
                        }
                    }
                } else {
                    System.err.println("Modrinth search failed with code: " + response.statusCode());
                }

            } catch (Exception e) {
                System.err.println("Error searching Modrinth: " + e.getMessage());
            }
            return results;
        });
    }

    /**
     * Get matching versions of a project.
     */
    public CompletableFuture<List<ProjectVersion>> getVersions(String projectIdOrSlug, String gameVersion, String loader) {
        return CompletableFuture.supplyAsync(() -> {
            List<ProjectVersion> versions = new ArrayList<>();
            try {
                StringBuilder urlBuilder = new StringBuilder(BASE_URL)
                        .append("/project/")
                        .append(projectIdOrSlug)
                        .append("/version");

                List<String> params = new ArrayList<>();
                if (gameVersion != null && !gameVersion.equalsIgnoreCase("Unknown") && !gameVersion.equalsIgnoreCase("Any")) {
                    params.add("game_versions=" + URLEncoder.encode("[\"" + gameVersion + "\"]", StandardCharsets.UTF_8));
                }
                if (loader != null && !loader.equalsIgnoreCase("Unknown") && !loader.equalsIgnoreCase("Vanilla") && !loader.equalsIgnoreCase("Any")) {
                    params.add("loaders=" + URLEncoder.encode("[\"" + loader.toLowerCase() + "\"]", StandardCharsets.UTF_8));
                }

                if (!params.isEmpty()) {
                    urlBuilder.append("?").append(String.join("&", params));
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlBuilder.toString()))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonArray arr = gson.fromJson(response.body(), JsonArray.class);
                    for (JsonElement el : arr) {
                        JsonObject obj = el.getAsJsonObject();
                        ProjectVersion ver = new ProjectVersion();
                        ver.id = obj.get("id").getAsString();
                        ver.name = obj.get("name").getAsString();
                        ver.versionNumber = obj.get("version_number").getAsString();

                        if (obj.has("files")) {
                            for (JsonElement fileEl : obj.getAsJsonArray("files")) {
                                JsonObject fileObj = fileEl.getAsJsonObject();
                                VersionFile vf = new VersionFile();
                                vf.url = fileObj.get("url").getAsString();
                                vf.filename = fileObj.get("filename").getAsString();
                                vf.primary = fileObj.get("primary").getAsBoolean();

                                if (fileObj.has("hashes")) {
                                    JsonObject hashObj = fileObj.getAsJsonObject("hashes");
                                    if (hashObj.has("sha1")) vf.sha1 = hashObj.get("sha1").getAsString();
                                    if (hashObj.has("sha512")) vf.sha512 = hashObj.get("sha512").getAsString();
                                }
                                ver.files.add(vf);
                            }
                        }

                        if (obj.has("dependencies")) {
                            for (JsonElement depEl : obj.getAsJsonArray("dependencies")) {
                                JsonObject depObj = depEl.getAsJsonObject();
                                ModrinthDependency md = new ModrinthDependency();
                                md.projectId = depObj.has("project_id") && !depObj.get("project_id").isJsonNull() ? depObj.get("project_id").getAsString() : null;
                                md.versionId = depObj.has("version_id") && !depObj.get("version_id").isJsonNull() ? depObj.get("version_id").getAsString() : null;
                                md.dependencyType = depObj.has("dependency_type") ? depObj.get("dependency_type").getAsString() : "required";
                                ver.dependencies.add(md);
                            }
                        }
                        versions.add(ver);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error fetching versions: " + e.getMessage());
            }
            return versions;
        });
    }

    /**
     * Resolve project details from project ID (to get name, slug, etc.)
     */
    public CompletableFuture<JsonObject> getProject(String projectIdOrSlug) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = BASE_URL + "/project/" + projectIdOrSlug;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return gson.fromJson(response.body(), JsonObject.class);
                }
            } catch (Exception e) {
                System.err.println("Error fetching project: " + e.getMessage());
            }
            return null;
        });
    }
}
