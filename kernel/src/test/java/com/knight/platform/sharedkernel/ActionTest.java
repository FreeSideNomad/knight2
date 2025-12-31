package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Action Value Object Tests")
class ActionTest {

    @Nested
    @DisplayName("Valid URN Creation Tests")
    class ValidUrnCreationTests {

        @Test
        @DisplayName("should create action from valid direct URN")
        void shouldCreateActionFromValidDirectUrn() {
            // when
            Action action = Action.of("direct:client-portal:profile:view");

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT);
            assertThat(action.service()).isEqualTo("client-portal");
            assertThat(action.resourceType()).isEqualTo("profile");
            assertThat(action.actionType()).isEqualTo(ActionType.VIEW);
        }

        @Test
        @DisplayName("should create action from valid indirect URN")
        void shouldCreateActionFromValidIndirectUrn() {
            // when
            Action action = Action.of("indirect:indirect-portal:client:update");

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.INDIRECT);
            assertThat(action.service()).isEqualTo("indirect-portal");
            assertThat(action.resourceType()).isEqualTo("client");
            assertThat(action.actionType()).isEqualTo(ActionType.UPDATE);
        }

        @Test
        @DisplayName("should create action from valid bank URN")
        void shouldCreateActionFromValidBankUrn() {
            // when
            Action action = Action.of("bank:payor-enrolment:enrolment:approve");

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.BANK);
            assertThat(action.service()).isEqualTo("payor-enrolment");
            assertThat(action.resourceType()).isEqualTo("enrolment");
            assertThat(action.actionType()).isEqualTo(ActionType.APPROVE);
        }

        @Test
        @DisplayName("should create action from valid admin URN")
        void shouldCreateActionFromValidAdminUrn() {
            // when
            Action action = Action.of("admin:user-management:user:manage");

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.ADMIN);
            assertThat(action.service()).isEqualTo("user-management");
            assertThat(action.resourceType()).isEqualTo("user");
            assertThat(action.actionType()).isEqualTo(ActionType.MANAGE);
        }

        @Test
        @DisplayName("should handle case-insensitive service and action types")
        void shouldHandleCaseInsensitiveTypes() {
            // when
            Action action = Action.of("DIRECT:client-portal:profile:VIEW");

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT);
            assertThat(action.actionType()).isEqualTo(ActionType.VIEW);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "direct:a:b:view",
            "direct:service123:resource:create",
            "direct:my-service:my-resource:update"
        })
        @DisplayName("should accept valid service and resource names")
        void shouldAcceptValidNames(String urn) {
            // when/then
            assertThatNoException().isThrownBy(() -> Action.of(urn));
        }
    }

    @Nested
    @DisplayName("Invalid URN Tests")
    class InvalidUrnTests {

        @Test
        @DisplayName("should reject null URN")
        void shouldRejectNullUrn() {
            // when/then
            assertThatNullPointerException()
                .isThrownBy(() -> Action.of((String) null))
                .withMessage("URN cannot be null");
        }

        @Test
        @DisplayName("should reject URN with wrong segment count - too few")
        void shouldRejectUrnWithTooFewSegments() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("direct:client-portal:view"))
                .withMessageContaining("Expected 4 segments")
                .withMessageContaining("got 3");
        }

        @Test
        @DisplayName("should reject URN with wrong segment count - too many")
        void shouldRejectUrnWithTooManySegments() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("direct:client-portal:profile:sub:view"))
                .withMessageContaining("Expected 4 segments")
                .withMessageContaining("got 5");
        }

        @Test
        @DisplayName("should reject unknown service type")
        void shouldRejectUnknownServiceType() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("unknown:client-portal:profile:view"))
                .withMessageContaining("Invalid service type 'unknown'")
                .withMessageContaining("Valid types: DIRECT, INDIRECT, BANK, ADMIN");
        }

        @Test
        @DisplayName("should reject unknown action type")
        void shouldRejectUnknownActionType() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("direct:client-portal:profile:execute"))
                .withMessageContaining("Invalid action type 'execute'")
                .withMessageContaining("Valid types: VIEW, CREATE, UPDATE, DELETE, APPROVE, MANAGE");
        }

        @Test
        @DisplayName("should reject empty service name")
        void shouldRejectEmptyServiceName() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("direct::profile:view"))
                .withMessageContaining("service cannot be empty");
        }

        @Test
        @DisplayName("should reject empty resource type")
        void shouldRejectEmptyResourceType() {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of("direct:client-portal::view"))
                .withMessageContaining("resourceType cannot be empty");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "direct:Client-Portal:profile:view",
            "direct:123service:profile:view",
            "direct:service_name:profile:view",
            "direct:service name:profile:view"
        })
        @DisplayName("should reject invalid service name patterns")
        void shouldRejectInvalidServiceNames(String urn) {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of(urn))
                .withMessageContaining("Must match pattern: [a-z][a-z0-9-]*");
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "direct:service:Profile:view",
            "direct:service:123resource:view",
            "direct:service:resource_type:view"
        })
        @DisplayName("should reject invalid resource type patterns")
        void shouldRejectInvalidResourceTypes(String urn) {
            // when/then
            assertThatIllegalArgumentException()
                .isThrownBy(() -> Action.of(urn))
                .withMessageContaining("Must match pattern: [a-z][a-z0-9-]*");
        }
    }

    @Nested
    @DisplayName("Component Factory Method Tests")
    class ComponentFactoryMethodTests {

        @Test
        @DisplayName("should create action from components")
        void shouldCreateActionFromComponents() {
            // when
            Action action = Action.of(ServiceType.DIRECT, "client-portal", "profile", ActionType.VIEW);

            // then
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT);
            assertThat(action.service()).isEqualTo("client-portal");
            assertThat(action.resourceType()).isEqualTo("profile");
            assertThat(action.actionType()).isEqualTo(ActionType.VIEW);
        }

        @Test
        @DisplayName("should reject null service type in component factory")
        void shouldRejectNullServiceType() {
            // when/then
            assertThatNullPointerException()
                .isThrownBy(() -> Action.of(null, "service", "resource", ActionType.VIEW))
                .withMessage("serviceType cannot be null");
        }

        @Test
        @DisplayName("should reject null action type in component factory")
        void shouldRejectNullActionType() {
            // when/then
            assertThatNullPointerException()
                .isThrownBy(() -> Action.of(ServiceType.DIRECT, "service", "resource", null))
                .withMessage("actionType cannot be null");
        }
    }

    @Nested
    @DisplayName("URN Serialization Tests")
    class UrnSerializationTests {

        @Test
        @DisplayName("should return correct URN string from urn() method")
        void shouldReturnCorrectUrnString() {
            // given
            Action action = Action.of("direct:client-portal:profile:view");

            // when
            String urn = action.urn();

            // then
            assertThat(urn).isEqualTo("direct:client-portal:profile:view");
        }

        @Test
        @DisplayName("should return same URN from toString()")
        void shouldReturnSameUrnFromToString() {
            // given
            Action action = Action.of("bank:payor-enrolment:enrolment:approve");

            // when
            String result = action.toString();

            // then
            assertThat(result).isEqualTo("bank:payor-enrolment:enrolment:approve");
        }

        @Test
        @DisplayName("should normalize case in URN output")
        void shouldNormalizeCaseInUrnOutput() {
            // given
            Action action = Action.of("DIRECT:client-portal:profile:VIEW");

            // when
            String urn = action.urn();

            // then
            assertThat(urn).isEqualTo("direct:client-portal:profile:view");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal for same URN")
        void shouldBeEqualForSameUrn() {
            // given
            Action action1 = Action.of("direct:client-portal:profile:view");
            Action action2 = Action.of("direct:client-portal:profile:view");

            // then
            assertThat(action1).isEqualTo(action2);
            assertThat(action1.hashCode()).isEqualTo(action2.hashCode());
        }

        @Test
        @DisplayName("should be equal regardless of input case")
        void shouldBeEqualRegardlessOfCase() {
            // given
            Action action1 = Action.of("DIRECT:client-portal:profile:VIEW");
            Action action2 = Action.of("direct:client-portal:profile:view");

            // then
            assertThat(action1).isEqualTo(action2);
        }

        @Test
        @DisplayName("should not be equal for different service types")
        void shouldNotBeEqualForDifferentServiceTypes() {
            // given
            Action action1 = Action.of("direct:client-portal:profile:view");
            Action action2 = Action.of("indirect:client-portal:profile:view");

            // then
            assertThat(action1).isNotEqualTo(action2);
        }

        @Test
        @DisplayName("should not be equal for different services")
        void shouldNotBeEqualForDifferentServices() {
            // given
            Action action1 = Action.of("direct:client-portal:profile:view");
            Action action2 = Action.of("direct:other-portal:profile:view");

            // then
            assertThat(action1).isNotEqualTo(action2);
        }

        @Test
        @DisplayName("should not be equal for different resource types")
        void shouldNotBeEqualForDifferentResourceTypes() {
            // given
            Action action1 = Action.of("direct:client-portal:profile:view");
            Action action2 = Action.of("direct:client-portal:account:view");

            // then
            assertThat(action1).isNotEqualTo(action2);
        }

        @Test
        @DisplayName("should not be equal for different action types")
        void shouldNotBeEqualForDifferentActionTypes() {
            // given
            Action action1 = Action.of("direct:client-portal:profile:view");
            Action action2 = Action.of("direct:client-portal:profile:update");

            // then
            assertThat(action1).isNotEqualTo(action2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            // given
            Action action = Action.of("direct:client-portal:profile:view");

            // then
            assertThat(action).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            // given
            Action action = Action.of("direct:client-portal:profile:view");

            // then
            assertThat(action).isEqualTo(action);
        }
    }

    @Nested
    @DisplayName("All Action Types Tests")
    class AllActionTypesTests {

        @Test
        @DisplayName("should support VIEW action type")
        void shouldSupportViewActionType() {
            Action action = Action.of("direct:service:resource:view");
            assertThat(action.actionType()).isEqualTo(ActionType.VIEW);
        }

        @Test
        @DisplayName("should support CREATE action type")
        void shouldSupportCreateActionType() {
            Action action = Action.of("direct:service:resource:create");
            assertThat(action.actionType()).isEqualTo(ActionType.CREATE);
        }

        @Test
        @DisplayName("should support UPDATE action type")
        void shouldSupportUpdateActionType() {
            Action action = Action.of("direct:service:resource:update");
            assertThat(action.actionType()).isEqualTo(ActionType.UPDATE);
        }

        @Test
        @DisplayName("should support DELETE action type")
        void shouldSupportDeleteActionType() {
            Action action = Action.of("direct:service:resource:delete");
            assertThat(action.actionType()).isEqualTo(ActionType.DELETE);
        }

        @Test
        @DisplayName("should support APPROVE action type")
        void shouldSupportApproveActionType() {
            Action action = Action.of("direct:service:resource:approve");
            assertThat(action.actionType()).isEqualTo(ActionType.APPROVE);
        }

        @Test
        @DisplayName("should support MANAGE action type")
        void shouldSupportManageActionType() {
            Action action = Action.of("direct:service:resource:manage");
            assertThat(action.actionType()).isEqualTo(ActionType.MANAGE);
        }
    }

    @Nested
    @DisplayName("All Service Types Tests")
    class AllServiceTypesTests {

        @Test
        @DisplayName("should support DIRECT service type")
        void shouldSupportDirectServiceType() {
            Action action = Action.of("direct:service:resource:view");
            assertThat(action.serviceType()).isEqualTo(ServiceType.DIRECT);
        }

        @Test
        @DisplayName("should support INDIRECT service type")
        void shouldSupportIndirectServiceType() {
            Action action = Action.of("indirect:service:resource:view");
            assertThat(action.serviceType()).isEqualTo(ServiceType.INDIRECT);
        }

        @Test
        @DisplayName("should support BANK service type")
        void shouldSupportBankServiceType() {
            Action action = Action.of("bank:service:resource:view");
            assertThat(action.serviceType()).isEqualTo(ServiceType.BANK);
        }

        @Test
        @DisplayName("should support ADMIN service type")
        void shouldSupportAdminServiceType() {
            Action action = Action.of("admin:service:resource:view");
            assertThat(action.serviceType()).isEqualTo(ServiceType.ADMIN);
        }
    }
}
