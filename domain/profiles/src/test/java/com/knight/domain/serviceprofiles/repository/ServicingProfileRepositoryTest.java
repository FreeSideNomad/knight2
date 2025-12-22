package com.knight.domain.serviceprofiles.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ServicingProfileRepository interfaces.
 */
class ServicingProfileRepositoryTest {

    @Nested
    @DisplayName("PageResult")
    class PageResultTests {

        @Test
        @DisplayName("should calculate total pages for exact division")
        void shouldCalculateTotalPagesForExactDivision() {
            ServicingProfileRepository.PageResult<String> result = new ServicingProfileRepository.PageResult<>(
                List.of("a", "b"), 20L, 0, 10
            );

            assertThat(result.totalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("should calculate total pages with remainder")
        void shouldCalculateTotalPagesWithRemainder() {
            ServicingProfileRepository.PageResult<String> result = new ServicingProfileRepository.PageResult<>(
                List.of("a"), 25L, 0, 10
            );

            assertThat(result.totalPages()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return zero pages for zero size")
        void shouldReturnZeroPagesForZeroSize() {
            ServicingProfileRepository.PageResult<String> result = new ServicingProfileRepository.PageResult<>(
                List.of(), 0L, 0, 0
            );

            assertThat(result.totalPages()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return one page for single element")
        void shouldReturnOnePageForSingleElement() {
            ServicingProfileRepository.PageResult<String> result = new ServicingProfileRepository.PageResult<>(
                List.of("a"), 1L, 0, 10
            );

            assertThat(result.totalPages()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return page result record properties")
        void shouldReturnPageResultRecordProperties() {
            List<String> content = List.of("a", "b", "c");
            ServicingProfileRepository.PageResult<String> result = new ServicingProfileRepository.PageResult<>(
                content, 100L, 2, 20
            );

            assertThat(result.content()).isEqualTo(content);
            assertThat(result.totalElements()).isEqualTo(100L);
            assertThat(result.page()).isEqualTo(2);
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalPages()).isEqualTo(5);
        }
    }
}
