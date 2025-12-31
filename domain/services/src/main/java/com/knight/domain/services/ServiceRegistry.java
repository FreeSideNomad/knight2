package com.knight.domain.services;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Registry for all services in the system.
 *
 * <p>Uses Spring dependency injection to automatically discover and register
 * all {@link Service} implementations annotated with {@code @Component}.
 *
 * <p>Provides methods to retrieve services by identifier or category.
 */
@Component
public class ServiceRegistry {

    private final Map<String, Service> servicesByIdentifier;
    private final List<Service> allServices;

    /**
     * Creates a new ServiceRegistry with the given services.
     *
     * @param services list of all service implementations (injected by Spring)
     */
    public ServiceRegistry(List<Service> services) {
        this.allServices = List.copyOf(services);
        this.servicesByIdentifier = services.stream()
            .collect(Collectors.toMap(
                Service::getServiceIdentifier,
                s -> s,
                (existing, replacement) -> {
                    throw new IllegalStateException(
                        "Duplicate service identifier: " + existing.getServiceIdentifier());
                }
            ));
    }

    /**
     * Gets a service by its identifier.
     *
     * @param identifier the service identifier
     * @return an Optional containing the service if found
     */
    public Optional<Service> getService(String identifier) {
        return Optional.ofNullable(servicesByIdentifier.get(identifier));
    }

    /**
     * Gets all registered services.
     *
     * @return immutable list of all services
     */
    public List<Service> getAllServices() {
        return allServices;
    }

    /**
     * Gets all services of a specific category.
     *
     * @param category the service category to filter by
     * @return immutable list of services in the given category
     */
    public List<Service> getServicesByCategory(ServiceCategory category) {
        return allServices.stream()
            .filter(s -> s.getServiceCategory() == category)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets the total number of registered services.
     *
     * @return the service count
     */
    public int getServiceCount() {
        return allServices.size();
    }

    /**
     * Checks if a service with the given identifier exists.
     *
     * @param identifier the service identifier
     * @return true if the service exists
     */
    public boolean hasService(String identifier) {
        return servicesByIdentifier.containsKey(identifier);
    }

    /**
     * Gets service information for all registered services.
     *
     * @return list of service information records
     */
    public List<ServiceInfo> getAllServiceInfo() {
        return allServices.stream()
            .map(Service::getServiceInfo)
            .collect(Collectors.toUnmodifiableList());
    }
}
