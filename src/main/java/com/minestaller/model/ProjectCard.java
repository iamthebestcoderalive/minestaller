package com.minestaller.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectCard {
    private String id;
    private String slug;
    private String title;
    private String description;
    private String author;
    private String iconUrl;
    private List<String> categories = new ArrayList<>();
    private List<String> versions = new ArrayList<>();
    private String projectType; // "mod", "resourcepack", "shader"
    private long downloads;
    private String clientSide; // "required", "optional", "unsupported"
    private String serverSide; // "required", "optional", "unsupported"

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getVersions() { return versions; }
    public void setVersions(List<String> versions) { this.versions = versions; }

    public String getProjectType() { return projectType; }
    public void setProjectType(String projectType) { this.projectType = projectType; }

    public long getDownloads() { return downloads; }
    public void setDownloads(long downloads) { this.downloads = downloads; }

    public String getClientSide() { return clientSide; }
    public void setClientSide(String clientSide) { this.clientSide = clientSide; }

    public String getServerSide() { return serverSide; }
    public void setServerSide(String serverSide) { this.serverSide = serverSide; }

    @Override
    public String toString() {
        return title + " by " + author + " [" + projectType + "]";
    }
}
