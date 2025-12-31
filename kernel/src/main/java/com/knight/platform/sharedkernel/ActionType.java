package com.knight.platform.sharedkernel;

/**
 * Enumeration of action types for permission action URNs.
 * Represents the type of operation that can be performed.
 */
public enum ActionType {
    /**
     * View/read access to a resource
     */
    VIEW,

    /**
     * Create new resources
     */
    CREATE,

    /**
     * Update existing resources
     */
    UPDATE,

    /**
     * Delete resources
     */
    DELETE,

    /**
     * Approve pending actions or workflows
     */
    APPROVE,

    /**
     * Full management access (includes all operations)
     */
    MANAGE
}
