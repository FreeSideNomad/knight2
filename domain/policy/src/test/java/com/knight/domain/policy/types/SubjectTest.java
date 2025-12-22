package com.knight.domain.policy.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Subject Tests")
class SubjectTest {

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("user() should create USER subject with string ID")
        void userShouldCreateUserSubjectWithStringId() {
            // Given
            String userId = UUID.randomUUID().toString();

            // When
            Subject subject = Subject.user(userId);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.USER);
            assertThat(subject.identifier()).isEqualTo(userId);
            assertThat(subject.toUrn()).isEqualTo("user:" + userId);
        }

        @Test
        @DisplayName("user() should create USER subject with UUID")
        void userShouldCreateUserSubjectWithUUID() {
            // Given
            UUID userId = UUID.randomUUID();

            // When
            Subject subject = Subject.user(userId);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.USER);
            assertThat(subject.identifier()).isEqualTo(userId.toString());
            assertThat(subject.toUrn()).isEqualTo("user:" + userId);
        }

        @Test
        @DisplayName("group() should create GROUP subject with string ID")
        void groupShouldCreateGroupSubjectWithStringId() {
            // Given
            String groupId = UUID.randomUUID().toString();

            // When
            Subject subject = Subject.group(groupId);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.GROUP);
            assertThat(subject.identifier()).isEqualTo(groupId);
            assertThat(subject.toUrn()).isEqualTo("group:" + groupId);
        }

        @Test
        @DisplayName("group() should create GROUP subject with UUID")
        void groupShouldCreateGroupSubjectWithUUID() {
            // Given
            UUID groupId = UUID.randomUUID();

            // When
            Subject subject = Subject.group(groupId);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.GROUP);
            assertThat(subject.identifier()).isEqualTo(groupId.toString());
            assertThat(subject.toUrn()).isEqualTo("group:" + groupId);
        }

        @Test
        @DisplayName("role() should create ROLE subject")
        void roleShouldCreateRoleSubject() {
            // Given
            String roleName = "SECURITY_ADMIN";

            // When
            Subject subject = Subject.role(roleName);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.ROLE);
            assertThat(subject.identifier()).isEqualTo(roleName);
            assertThat(subject.toUrn()).isEqualTo("role:" + roleName);
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("should reject null type")
        void shouldRejectNullType() {
            // When/Then
            assertThatThrownBy(() -> new Subject(null, "identifier"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject type cannot be null");
        }

        @Test
        @DisplayName("should reject null identifier")
        void shouldRejectNullIdentifier() {
            // When/Then
            assertThatThrownBy(() -> Subject.user((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject identifier cannot be null or blank");
        }

        @Test
        @DisplayName("should reject blank identifier")
        void shouldRejectBlankIdentifier() {
            // When/Then
            assertThatThrownBy(() -> new Subject(Subject.SubjectType.USER, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject identifier cannot be null or blank");
        }

        @Test
        @DisplayName("should reject invalid UUID for USER type")
        void shouldRejectInvalidUuidForUserType() {
            // When/Then
            assertThatThrownBy(() -> Subject.user("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UUID for USER");
        }

        @Test
        @DisplayName("should reject invalid UUID for GROUP type")
        void shouldRejectInvalidUuidForGroupType() {
            // When/Then
            assertThatThrownBy(() -> Subject.group("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UUID for GROUP");
        }

        @Test
        @DisplayName("should reject invalid role name format")
        void shouldRejectInvalidRoleNameFormat() {
            // When/Then
            assertThatThrownBy(() -> Subject.role("invalid-role"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role name");

            assertThatThrownBy(() -> Subject.role("lowercase"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role name");

            assertThatThrownBy(() -> Subject.role("123STARTS_WITH_NUMBER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid role name");
        }

        @Test
        @DisplayName("should accept valid role name formats")
        void shouldAcceptValidRoleNameFormats() {
            // When/Then
            assertThatCode(() -> Subject.role("ADMIN")).doesNotThrowAnyException();
            assertThatCode(() -> Subject.role("SECURITY_ADMIN")).doesNotThrowAnyException();
            assertThatCode(() -> Subject.role("ROLE123")).doesNotThrowAnyException();
            assertThatCode(() -> Subject.role("SERVICE_ADMIN_2")).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("fromUrn() Tests")
    class FromUrnTests {

        @Test
        @DisplayName("should parse user URN")
        void shouldParseUserUrn() {
            // Given
            UUID userId = UUID.randomUUID();
            String urn = "user:" + userId;

            // When
            Subject subject = Subject.fromUrn(urn);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.USER);
            assertThat(subject.identifier()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("should parse group URN")
        void shouldParseGroupUrn() {
            // Given
            UUID groupId = UUID.randomUUID();
            String urn = "group:" + groupId;

            // When
            Subject subject = Subject.fromUrn(urn);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.GROUP);
            assertThat(subject.identifier()).isEqualTo(groupId.toString());
        }

        @Test
        @DisplayName("should parse role URN")
        void shouldParseRoleUrn() {
            // Given
            String urn = "role:SECURITY_ADMIN";

            // When
            Subject subject = Subject.fromUrn(urn);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.ROLE);
            assertThat(subject.identifier()).isEqualTo("SECURITY_ADMIN");
        }

        @Test
        @DisplayName("should parse URN case-insensitively")
        void shouldParseUrnCaseInsensitively() {
            // Given
            String urn = "USER:" + UUID.randomUUID();

            // When
            Subject subject = Subject.fromUrn(urn);

            // Then
            assertThat(subject.type()).isEqualTo(Subject.SubjectType.USER);
        }

        @Test
        @DisplayName("should reject null URN")
        void shouldRejectNullUrn() {
            // When/Then
            assertThatThrownBy(() -> Subject.fromUrn(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid subject URN");
        }

        @Test
        @DisplayName("should reject URN without colon")
        void shouldRejectUrnWithoutColon() {
            // When/Then
            assertThatThrownBy(() -> Subject.fromUrn("invalidurn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid subject URN");
        }

        @Test
        @DisplayName("should reject unknown subject type")
        void shouldRejectUnknownSubjectType() {
            // When/Then
            assertThatThrownBy(() -> Subject.fromUrn("unknown:123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown subject type");
        }
    }

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("should be equal when type and identifier match")
        void shouldBeEqualWhenTypeAndIdentifierMatch() {
            // Given
            UUID userId = UUID.randomUUID();
            Subject subject1 = Subject.user(userId);
            Subject subject2 = Subject.user(userId);

            // When/Then
            assertThat(subject1).isEqualTo(subject2);
            assertThat(subject1.hashCode()).isEqualTo(subject2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when identifiers differ")
        void shouldNotBeEqualWhenIdentifiersDiffer() {
            // Given
            Subject subject1 = Subject.user(UUID.randomUUID());
            Subject subject2 = Subject.user(UUID.randomUUID());

            // When/Then
            assertThat(subject1).isNotEqualTo(subject2);
        }

        @Test
        @DisplayName("should not be equal when types differ")
        void shouldNotBeEqualWhenTypesDiffer() {
            // Given
            UUID id = UUID.randomUUID();
            Subject userSubject = Subject.user(id);
            Subject groupSubject = Subject.group(id);

            // When/Then
            assertThat(userSubject).isNotEqualTo(groupSubject);
        }

        @Test
        @DisplayName("role subjects should be equal with same name")
        void roleSubjectsShouldBeEqualWithSameName() {
            // Given
            Subject role1 = Subject.role("SECURITY_ADMIN");
            Subject role2 = Subject.role("SECURITY_ADMIN");

            // When/Then
            assertThat(role1).isEqualTo(role2);
            assertThat(role1.hashCode()).isEqualTo(role2.hashCode());
        }
    }

    @Nested
    @DisplayName("toUrn() Tests")
    class ToUrnTests {

        @Test
        @DisplayName("should generate correct URN for user")
        void shouldGenerateCorrectUrnForUser() {
            // Given
            UUID userId = UUID.randomUUID();
            Subject subject = Subject.user(userId);

            // When
            String urn = subject.toUrn();

            // Then
            assertThat(urn).isEqualTo("user:" + userId);
        }

        @Test
        @DisplayName("should generate correct URN for group")
        void shouldGenerateCorrectUrnForGroup() {
            // Given
            UUID groupId = UUID.randomUUID();
            Subject subject = Subject.group(groupId);

            // When
            String urn = subject.toUrn();

            // Then
            assertThat(urn).isEqualTo("group:" + groupId);
        }

        @Test
        @DisplayName("should generate correct URN for role")
        void shouldGenerateCorrectUrnForRole() {
            // Given
            Subject subject = Subject.role("SECURITY_ADMIN");

            // When
            String urn = subject.toUrn();

            // Then
            assertThat(urn).isEqualTo("role:SECURITY_ADMIN");
        }

        @Test
        @DisplayName("toUrn() and fromUrn() should be reversible")
        void toUrnAndFromUrnShouldBeReversible() {
            // Given
            Subject original = Subject.role("APPROVER");

            // When
            String urn = original.toUrn();
            Subject parsed = Subject.fromUrn(urn);

            // Then
            assertThat(parsed).isEqualTo(original);
        }
    }
}
