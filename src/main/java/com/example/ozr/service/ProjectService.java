package com.example.ozr.service;

import com.example.ozr.config.AppProperties;
import com.example.ozr.dto.ProjectDto;
import com.example.ozr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final AppProperties appProperties;

    public List<ProjectDto> getAllProjects() {
        return appProperties.getProjects().stream()
                .map(p -> new ProjectDto(
                        p.getProjectId(),
                        p.getProjectPath(),
                        p.getDefaultBranch(),
                        p.getAllowedFiles()))
                .collect(Collectors.toList());
    }

    public List<String> getAllowedFiles(String projectId) {
        AppProperties.ProjectConfig project = appProperties.findProject(projectId);
        if (project == null) {
            throw new BusinessException("존재하지 않는 프로젝트입니다: " + projectId);
        }
        return project.getAllowedFiles();
    }
}
