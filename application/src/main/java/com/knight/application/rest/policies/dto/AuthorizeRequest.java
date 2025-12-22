package com.knight.application.rest.policies.dto;

import java.util.Set;

/**
 * Request to check authorization.
 */
public record AuthorizeRequest(
    String action,          // action to check
    String resourceId       // optional resource ID
) {}
