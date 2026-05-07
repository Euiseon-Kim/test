package com.example.ozr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ProjectDto {
    private String projectId;
    private String projectPath;
    private String defaultBranch;
    private List<String> allowedFiles;
}
