package com.knight.application.rest.accountgroups.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountGroupRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description
) {}
