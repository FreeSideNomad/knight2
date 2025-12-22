package com.knight.application.rest.policies.dto;

/**
 * Request to create a permission policy.
 */
public record CreatePermissionPolicyRequest(
    String subject,         // user:uuid, group:uuid, or role:NAME
    String action,          // action pattern e.g., "payments.ach-payments.*"
    String resource,        // resource pattern e.g., "*" or "CAN_DDA:DDA:*"
    String effect,          // ALLOW or DENY
    String description
) {}
