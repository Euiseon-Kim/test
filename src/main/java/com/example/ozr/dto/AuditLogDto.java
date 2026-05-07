package com.example.ozr.dto;

import com.example.ozr.model.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogDto {

    private Long id;
    private String requesterId;
    private String requesterName;
    private String department;
    private String projectId;
    private String projectPath;
    private String filePath;
    private String sourceBranch;
    private String createdBranch;
    private String commitId;
    private String validationStatus;
    private String changeReason;
    private LocalDateTime timestamp;
    private boolean success;
    private String errorMessage;

    public static AuditLogDto from(AuditLog log) {
        AuditLogDto dto = new AuditLogDto();
        dto.id = log.getId();
        dto.requesterId = log.getRequesterId();
        dto.requesterName = log.getRequesterName();
        dto.department = log.getDepartment();
        dto.projectId = log.getProjectId();
        dto.projectPath = log.getProjectPath();
        dto.filePath = log.getFilePath();
        dto.sourceBranch = log.getSourceBranch();
        dto.createdBranch = log.getCreatedBranch();
        dto.commitId = log.getCommitId();
        dto.validationStatus = log.getValidationStatus();
        dto.changeReason = log.getChangeReason();
        dto.timestamp = log.getTimestamp();
        dto.success = log.isSuccess();
        dto.errorMessage = log.getErrorMessage();
        return dto;
    }
}
