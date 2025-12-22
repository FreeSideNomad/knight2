package com.knight.domain.policy.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Resource Tests")
class ResourceTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() should create resource with value")
        void ofShouldCreateResourceWithValue() {
            // When
            Resource resource = Resource.of("system:type:123");

            // Then
            assertThat(resource.value()).isEqualTo("system:type:123");
        }

        @Test
        @DisplayName("all() should create wildcard resource")
        void allShouldCreateWildcardResource() {
            // When
            Resource resource = Resource.all();

            // Then
            assertThat(resource.value()).isEqualTo("*");
        }

        @Test
        @DisplayName("ofList() should create comma-separated resource list")
        void ofListShouldCreateCommaSeparatedResourceList() {
            // Given
            List<String> resourceIds = List.of("resource:1", "resource:2", "resource:3");

            // When
            Resource resource = Resource.ofList(resourceIds);

            // Then
            assertThat(resource.value()).isEqualTo("resource:1,resource:2,resource:3");
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            // When/Then
            assertThatThrownBy(() -> Resource.of(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");
        }

        @Test
        @DisplayName("should reject blank value")
        void shouldRejectBlankValue() {
            // When/Then
            assertThatThrownBy(() -> Resource.of(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");

            assertThatThrownBy(() -> Resource.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");
        }
    }

    @Nested
    @DisplayName("matches() Tests")
    class MatchesTests {

        @Nested
        @DisplayName("Wildcard Matching Tests")
        class WildcardMatchingTests {

            @Test
            @DisplayName("* should match any resource")
            void wildcardShouldMatchAnyResource() {
                // Given
                Resource resource = Resource.all();

                // When/Then
                assertThat(resource.matches("any:resource:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isTrue();
                assertThat(resource.matches("anything")).isTrue();
            }
        }

        @Nested
        @DisplayName("Exact Matching Tests")
        class ExactMatchingTests {

            @Test
            @DisplayName("should match exact resource ID")
            void shouldMatchExactResourceId() {
                // Given
                Resource resource = Resource.of("system:type:123");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isFalse();
            }

            @Test
            @DisplayName("should match exact string")
            void shouldMatchExactString() {
                // Given
                Resource resource = Resource.of("my-resource");

                // When/Then
                assertThat(resource.matches("my-resource")).isTrue();
                assertThat(resource.matches("other-resource")).isFalse();
            }
        }

        @Nested
        @DisplayName("Pattern Matching Tests")
        class PatternMatchingTests {

            @Test
            @DisplayName("should match wildcard at end")
            void shouldMatchWildcardAtEnd() {
                // Given
                Resource resource = Resource.of("system:type:*");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isTrue();
                assertThat(resource.matches("system:type:abc")).isTrue();
                assertThat(resource.matches("other:type:123")).isFalse();
            }

            @Test
            @DisplayName("should match wildcard at beginning")
            void shouldMatchWildcardAtBeginning() {
                // Given
                Resource resource = Resource.of("*:type:123");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("other:type:123")).isTrue();
                assertThat(resource.matches("any:type:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isFalse();
            }

            @Test
            @DisplayName("should match wildcard in middle")
            void shouldMatchWildcardInMiddle() {
                // Given
                Resource resource = Resource.of("system:*:123");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("system:other:123")).isTrue();
                assertThat(resource.matches("system:anything:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isFalse();
            }

            @Test
            @DisplayName("should match multiple wildcards")
            void shouldMatchMultipleWildcards() {
                // Given
                Resource resource = Resource.of("system:*:*");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("system:other:456")).isTrue();
                assertThat(resource.matches("system:anything:xyz")).isTrue();
                assertThat(resource.matches("other:type:123")).isFalse();
            }

            @Test
            @DisplayName("should handle dot in resource patterns")
            void shouldHandleDotInResourcePatterns() {
                // Given
                Resource resource = Resource.of("com.example.*");

                // When/Then
                assertThat(resource.matches("com.example.resource1")).isTrue();
                assertThat(resource.matches("com.example.resource2")).isTrue();
                assertThat(resource.matches("com.other.resource")).isFalse();
            }
        }

        @Nested
        @DisplayName("Comma-Separated List Matching Tests")
        class CommaSeparatedListTests {

            @Test
            @DisplayName("should match any resource in comma-separated list")
            void shouldMatchAnyResourceInList() {
                // Given
                Resource resource = Resource.of("resource:1,resource:2,resource:3");

                // When/Then
                assertThat(resource.matches("resource:1")).isTrue();
                assertThat(resource.matches("resource:2")).isTrue();
                assertThat(resource.matches("resource:3")).isTrue();
                assertThat(resource.matches("resource:4")).isFalse();
            }

            @Test
            @DisplayName("should match with wildcards in list")
            void shouldMatchWithWildcardsInList() {
                // Given
                Resource resource = Resource.of("system:*:1,system:*:2");

                // When/Then
                assertThat(resource.matches("system:type:1")).isTrue();
                assertThat(resource.matches("system:other:1")).isTrue();
                assertThat(resource.matches("system:type:2")).isTrue();
                assertThat(resource.matches("system:type:3")).isFalse();
            }

            @Test
            @DisplayName("should handle whitespace in comma-separated list")
            void shouldHandleWhitespaceInList() {
                // Given
                Resource resource = Resource.of("resource:1, resource:2 , resource:3");

                // When/Then
                assertThat(resource.matches("resource:1")).isTrue();
                assertThat(resource.matches("resource:2")).isTrue();
                assertThat(resource.matches("resource:3")).isTrue();
            }

            @Test
            @DisplayName("should match complex list patterns")
            void shouldMatchComplexListPatterns() {
                // Given
                Resource resource = Resource.of("system:type:*,other:*:123,exact:resource");

                // When/Then
                assertThat(resource.matches("system:type:123")).isTrue();
                assertThat(resource.matches("system:type:456")).isTrue();
                assertThat(resource.matches("other:anything:123")).isTrue();
                assertThat(resource.matches("exact:resource")).isTrue();
                assertThat(resource.matches("other:anything:456")).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("patterns() Tests")
    class PatternsTests {

        @Test
        @DisplayName("should return single pattern")
        void shouldReturnSinglePattern() {
            // Given
            Resource resource = Resource.of("system:type:123");

            // When
            List<String> patterns = resource.patterns();

            // Then
            assertThat(patterns).containsExactly("system:type:123");
        }

        @Test
        @DisplayName("should return multiple patterns from comma-separated list")
        void shouldReturnMultiplePatternsFromList() {
            // Given
            Resource resource = Resource.of("resource:1,resource:2,resource:3");

            // When
            List<String> patterns = resource.patterns();

            // Then
            assertThat(patterns).containsExactly("resource:1", "resource:2", "resource:3");
        }

        @Test
        @DisplayName("should trim whitespace from patterns")
        void shouldTrimWhitespaceFromPatterns() {
            // Given
            Resource resource = Resource.of("resource:1 , resource:2 ,resource:3");

            // When
            List<String> patterns = resource.patterns();

            // Then
            assertThat(patterns).containsExactly("resource:1", "resource:2", "resource:3");
        }

        @Test
        @DisplayName("should return wildcard pattern")
        void shouldReturnWildcardPattern() {
            // Given
            Resource resource = Resource.all();

            // When
            List<String> patterns = resource.patterns();

            // Then
            assertThat(patterns).containsExactly("*");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal when values match")
        void shouldBeEqualWhenValuesMatch() {
            // Given
            Resource resource1 = Resource.of("system:type:123");
            Resource resource2 = Resource.of("system:type:123");

            // When/Then
            assertThat(resource1).isEqualTo(resource2);
            assertThat(resource1.hashCode()).isEqualTo(resource2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            // Given
            Resource resource1 = Resource.of("system:type:123");
            Resource resource2 = Resource.of("system:type:456");

            // When/Then
            assertThat(resource1).isNotEqualTo(resource2);
        }

        @Test
        @DisplayName("wildcard resources should be equal")
        void wildcardResourcesShouldBeEqual() {
            // Given
            Resource resource1 = Resource.all();
            Resource resource2 = Resource.of("*");

            // When/Then
            assertThat(resource1).isEqualTo(resource2);
        }

        @Test
        @DisplayName("list resources should be equal regardless of creation method")
        void listResourcesShouldBeEqual() {
            // Given
            Resource resource1 = Resource.of("resource:1,resource:2,resource:3");
            Resource resource2 = Resource.ofList(List.of("resource:1", "resource:2", "resource:3"));

            // When/Then
            assertThat(resource1).isEqualTo(resource2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle special characters in resource ID")
        void shouldHandleSpecialCharactersInResourceId() {
            // Given
            Resource resource = Resource.of("system:type:abc-123_def");

            // When/Then
            assertThat(resource.matches("system:type:abc-123_def")).isTrue();
        }

        @Test
        @DisplayName("should handle UUID-like resource IDs")
        void shouldHandleUuidLikeResourceIds() {
            // Given
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            Resource resource = Resource.of("system:type:" + uuid);

            // When/Then
            assertThat(resource.matches("system:type:" + uuid)).isTrue();
        }

        @Test
        @DisplayName("should reject empty list patterns")
        void shouldRejectEmptyListPatterns() {
            // When/Then
            assertThatThrownBy(() -> Resource.ofList(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");
        }

        @Test
        @DisplayName("should match patterns with slashes")
        void shouldMatchPatternsWithSlashes() {
            // Given
            Resource resource = Resource.of("api/v1/*");

            // When/Then
            assertThat(resource.matches("api/v1/users")).isTrue();
            assertThat(resource.matches("api/v1/posts")).isTrue();
            assertThat(resource.matches("api/v2/users")).isFalse();
        }
    }
}
