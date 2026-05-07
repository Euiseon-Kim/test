package com.example.ozr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {
    private String type;
    private String message;
    private int count;
    private Integer line;
}
