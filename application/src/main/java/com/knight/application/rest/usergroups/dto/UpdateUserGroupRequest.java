package com.knight.application.rest.usergroups.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserGroupRequest(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    String name,

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    String description
) {}
