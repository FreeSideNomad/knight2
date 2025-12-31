package com.knight.domain.services;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.platform.sharedkernel.Action;

import java.util.List;

/**
 * Abstract base class for all services in the system.
 *
 * <p>All services must extend this class and implement the required methods
 * to provide service metadata and eligibility checking.
 *
 * <p>Services are automatically discovered and registered via Spring's
 * dependency injection when annotated with {@code @Component}.
 */
public abstract class Service {

    /**
     * Returns the unique identifier for this service.
     * This identifier is used in Action URNs and for service lookup.
     *
     * @return the service identifier (e.g., "payment", "reporting")
     */
    public abstract String getServiceIdentifier();

    /**
     * Returns the category of this service for grouping.
     *
     * @return the service category
     */
    public abstract ServiceCategory getServiceCategory();

    /**
     * Returns the human-readable display name for this service.
     *
     * @return the display name (e.g., "Payment Service", "Reporting Service")
     */
    public abstract String getDisplayName();

    /**
     * Returns a description of this service's functionality.
     *
     * @return the service description
     */
    public abstract String getDescription();

    /**
     * Determines if a client account is eligible for this service.
     *
     * <p>Each service implements its own eligibility rules based on:
     * <ul>
     *   <li>Account status (active, suspended, closed)</li>
     *   <li>Account type (direct vs indirect)</li>
     *   <li>Service-specific configurations</li>
     *   <li>Feature flags and permissions</li>
     * </ul>
     *
     * @param account the client account to check eligibility for
     * @return true if the account is eligible for this service
     */
    public abstract boolean isAccountEligible(ClientAccount account);

    /**
     * Returns the list of actions available for this service.
     *
     * <p>These actions are used for permission management and define
     * what operations can be performed on this service.
     *
     * @return immutable list of actions
     */
    public abstract List<Action> getActions();

    /**
     * Returns the complete service information as an immutable record.
     *
     * @return the service information
     */
    public ServiceInfo getServiceInfo() {
        return new ServiceInfo(
            getServiceIdentifier(),
            getServiceCategory(),
            getDisplayName(),
            getDescription(),
            getActions()
        );
    }
}
