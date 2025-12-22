package com.knight.application.rest.serviceprofiles.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProfileSearchRequest record validation.
 */
class ProfileSearchRequestTest {

    @Test
    @DisplayName("should set default page when negative")
    void shouldSetDefaultPageWhenNegative() {
        ProfileSearchRequest request = new ProfileSearchRequest(
            "srf:123456789",
            null,
            true,
            Set.of("SERVICING"),
            -5,  // negative page
            20
        );

        assertThat(request.page()).isEqualTo(0);
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("should set default size when zero or negative")
    void shouldSetDefaultSizeWhenZeroOrNegative() {
        ProfileSearchRequest request = new ProfileSearchRequest(
            "srf:123456789",
            null,
            true,
            null,
            0,
            0  // zero size
        );

        assertThat(request.page()).isEqualTo(0);
        assertThat(request.size()).isEqualTo(20);
    }

    @Test
    @DisplayName("should cap size at 100 when too large")
    void shouldCapSizeAt100WhenTooLarge() {
        ProfileSearchRequest request = new ProfileSearchRequest(
            null,
            "search name",
            false,
            Set.of("ONLINE"),
            5,
            200  // too large
        );

        assertThat(request.page()).isEqualTo(5);
        assertThat(request.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("should preserve valid values")
    void shouldPreserveValidValues() {
        ProfileSearchRequest request = new ProfileSearchRequest(
            "srf:123456789",
            "Test Client",
            true,
            Set.of("SERVICING", "ONLINE"),
            10,
            50
        );

        assertThat(request.clientId()).isEqualTo("srf:123456789");
        assertThat(request.clientName()).isEqualTo("Test Client");
        assertThat(request.primaryOnly()).isTrue();
        assertThat(request.profileTypes()).containsExactlyInAnyOrder("SERVICING", "ONLINE");
        assertThat(request.page()).isEqualTo(10);
        assertThat(request.size()).isEqualTo(50);
    }
}
