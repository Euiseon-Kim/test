package com.example.ozr.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfo {
    private String userId;
    private String name;
    private String department;
}
