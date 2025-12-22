package com.knight.domain.policy.types;

/**
 * Predefined system roles with default action patterns.
 * These roles define the base permissions for users.
 */
public enum PredefinedRole {

    SECURITY_ADMIN("SECURITY_ADMIN", "security.*", "All security-related actions"),
    SERVICE_ADMIN("SERVICE_ADMIN", "*", "Full access to all services and settings"),
    READER("READER", "*.view", "View all resources"),
    CREATOR("CREATOR", "*.create,*.update,*.delete", "Create, update, and delete resources"),
    APPROVER("APPROVER", "*.approve", "Approve pending items");

    private final String roleName;
    private final String actionPatterns;
    private final String description;

    PredefinedRole(String roleName, String actionPatterns, String description) {
        this.roleName = roleName;
        this.actionPatterns = actionPatterns;
        this.description = description;
    }

    public String roleName() { return roleName; }
    public String[] actionPatterns() { return actionPatterns.split(","); }
    public String description() { return description; }

    /**
     * Parse role from name string.
     */
    public static PredefinedRole fromName(String name) {
        for (PredefinedRole role : values()) {
            if (role.roleName.equals(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown predefined role: " + name);
    }

    /**
     * Check if a role name is a predefined role.
     */
    public static boolean isPredefinedRole(String name) {
        for (PredefinedRole role : values()) {
            if (role.roleName.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
