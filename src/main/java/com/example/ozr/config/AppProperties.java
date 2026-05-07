package com.example.ozr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private GitLab gitlab = new GitLab();
    private List<ProjectConfig> projects = new ArrayList<>();
    private long maxFileSizeBytes = 10 * 1024 * 1024L;
    private Jwt jwt = new Jwt();

    @Data
    public static class GitLab {
        private String baseUrl = "http://gitlab.example.com";
        private String apiUrl = "http://gitlab.example.com/api/v4";
        private String botToken = "";
    }

    @Data
    public static class ProjectConfig {
        private String projectId;
        private String projectPath;
        private String defaultBranch = "main";
        private List<String> allowedFiles = new ArrayList<>();
    }

    @Data
    public static class Jwt {
        private String secret = "changeme-use-long-256bit-secret-in-production-env-only";
        private long expirationMs = 86400000L;
    }

    public ProjectConfig findProject(String projectId) {
        return projects.stream()
                .filter(p -> p.getProjectId().equals(projectId))
                .findFirst()
                .orElse(null);
    }

    public boolean isAllowedFile(String projectId, String filePath) {
        ProjectConfig project = findProject(projectId);
        if (project == null) return false;
        return project.getAllowedFiles().contains(filePath);
    }
}
