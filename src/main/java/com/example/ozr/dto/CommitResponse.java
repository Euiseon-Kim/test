package com.example.ozr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitResponse {
    private boolean success;
    private String branch;
    private String commitId;
    private String message;
}
