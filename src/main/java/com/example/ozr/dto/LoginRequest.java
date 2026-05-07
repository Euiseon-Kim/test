package com.example.ozr.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "userId는 필수입니다.")
    private String userId;

    @NotBlank(message = "password는 필수입니다.")
    private String password;
}
