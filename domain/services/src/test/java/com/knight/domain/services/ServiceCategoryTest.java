package com.knight.domain.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ServiceCategoryTest {

    @Test
    void shouldHavePaymentCategory() {
        assertThat(ServiceCategory.PAYMENT).isNotNull();
        assertThat(ServiceCategory.PAYMENT.name()).isEqualTo("PAYMENT");
    }

    @Test
    void shouldHaveReportingCategory() {
        assertThat(ServiceCategory.REPORTING).isNotNull();
        assertThat(ServiceCategory.REPORTING.name()).isEqualTo("REPORTING");
    }

    @Test
    void shouldHaveAdministrativeCategory() {
        assertThat(ServiceCategory.ADMINISTRATIVE).isNotNull();
        assertThat(ServiceCategory.ADMINISTRATIVE.name()).isEqualTo("ADMINISTRATIVE");
    }

    @Test
    void shouldHaveIntegrationCategory() {
        assertThat(ServiceCategory.INTEGRATION).isNotNull();
        assertThat(ServiceCategory.INTEGRATION.name()).isEqualTo("INTEGRATION");
    }

    @Test
    void shouldHaveCommunicationCategory() {
        assertThat(ServiceCategory.COMMUNICATION).isNotNull();
        assertThat(ServiceCategory.COMMUNICATION.name()).isEqualTo("COMMUNICATION");
    }

    @Test
    void shouldHaveFiveCategories() {
        assertThat(ServiceCategory.values()).hasSize(5);
    }

    @Test
    void shouldSupportValueOf() {
        assertThat(ServiceCategory.valueOf("PAYMENT")).isEqualTo(ServiceCategory.PAYMENT);
        assertThat(ServiceCategory.valueOf("REPORTING")).isEqualTo(ServiceCategory.REPORTING);
        assertThat(ServiceCategory.valueOf("ADMINISTRATIVE")).isEqualTo(ServiceCategory.ADMINISTRATIVE);
        assertThat(ServiceCategory.valueOf("INTEGRATION")).isEqualTo(ServiceCategory.INTEGRATION);
        assertThat(ServiceCategory.valueOf("COMMUNICATION")).isEqualTo(ServiceCategory.COMMUNICATION);
    }

    @Test
    void shouldThrowOnInvalidValueOf() {
        assertThatThrownBy(() -> ServiceCategory.valueOf("INVALID"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
