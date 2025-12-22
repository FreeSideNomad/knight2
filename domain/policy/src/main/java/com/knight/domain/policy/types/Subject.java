package com.knight.domain.policy.types;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Subject value object representing who a policy applies to.
 * Can be a user, group, or role.
 * URN format: user:{uuid}, group:{uuid}, role:{roleName}
 */
public record Subject(SubjectType type, String identifier) {

    public enum SubjectType {
        USER,   // user:{uuid}
        GROUP,  // group:{uuid}
        ROLE    // role:{roleName}
    }

    private static final Pattern ROLE_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    public Subject {
        if (type == null) {
            throw new IllegalArgumentException("Subject type cannot be null");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Subject identifier cannot be null or blank");
        }

        // Validate identifier format based on type
        if (type == SubjectType.USER || type == SubjectType.GROUP) {
            try {
                UUID.fromString(identifier);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid UUID for " + type + ": " + identifier);
            }
        } else if (type == SubjectType.ROLE) {
            if (!ROLE_NAME.matcher(identifier).matches()) {
                throw new IllegalArgumentException("Invalid role name: " + identifier);
            }
        }
    }

    /**
     * Parse a subject from URN format (user:uuid, group:uuid, role:name).
     */
    public static Subject fromUrn(String urn) {
        if (urn == null || !urn.contains(":")) {
            throw new IllegalArgumentException("Invalid subject URN: " + urn);
        }
        String[] parts = urn.split(":", 2);
        SubjectType type = switch (parts[0].toLowerCase()) {
            case "user" -> SubjectType.USER;
            case "group" -> SubjectType.GROUP;
            case "role" -> SubjectType.ROLE;
            default -> throw new IllegalArgumentException("Unknown subject type: " + parts[0]);
        };
        return new Subject(type, parts[1]);
    }

    public static Subject user(String userId) {
        return new Subject(SubjectType.USER, userId);
    }

    public static Subject user(UUID userId) {
        return new Subject(SubjectType.USER, userId.toString());
    }

    public static Subject group(String groupId) {
        return new Subject(SubjectType.GROUP, groupId);
    }

    public static Subject group(UUID groupId) {
        return new Subject(SubjectType.GROUP, groupId.toString());
    }

    public static Subject role(String roleName) {
        return new Subject(SubjectType.ROLE, roleName);
    }

    public String toUrn() {
        return type.name().toLowerCase() + ":" + identifier;
    }
}
