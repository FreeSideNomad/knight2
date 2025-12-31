package com.knight.domain.services;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.platform.sharedkernel.Action;
import com.knight.platform.sharedkernel.ActionType;
import com.knight.platform.sharedkernel.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ServiceRegistryTest {

    private TestPaymentService paymentService;
    private TestReportingService reportingService;
    private TestAdminService adminService;
    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        paymentService = new TestPaymentService();
        reportingService = new TestReportingService();
        adminService = new TestAdminService();
        registry = new ServiceRegistry(List.of(paymentService, reportingService, adminService));
    }

    @Test
    void shouldRegisterAllServices() {
        assertThat(registry.getServiceCount()).isEqualTo(3);
    }

    @Test
    void shouldGetServiceByIdentifier() {
        Optional<Service> service = registry.getService("test-payment");
        assertThat(service).isPresent();
        assertThat(service.get()).isSameAs(paymentService);
    }

    @Test
    void shouldReturnEmptyForUnknownIdentifier() {
        Optional<Service> service = registry.getService("unknown-service");
        assertThat(service).isEmpty();
    }

    @Test
    void shouldReturnEmptyForNullIdentifier() {
        Optional<Service> service = registry.getService(null);
        assertThat(service).isEmpty();
    }

    @Test
    void shouldGetAllServices() {
        List<Service> allServices = registry.getAllServices();
        assertThat(allServices).hasSize(3);
        assertThat(allServices).containsExactlyInAnyOrder(paymentService, reportingService, adminService);
    }

    @Test
    void shouldReturnImmutableListFromGetAllServices() {
        List<Service> allServices = registry.getAllServices();
        assertThatThrownBy(() -> allServices.add(new TestPaymentService()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldGetServicesByCategory() {
        List<Service> paymentServices = registry.getServicesByCategory(ServiceCategory.PAYMENT);
        assertThat(paymentServices).hasSize(1);
        assertThat(paymentServices).contains(paymentService);

        List<Service> reportingServices = registry.getServicesByCategory(ServiceCategory.REPORTING);
        assertThat(reportingServices).hasSize(1);
        assertThat(reportingServices).contains(reportingService);

        List<Service> adminServices = registry.getServicesByCategory(ServiceCategory.ADMINISTRATIVE);
        assertThat(adminServices).hasSize(1);
        assertThat(adminServices).contains(adminService);
    }

    @Test
    void shouldReturnEmptyListForCategoryWithNoServices() {
        List<Service> integrationServices = registry.getServicesByCategory(ServiceCategory.INTEGRATION);
        assertThat(integrationServices).isEmpty();
    }

    @Test
    void shouldCheckIfServiceExists() {
        assertThat(registry.hasService("test-payment")).isTrue();
        assertThat(registry.hasService("test-reporting")).isTrue();
        assertThat(registry.hasService("test-admin")).isTrue();
        assertThat(registry.hasService("nonexistent")).isFalse();
    }

    @Test
    void shouldGetAllServiceInfo() {
        List<ServiceInfo> allInfo = registry.getAllServiceInfo();
        assertThat(allInfo).hasSize(3);

        List<String> identifiers = allInfo.stream()
            .map(ServiceInfo::identifier)
            .toList();
        assertThat(identifiers).containsExactlyInAnyOrder(
            "test-payment", "test-reporting", "test-admin"
        );
    }

    @Test
    void shouldReturnImmutableListFromGetAllServiceInfo() {
        List<ServiceInfo> allInfo = registry.getAllServiceInfo();
        assertThatThrownBy(() -> allInfo.add(new ServiceInfo(
            "new-service",
            ServiceCategory.PAYMENT,
            "New Service",
            "Description",
            Collections.emptyList()
        ))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldThrowOnDuplicateServiceIdentifiers() {
        TestPaymentService duplicate1 = new TestPaymentService();
        TestPaymentService duplicate2 = new TestPaymentService();

        assertThatThrownBy(() -> new ServiceRegistry(List.of(duplicate1, duplicate2)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate service identifier: test-payment");
    }

    @Test
    void shouldHandleEmptyServicesList() {
        ServiceRegistry emptyRegistry = new ServiceRegistry(Collections.emptyList());

        assertThat(emptyRegistry.getServiceCount()).isZero();
        assertThat(emptyRegistry.getAllServices()).isEmpty();
        assertThat(emptyRegistry.getService("any")).isEmpty();
        assertThat(emptyRegistry.hasService("any")).isFalse();
    }

    @Test
    void shouldHandleSingleService() {
        ServiceRegistry singleRegistry = new ServiceRegistry(List.of(paymentService));

        assertThat(singleRegistry.getServiceCount()).isOne();
        assertThat(singleRegistry.getService("test-payment")).isPresent();
        assertThat(singleRegistry.getServicesByCategory(ServiceCategory.PAYMENT)).hasSize(1);
    }

    // Test service implementations
    static class TestPaymentService extends Service {
        @Override
        public String getServiceIdentifier() {
            return "test-payment";
        }

        @Override
        public ServiceCategory getServiceCategory() {
            return ServiceCategory.PAYMENT;
        }

        @Override
        public String getDisplayName() {
            return "Test Payment Service";
        }

        @Override
        public String getDescription() {
            return "Test payment service for unit testing";
        }

        @Override
        public boolean isAccountEligible(ClientAccount account) {
            return account.isActive();
        }

        @Override
        public List<Action> getActions() {
            return List.of(
                Action.of(ServiceType.DIRECT, "test-payment", "payment", ActionType.CREATE),
                Action.of(ServiceType.DIRECT, "test-payment", "payment", ActionType.VIEW)
            );
        }
    }

    static class TestReportingService extends Service {
        @Override
        public String getServiceIdentifier() {
            return "test-reporting";
        }

        @Override
        public ServiceCategory getServiceCategory() {
            return ServiceCategory.REPORTING;
        }

        @Override
        public String getDisplayName() {
            return "Test Reporting Service";
        }

        @Override
        public String getDescription() {
            return "Test reporting service for unit testing";
        }

        @Override
        public boolean isAccountEligible(ClientAccount account) {
            return account.isActive() || account.isClosed();
        }

        @Override
        public List<Action> getActions() {
            return List.of(
                Action.of(ServiceType.DIRECT, "test-reporting", "report", ActionType.VIEW)
            );
        }
    }

    static class TestAdminService extends Service {
        @Override
        public String getServiceIdentifier() {
            return "test-admin";
        }

        @Override
        public ServiceCategory getServiceCategory() {
            return ServiceCategory.ADMINISTRATIVE;
        }

        @Override
        public String getDisplayName() {
            return "Test Admin Service";
        }

        @Override
        public String getDescription() {
            return "Test admin service for unit testing";
        }

        @Override
        public boolean isAccountEligible(ClientAccount account) {
            return account.isActive();
        }

        @Override
        public List<Action> getActions() {
            return List.of(
                Action.of(ServiceType.ADMIN, "test-admin", "settings", ActionType.MANAGE)
            );
        }
    }
}
