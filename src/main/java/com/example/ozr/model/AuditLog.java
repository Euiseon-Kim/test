package com.example.ozr.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String requesterId;

    @Column(nullable = false)
    private String requesterName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String projectPath;

    @Column(nullable = false)
    private String filePath;

    private String sourceBranch;
    private String createdBranch;
    private String commitId;

    @Column(nullable = false)
    private String validationStatus;

    @Column(length = 2000)
    private String changeReason;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 2000)
    private String errorMessage;

    @PrePersist
    public void prePersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
