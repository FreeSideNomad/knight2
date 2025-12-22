package com.knight.application.rest.policies.dto;

/**
 * Request to update a permission policy.
 */
public record UpdatePermissionPolicyRequest(
    String action,          // action pattern
    String resource,        // resource pattern
    String effect,          // ALLOW or DENY
    String description
) {}
