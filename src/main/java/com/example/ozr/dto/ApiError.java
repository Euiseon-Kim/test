package com.example.ozr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ApiError {
    private int status;
    private String message;
    private LocalDateTime timestamp;

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, LocalDateTime.now());
    }
}
