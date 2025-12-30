package com.knight.application.rest.indirect;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank String loginId,
    @NotBlank @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName,
    Set<String> roles
) {}
