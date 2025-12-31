package com.knight.platform.sharedkernel;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a permission action URN.
 *
 * <p>Action URN format: {@code [service_type]:[service]:[resource_type]:[action_type]}
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code direct:client-portal:profile:view}</li>
 *   <li>{@code indirect:indirect-portal:client:update}</li>
 *   <li>{@code bank:payor-enrolment:enrolment:approve}</li>
 *   <li>{@code admin:user-management:user:manage}</li>
 * </ul>
 */
public final class Action {

    private static final String URN_SEPARATOR = ":";
    private static final int SEGMENT_COUNT = 4;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*$");

    private final ServiceType serviceType;
    private final String service;
    private final String resourceType;
    private final ActionType actionType;

    private Action(ServiceType serviceType, String service, String resourceType, ActionType actionType) {
        this.serviceType = Objects.requireNonNull(serviceType, "serviceType cannot be null");
        this.service = Objects.requireNonNull(service, "service cannot be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.actionType = Objects.requireNonNull(actionType, "actionType cannot be null");
    }

    /**
     * Creates an Action from a URN string.
     *
     * @param urn the action URN string (e.g., "direct:client-portal:profile:view")
     * @return the Action instance
     * @throws IllegalArgumentException if the URN is invalid
     */
    public static Action of(String urn) {
        Objects.requireNonNull(urn, "URN cannot be null");

        String[] segments = urn.split(URN_SEPARATOR);
        if (segments.length != SEGMENT_COUNT) {
            throw new IllegalArgumentException(
                "Invalid Action URN format. Expected 4 segments separated by ':' but got " +
                segments.length + ". URN: " + urn);
        }

        ServiceType serviceType = parseServiceType(segments[0], urn);
        String service = validateName(segments[1], "service", urn);
        String resourceType = validateName(segments[2], "resourceType", urn);
        ActionType actionType = parseActionType(segments[3], urn);

        return new Action(serviceType, service, resourceType, actionType);
    }

    /**
     * Creates an Action from individual components.
     *
     * @param serviceType the service type
     * @param service the service name
     * @param resourceType the resource type
     * @param actionType the action type
     * @return the Action instance
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public static Action of(ServiceType serviceType, String service, String resourceType, ActionType actionType) {
        validateName(service, "service", null);
        validateName(resourceType, "resourceType", null);
        return new Action(serviceType, service, resourceType, actionType);
    }

    private static ServiceType parseServiceType(String value, String urn) {
        try {
            return ServiceType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid service type '" + value + "' in URN. Valid types: DIRECT, INDIRECT, BANK, ADMIN. URN: " + urn);
        }
    }

    private static ActionType parseActionType(String value, String urn) {
        try {
            return ActionType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid action type '" + value + "' in URN. Valid types: VIEW, CREATE, UPDATE, DELETE, APPROVE, MANAGE. URN: " + urn);
        }
    }

    private static String validateName(String value, String fieldName, String urn) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(
                fieldName + " cannot be empty" + (urn != null ? ". URN: " + urn : ""));
        }
        if (!NAME_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Invalid " + fieldName + " '" + value + "'. Must match pattern: [a-z][a-z0-9-]*" +
                (urn != null ? ". URN: " + urn : ""));
        }
        return value;
    }

    /**
     * Returns the service type.
     */
    public ServiceType serviceType() {
        return serviceType;
    }

    /**
     * Returns the service name.
     */
    public String service() {
        return service;
    }

    /**
     * Returns the resource type.
     */
    public String resourceType() {
        return resourceType;
    }

    /**
     * Returns the action type.
     */
    public ActionType actionType() {
        return actionType;
    }

    /**
     * Returns the URN string representation.
     */
    public String urn() {
        return serviceType.name().toLowerCase() + URN_SEPARATOR +
               service + URN_SEPARATOR +
               resourceType + URN_SEPARATOR +
               actionType.name().toLowerCase();
    }

    @Override
    public String toString() {
        return urn();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;
        return serviceType == action.serviceType &&
               Objects.equals(service, action.service) &&
               Objects.equals(resourceType, action.resourceType) &&
               actionType == action.actionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceType, service, resourceType, actionType);
    }
}
