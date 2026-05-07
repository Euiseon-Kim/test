package com.example.ozr.controller;

import com.example.ozr.dto.ProjectDto;
import com.example.ozr.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<List<ProjectDto>> listProjects() {
        return ResponseEntity.ok(projectService.getAllProjects());
    }

    @GetMapping("/{projectId}/files")
    public ResponseEntity<List<String>> listFiles(@PathVariable String projectId) {
        return ResponseEntity.ok(projectService.getAllowedFiles(projectId));
    }
}
