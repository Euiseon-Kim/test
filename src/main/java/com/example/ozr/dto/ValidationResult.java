package com.example.ozr.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ValidationResult {
    private String status;
    private boolean canCommit;
    @Builder.Default
    private List<ValidationIssue> issues = new ArrayList<>();
    private boolean changed;
    private boolean sanitizedAvailable;
}
