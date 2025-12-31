package com.knight.domain.services;

import com.knight.platform.sharedkernel.Action;
import com.knight.platform.sharedkernel.ActionType;
import com.knight.platform.sharedkernel.ServiceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ServiceInfoTest {

    private static final String VALID_IDENTIFIER = "payment";
    private static final ServiceCategory VALID_CATEGORY = ServiceCategory.PAYMENT;
    private static final String VALID_DISPLAY_NAME = "Payment Service";
    private static final String VALID_DESCRIPTION = "Process payments";
    private static final List<Action> VALID_ACTIONS = List.of(
        Action.of(ServiceType.DIRECT, "payment", "transaction", ActionType.CREATE),
        Action.of(ServiceType.DIRECT, "payment", "transaction", ActionType.VIEW)
    );

    @Test
    void shouldCreateServiceInfoWithAllFields() {
        ServiceInfo info = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        assertThat(info.identifier()).isEqualTo(VALID_IDENTIFIER);
        assertThat(info.category()).isEqualTo(VALID_CATEGORY);
        assertThat(info.displayName()).isEqualTo(VALID_DISPLAY_NAME);
        assertThat(info.description()).isEqualTo(VALID_DESCRIPTION);
        assertThat(info.actions()).hasSize(2);
    }

    @Test
    void shouldMakeActionsListImmutable() {
        ServiceInfo info = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        assertThatThrownBy(() -> info.actions().add(
            Action.of(ServiceType.DIRECT, "payment", "transaction", ActionType.DELETE)
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldAcceptEmptyActionsList() {
        ServiceInfo info = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            Collections.emptyList()
        );

        assertThat(info.actions()).isEmpty();
    }

    @Test
    void shouldThrowWhenIdentifierIsNull() {
        assertThatThrownBy(() -> new ServiceInfo(
            null,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("identifier cannot be null");
    }

    @Test
    void shouldThrowWhenCategoryIsNull() {
        assertThatThrownBy(() -> new ServiceInfo(
            VALID_IDENTIFIER,
            null,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("category cannot be null");
    }

    @Test
    void shouldThrowWhenDisplayNameIsNull() {
        assertThatThrownBy(() -> new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            null,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("displayName cannot be null");
    }

    @Test
    void shouldThrowWhenDescriptionIsNull() {
        assertThatThrownBy(() -> new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            null,
            VALID_ACTIONS
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("description cannot be null");
    }

    @Test
    void shouldThrowWhenActionsIsNull() {
        assertThatThrownBy(() -> new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            null
        ))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actions cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void shouldThrowWhenIdentifierIsBlank(String blankIdentifier) {
        assertThatThrownBy(() -> new ServiceInfo(
            blankIdentifier,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("identifier cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    void shouldThrowWhenDisplayNameIsBlank(String blankDisplayName) {
        assertThatThrownBy(() -> new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            blankDisplayName,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("displayName cannot be blank");
    }

    @Test
    void shouldSupportAllServiceCategories() {
        for (ServiceCategory category : ServiceCategory.values()) {
            ServiceInfo info = new ServiceInfo(
                VALID_IDENTIFIER,
                category,
                VALID_DISPLAY_NAME,
                VALID_DESCRIPTION,
                VALID_ACTIONS
            );
            assertThat(info.category()).isEqualTo(category);
        }
    }

    @Test
    void shouldSupportEquality() {
        ServiceInfo info1 = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        ServiceInfo info2 = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        assertThat(info1).isEqualTo(info2);
        assertThat(info1.hashCode()).isEqualTo(info2.hashCode());
    }

    @Test
    void shouldNotBeEqualWhenIdentifiersDiffer() {
        ServiceInfo info1 = new ServiceInfo(
            "payment",
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        ServiceInfo info2 = new ServiceInfo(
            "reporting",
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        assertThat(info1).isNotEqualTo(info2);
    }

    @Test
    void shouldHaveToStringRepresentation() {
        ServiceInfo info = new ServiceInfo(
            VALID_IDENTIFIER,
            VALID_CATEGORY,
            VALID_DISPLAY_NAME,
            VALID_DESCRIPTION,
            VALID_ACTIONS
        );

        String toString = info.toString();
        assertThat(toString).contains(VALID_IDENTIFIER);
        assertThat(toString).contains(VALID_CATEGORY.name());
        assertThat(toString).contains(VALID_DISPLAY_NAME);
    }
}
