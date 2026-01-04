package com.knight.indirectportal.services.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class CreateUserRequest {
    private String loginId;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private boolean sendInvitation = true;
}
