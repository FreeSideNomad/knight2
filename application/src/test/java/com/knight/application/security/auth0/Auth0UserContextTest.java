package com.knight.application.security.auth0;

import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import com.knight.platform.sharedkernel.SrfClientId;

import static org.assertj.core.api.Assertions.assertThat;

class Auth0UserContextTest {

    private Auth0UserContext context;

    @BeforeEach
    void setUp() {
        context = new Auth0UserContext();
    }

    @Nested
    @DisplayName("Initial state tests")
    class InitialStateTests {

        @Test
        @DisplayName("should not be initialized initially")
        void shouldNotBeInitializedInitially() {
            assertThat(context.isInitialized()).isFalse();
        }

        @Test
        @DisplayName("should not be auth0 request initially")
        void shouldNotBeAuth0RequestInitially() {
            assertThat(context.isAuth0Request()).isFalse();
        }

        @Test
        @DisplayName("should return empty user initially")
        void shouldReturnEmptyUserInitially() {
            assertThat(context.getUser()).isEmpty();
        }

        @Test
        @DisplayName("should return empty profile id initially")
        void shouldReturnEmptyProfileIdInitially() {
            assertThat(context.getProfileId()).isEmpty();
        }

        @Test
        @DisplayName("should return empty user email initially")
        void shouldReturnEmptyUserEmailInitially() {
            assertThat(context.getUserEmail()).isEmpty();
        }

        @Test
        @DisplayName("should return empty profile id urn initially")
        void shouldReturnEmptyProfileIdUrnInitially() {
            assertThat(context.getProfileIdUrn()).isEmpty();
        }
    }

    @Nested
    @DisplayName("initialize tests")
    class InitializeTests {

        @Test
        @DisplayName("should initialize with user")
        void shouldInitializeWithUser() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            User user = User.create(
                "testuser@king.com",
                "test@example.com",
                "Test",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profileId,
                Set.of(User.Role.SECURITY_ADMIN),
                "system"
            );

            context.initialize(
                "auth0|12345",
                "https://test.auth0.com/",
                List.of("openid", "profile"),
                "client-id",
                user
            );

            assertThat(context.isInitialized()).isTrue();
            assertThat(context.isAuth0Request()).isTrue();
            assertThat(context.getSubject()).isEqualTo("auth0|12345");
            assertThat(context.getIssuer()).isEqualTo("https://test.auth0.com/");
            assertThat(context.getScopes()).containsExactly("openid", "profile");
            assertThat(context.getAuthorizedParty()).isEqualTo("client-id");
            assertThat(context.getUser()).contains(user);
        }

        @Test
        @DisplayName("should initialize without user")
        void shouldInitializeWithoutUser() {
            context.initialize(
                "auth0|12345",
                "https://test.auth0.com/",
                List.of("openid"),
                "client-id",
                null
            );

            assertThat(context.isInitialized()).isTrue();
            assertThat(context.isAuth0Request()).isTrue();
            assertThat(context.getUser()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUser tests")
    class GetUserTests {

        @Test
        @DisplayName("should return user when set")
        void shouldReturnUserWhenSet() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            User user = User.create(
                "testuser@king.com",
                "test@example.com",
                "Test",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profileId,
                Set.of(User.Role.SECURITY_ADMIN),
                "system"
            );

            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", user);

            assertThat(context.getUser()).contains(user);
        }

        @Test
        @DisplayName("should return empty when user is null")
        void shouldReturnEmptyWhenUserIsNull() {
            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", null);

            assertThat(context.getUser()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getUserEmail tests")
    class GetUserEmailTests {

        @Test
        @DisplayName("should return user email when user set")
        void shouldReturnUserEmailWhenUserSet() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            User user = User.create(
                "testuser@king.com",
                "test@example.com",
                "Test",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profileId,
                Set.of(User.Role.SECURITY_ADMIN),
                "system"
            );

            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", user);

            assertThat(context.getUserEmail()).contains("test@example.com");
        }

        @Test
        @DisplayName("should return empty when user is null")
        void shouldReturnEmptyWhenUserIsNull() {
            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", null);

            assertThat(context.getUserEmail()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProfileId tests")
    class GetProfileIdTests {

        @Test
        @DisplayName("should return profile id when user set")
        void shouldReturnProfileIdWhenUserSet() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            User user = User.create(
                "testuser@king.com",
                "test@example.com",
                "Test",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profileId,
                Set.of(User.Role.SECURITY_ADMIN),
                "system"
            );

            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", user);

            assertThat(context.getProfileId()).contains(profileId);
        }

        @Test
        @DisplayName("should return empty when user is null")
        void shouldReturnEmptyWhenUserIsNull() {
            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", null);

            assertThat(context.getProfileId()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProfileIdUrn tests")
    class GetProfileIdUrnTests {

        @Test
        @DisplayName("should return profile id urn when user set")
        void shouldReturnProfileIdUrnWhenUserSet() {
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            User user = User.create(
                "testuser@king.com",
                "test@example.com",
                "Test",
                "User",
                User.UserType.CLIENT_USER,
                User.IdentityProvider.AUTH0,
                profileId,
                Set.of(User.Role.SECURITY_ADMIN),
                "system"
            );

            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", user);

            assertThat(context.getProfileIdUrn()).contains(profileId.urn());
        }

        @Test
        @DisplayName("should return empty when user is null")
        void shouldReturnEmptyWhenUserIsNull() {
            context.initialize("auth0|12345", "https://test.auth0.com/", List.of(), "client-id", null);

            assertThat(context.getProfileIdUrn()).isEmpty();
        }
    }
}
