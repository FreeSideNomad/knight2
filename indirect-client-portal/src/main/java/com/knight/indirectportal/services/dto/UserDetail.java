package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDetail {
    private String userId;
    private String email;
    private String name;
    private List<String> roles;
    private String status;
    private String statusDisplayName;
    private Instant lastLogin;
    private Instant createdAt;
}
