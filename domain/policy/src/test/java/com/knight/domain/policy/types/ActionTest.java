package com.knight.domain.policy.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Action Tests")
class ActionTest {

    @Nested
    @DisplayName("of() Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("should create action with valid format")
        void shouldCreateActionWithValidFormat() {
            // When/Then
            assertThatCode(() -> Action.of("service.create")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("payment.process")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("security.admin.manage")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("service-group.service.resource.operation")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should create action with wildcards")
        void shouldCreateActionWithWildcards() {
            // When/Then
            assertThatCode(() -> Action.of("*")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("*.create")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("service.*")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("*.*.create")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should reject null action")
        void shouldRejectNullAction() {
            // When/Then
            assertThatThrownBy(() -> Action.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action cannot be null or blank");
        }

        @Test
        @DisplayName("should reject blank action")
        void shouldRejectBlankAction() {
            // When/Then
            assertThatThrownBy(() -> Action.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action cannot be null or blank");

            assertThatThrownBy(() -> Action.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action cannot be null or blank");
        }

        @Test
        @DisplayName("should reject invalid format")
        void shouldRejectInvalidFormat() {
            // When/Then - uppercase not allowed
            assertThatThrownBy(() -> Action.of("Service.Create"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid action format");

            // Starts with number
            assertThatThrownBy(() -> Action.of("123service.create"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid action format");

            // Contains spaces
            assertThatThrownBy(() -> Action.of("service .create"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid action format");

            // Contains special characters
            assertThatThrownBy(() -> Action.of("service@create"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid action format");
        }

        @Test
        @DisplayName("should accept hyphenated names")
        void shouldAcceptHyphenatedNames() {
            // When/Then
            assertThatCode(() -> Action.of("service-group.action")).doesNotThrowAnyException();
            assertThatCode(() -> Action.of("multi-word-service.create")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("all() Factory Method Tests")
    class AllFactoryTests {

        @Test
        @DisplayName("should create wildcard action")
        void shouldCreateWildcardAction() {
            // When
            Action action = Action.all();

            // Then
            assertThat(action.value()).isEqualTo("*");
        }
    }

    @Nested
    @DisplayName("matches() Tests")
    class MatchesTests {

        @Nested
        @DisplayName("Exact Match Tests")
        class ExactMatchTests {

            @Test
            @DisplayName("should match identical actions")
            void shouldMatchIdenticalActions() {
                // Given
                Action pattern = Action.of("service.create");
                Action action = Action.of("service.create");

                // When/Then
                assertThat(pattern.matches(action)).isTrue();
            }

            @Test
            @DisplayName("should not match different actions")
            void shouldNotMatchDifferentActions() {
                // Given
                Action pattern = Action.of("service.create");
                Action action = Action.of("service.delete");

                // When/Then
                assertThat(pattern.matches(action)).isFalse();
            }

            @Test
            @DisplayName("should match multi-segment actions")
            void shouldMatchMultiSegmentActions() {
                // Given
                Action pattern = Action.of("security.admin.users.create");
                Action action = Action.of("security.admin.users.create");

                // When/Then
                assertThat(pattern.matches(action)).isTrue();
            }
        }

        @Nested
        @DisplayName("Wildcard (*) Tests")
        class WildcardTests {

            @Test
            @DisplayName("* should match any action")
            void wildcardShouldMatchAnyAction() {
                // Given
                Action pattern = Action.of("*");

                // When/Then
                assertThat(pattern.matches(Action.of("service.create"))).isTrue();
                assertThat(pattern.matches(Action.of("payment.process"))).isTrue();
                assertThat(pattern.matches(Action.of("security.admin.manage"))).isTrue();
                assertThat(pattern.matches(Action.of("any.random.action.here"))).isTrue();
            }
        }

        @Nested
        @DisplayName("Suffix Wildcard (*.action) Tests")
        class SuffixWildcardTests {

            @Test
            @DisplayName("*.create should match all create actions")
            void suffixWildcardShouldMatchAllCreateActions() {
                // Given
                Action pattern = Action.of("*.create");

                // When/Then
                assertThat(pattern.matches(Action.of("service.create"))).isTrue();
                assertThat(pattern.matches(Action.of("payment.create"))).isTrue();
                assertThat(pattern.matches(Action.of("security.admin.create"))).isTrue();
            }

            @Test
            @DisplayName("*.create should not match non-create actions")
            void suffixWildcardShouldNotMatchNonCreateActions() {
                // Given
                Action pattern = Action.of("*.create");

                // When/Then
                assertThat(pattern.matches(Action.of("service.delete"))).isFalse();
                assertThat(pattern.matches(Action.of("payment.process"))).isFalse();
                assertThat(pattern.matches(Action.of("service.update"))).isFalse();
            }

            @Test
            @DisplayName("*.view should match all view actions")
            void suffixWildcardShouldMatchAllViewActions() {
                // Given
                Action pattern = Action.of("*.view");

                // When/Then
                assertThat(pattern.matches(Action.of("service.view"))).isTrue();
                assertThat(pattern.matches(Action.of("payment.view"))).isTrue();
                assertThat(pattern.matches(Action.of("complex.path.view"))).isTrue();
            }

            @Test
            @DisplayName("*.approve should match all approve actions")
            void suffixWildcardShouldMatchAllApproveActions() {
                // Given
                Action pattern = Action.of("*.approve");

                // When/Then
                assertThat(pattern.matches(Action.of("payment.approve"))).isTrue();
                assertThat(pattern.matches(Action.of("transaction.approve"))).isTrue();
            }
        }

        @Nested
        @DisplayName("Prefix Wildcard (service.*) Tests")
        class PrefixWildcardTests {

            @Test
            @DisplayName("service.* should match all service actions")
            void prefixWildcardShouldMatchAllServiceActions() {
                // Given
                Action pattern = Action.of("service.*");

                // When/Then
                assertThat(pattern.matches(Action.of("service.create"))).isTrue();
                assertThat(pattern.matches(Action.of("service.delete"))).isTrue();
                assertThat(pattern.matches(Action.of("service.update"))).isTrue();
                assertThat(pattern.matches(Action.of("service.view"))).isTrue();
            }

            @Test
            @DisplayName("service.* should not match other service prefixes")
            void prefixWildcardShouldNotMatchOtherPrefixes() {
                // Given
                Action pattern = Action.of("service.*");

                // When/Then
                assertThat(pattern.matches(Action.of("payment.create"))).isFalse();
                assertThat(pattern.matches(Action.of("security.admin"))).isFalse();
            }

            @Test
            @DisplayName("security.* should match all security actions")
            void prefixWildcardShouldMatchSecurityActions() {
                // Given
                Action pattern = Action.of("security.*");

                // When/Then
                assertThat(pattern.matches(Action.of("security.admin"))).isTrue();
                assertThat(pattern.matches(Action.of("security.users.manage"))).isTrue();
                assertThat(pattern.matches(Action.of("security.roles.assign"))).isTrue();
            }

            @Test
            @DisplayName("payment.* should match all payment actions")
            void prefixWildcardShouldMatchPaymentActions() {
                // Given
                Action pattern = Action.of("payment.*");

                // When/Then
                assertThat(pattern.matches(Action.of("payment.process"))).isTrue();
                assertThat(pattern.matches(Action.of("payment.refund"))).isTrue();
                assertThat(pattern.matches(Action.of("payment.cancel"))).isTrue();
            }
        }

        @Nested
        @DisplayName("Edge Cases")
        class EdgeCaseTests {

            @Test
            @DisplayName("should handle single segment actions")
            void shouldHandleSingleSegmentActions() {
                // Given
                Action pattern = Action.of("admin");
                Action action = Action.of("admin");

                // When/Then
                assertThat(pattern.matches(action)).isTrue();
            }

            @Test
            @DisplayName("*.action should not match single segment")
            void suffixWildcardShouldNotMatchSingleSegment() {
                // Given
                Action pattern = Action.of("*.create");

                // When/Then
                assertThat(pattern.matches(Action.of("create"))).isFalse();
            }

            @Test
            @DisplayName("prefix.* should match nested paths")
            void prefixWildcardShouldMatchNestedPaths() {
                // Given
                Action pattern = Action.of("service.*");

                // When/Then
                assertThat(pattern.matches(Action.of("service.admin.users.create"))).isTrue();
                assertThat(pattern.matches(Action.of("service.sub.path.action"))).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal when values match")
        void shouldBeEqualWhenValuesMatch() {
            // Given
            Action action1 = Action.of("service.create");
            Action action2 = Action.of("service.create");

            // When/Then
            assertThat(action1).isEqualTo(action2);
            assertThat(action1.hashCode()).isEqualTo(action2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            // Given
            Action action1 = Action.of("service.create");
            Action action2 = Action.of("service.delete");

            // When/Then
            assertThat(action1).isNotEqualTo(action2);
        }

        @Test
        @DisplayName("wildcard actions should be equal")
        void wildcardActionsShouldBeEqual() {
            // Given
            Action action1 = Action.of("*");
            Action action2 = Action.all();

            // When/Then
            assertThat(action1).isEqualTo(action2);
        }
    }

    @Nested
    @DisplayName("value() Accessor Tests")
    class ValueAccessorTests {

        @Test
        @DisplayName("should return correct value")
        void shouldReturnCorrectValue() {
            // Given
            Action action = Action.of("service.create");

            // When/Then
            assertThat(action.value()).isEqualTo("service.create");
        }

        @Test
        @DisplayName("should return wildcard value")
        void shouldReturnWildcardValue() {
            // Given
            Action action = Action.all();

            // When/Then
            assertThat(action.value()).isEqualTo("*");
        }
    }
}
