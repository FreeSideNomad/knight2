package com.knight.application.service.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailRequest.
 */
class EmailRequestTest {

    @Nested
    @DisplayName("Record Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                "Test User",
                "Subject",
                "<p>HTML</p>",
                "Text",
                Map.of("key", "value")
            );

            assertThat(request.to()).isEqualTo("test@example.com");
            assertThat(request.toName()).isEqualTo("Test User");
            assertThat(request.subject()).isEqualTo("Subject");
            assertThat(request.htmlBody()).isEqualTo("<p>HTML</p>");
            assertThat(request.textBody()).isEqualTo("Text");
            assertThat(request.metadata()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("should create request with null optional fields")
        void shouldCreateRequestWithNullOptionalFields() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                null,
                "Subject",
                "<p>HTML</p>",
                null,
                null
            );

            assertThat(request.to()).isEqualTo("test@example.com");
            assertThat(request.toName()).isNull();
            assertThat(request.subject()).isEqualTo("Subject");
            assertThat(request.htmlBody()).isEqualTo("<p>HTML</p>");
            assertThat(request.textBody()).isNull();
            assertThat(request.metadata()).isNull();
        }
    }

    @Nested
    @DisplayName("simple() Factory Method")
    class SimpleFactoryTests {

        @Test
        @DisplayName("should create simple request with required fields only")
        void shouldCreateSimpleRequest() {
            EmailRequest request = EmailRequest.simple(
                "test@example.com",
                "Subject",
                "<p>HTML</p>",
                "Text"
            );

            assertThat(request.to()).isEqualTo("test@example.com");
            assertThat(request.toName()).isNull();
            assertThat(request.subject()).isEqualTo("Subject");
            assertThat(request.htmlBody()).isEqualTo("<p>HTML</p>");
            assertThat(request.textBody()).isEqualTo("Text");
            assertThat(request.metadata()).isEmpty();
        }

        @Test
        @DisplayName("should have empty metadata instead of null")
        void shouldHaveEmptyMetadata() {
            EmailRequest request = EmailRequest.simple(
                "test@example.com",
                "Subject",
                "<p>HTML</p>",
                "Text"
            );

            assertThat(request.metadata()).isNotNull();
            assertThat(request.metadata()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Record Equality")
    class EqualityTests {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenFieldsMatch() {
            Map<String, String> metadata = Map.of("key", "value");

            EmailRequest request1 = new EmailRequest(
                "test@example.com",
                "Name",
                "Subject",
                "<p>HTML</p>",
                "Text",
                metadata
            );

            EmailRequest request2 = new EmailRequest(
                "test@example.com",
                "Name",
                "Subject",
                "<p>HTML</p>",
                "Text",
                metadata
            );

            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when fields differ")
        void shouldNotBeEqualWhenFieldsDiffer() {
            EmailRequest request1 = EmailRequest.simple(
                "test1@example.com",
                "Subject",
                "<p>HTML</p>",
                "Text"
            );

            EmailRequest request2 = EmailRequest.simple(
                "test2@example.com",
                "Subject",
                "<p>HTML</p>",
                "Text"
            );

            assertThat(request1).isNotEqualTo(request2);
        }
    }

    @Nested
    @DisplayName("Record toString()")
    class ToStringTests {

        @Test
        @DisplayName("should include all fields in toString")
        void shouldIncludeAllFieldsInToString() {
            EmailRequest request = new EmailRequest(
                "test@example.com",
                "Name",
                "Subject",
                "<p>HTML</p>",
                "Text",
                Map.of("key", "value")
            );

            String toString = request.toString();

            assertThat(toString).contains("test@example.com");
            assertThat(toString).contains("Name");
            assertThat(toString).contains("Subject");
        }
    }
}
