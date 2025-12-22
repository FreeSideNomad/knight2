package com.knight.domain.clients.api;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PageResult.
 */
@DisplayName("PageResult Tests")
class PageResultTest {

    @Nested
    @DisplayName("of() factory method")
    class OfFactoryMethod {

        @Test
        @DisplayName("should create page result with correct total pages")
        void shouldCreatePageResultWithCorrectTotalPages() {
            List<String> content = List.of("item1", "item2");
            PageResult<String> result = PageResult.of(content, 0, 10, 25);

            assertThat(result.content()).isEqualTo(content);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(25);
            assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        }

        @Test
        @DisplayName("should handle exact page division")
        void shouldHandleExactPageDivision() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 20);

            assertThat(result.totalPages()).isEqualTo(2); // 20/10 = 2
        }

        @Test
        @DisplayName("should handle zero size")
        void shouldHandleZeroSize() {
            PageResult<String> result = PageResult.of(List.of(), 0, 0, 0);

            assertThat(result.totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("should handle single element")
        void shouldHandleSingleElement() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 1);

            assertThat(result.totalPages()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("isFirst()")
    class IsFirst {

        @Test
        @DisplayName("should return true for first page")
        void shouldReturnTrueForFirstPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 25);

            assertThat(result.isFirst()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-first page")
        void shouldReturnFalseForNonFirstPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 1, 10, 25);

            assertThat(result.isFirst()).isFalse();
        }
    }

    @Nested
    @DisplayName("isLast()")
    class IsLast {

        @Test
        @DisplayName("should return true for last page")
        void shouldReturnTrueForLastPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 2, 10, 25);

            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-last page")
        void shouldReturnFalseForNonLastPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 25);

            assertThat(result.isLast()).isFalse();
        }

        @Test
        @DisplayName("should return true when page equals totalPages minus one")
        void shouldReturnTrueWhenPageEqualsLastPage() {
            // 30 elements with page size 10 = 3 pages (0, 1, 2)
            PageResult<String> result = PageResult.of(List.of("item1"), 2, 10, 30);

            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("should return true when page exceeds totalPages")
        void shouldReturnTrueWhenPageExceedsTotalPages() {
            PageResult<String> result = PageResult.of(List.of(), 5, 10, 25);

            assertThat(result.isLast()).isTrue();
        }
    }

    @Nested
    @DisplayName("hasNext()")
    class HasNext {

        @Test
        @DisplayName("should return true when not on last page")
        void shouldReturnTrueWhenNotOnLastPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 25);

            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("should return false when on last page")
        void shouldReturnFalseWhenOnLastPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 2, 10, 25);

            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("should return false when on middle page of multiple pages")
        void shouldReturnTrueWhenOnMiddlePage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 1, 10, 30);

            assertThat(result.hasNext()).isTrue(); // page 1 of 3 (pages 0, 1, 2)
        }
    }

    @Nested
    @DisplayName("hasPrevious()")
    class HasPrevious {

        @Test
        @DisplayName("should return false for first page")
        void shouldReturnFalseForFirstPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 0, 10, 25);

            assertThat(result.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("should return true for non-first page")
        void shouldReturnTrueForNonFirstPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 1, 10, 25);

            assertThat(result.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("should return true for last page")
        void shouldReturnTrueForLastPage() {
            PageResult<String> result = PageResult.of(List.of("item1"), 2, 10, 25);

            assertThat(result.hasPrevious()).isTrue();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty content")
        void shouldHandleEmptyContent() {
            PageResult<String> result = PageResult.of(List.of(), 0, 10, 0);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalPages()).isEqualTo(0);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("should handle single page result")
        void shouldHandleSinglePageResult() {
            PageResult<String> result = PageResult.of(List.of("a", "b", "c"), 0, 10, 3);

            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.isFirst()).isTrue();
            assertThat(result.isLast()).isTrue();
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isFalse();
        }
    }
}
