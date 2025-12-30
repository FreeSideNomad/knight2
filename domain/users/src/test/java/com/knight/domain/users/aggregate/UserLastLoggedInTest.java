package com.knight.domain.users.aggregate;

import com.knight.domain.users.aggregate.User.IdentityProvider;
import com.knight.domain.users.aggregate.User.LockType;
import com.knight.domain.users.aggregate.User.Role;
import com.knight.domain.users.aggregate.User.Status;
import com.knight.domain.users.aggregate.User.UserType;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class specifically for testing the lastLoggedInAt functionality.
 * This is a focused test suite for US-UM-001: Track User Login Time.
 */
@DisplayName("User Last Logged In Tests")
class UserLastLoggedInTest {

    private static final String LOGIN_ID = "jdoe";
    private static final String EMAIL = "test@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final ProfileId PROFILE_ID = ProfileId.of("servicing", ClientId.of("srf:123456789"));
    private static final Set<Role> ROLES = Set.of(Role.READER);
    private static final String CREATED_BY = "admin@example.com";

    @Test
    @DisplayName("should have null lastLoggedInAt when user is created")
    void shouldHaveNullLastLoggedInAtWhenCreated() {
        // when
        User user = User.create(
            LOGIN_ID,
            EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            IdentityProvider.AUTH0,
            PROFILE_ID,
            ROLES,
            CREATED_BY
        );

        // then
        assertThat(user.lastLoggedInAt()).isNull();
    }

    @Test
    @DisplayName("should set lastLoggedInAt timestamp when recordLogin is called")
    void shouldSetLastLoggedInAtWhenRecordLoginCalled() {
        // given
        User user = createUser();
        Instant beforeLogin = Instant.now().minusMillis(100);

        // when
        user.recordLogin();

        // then
        Instant afterLogin = Instant.now().plusMillis(100);
        assertThat(user.lastLoggedInAt()).isNotNull();
        assertThat(user.lastLoggedInAt()).isAfter(beforeLogin);
        assertThat(user.lastLoggedInAt()).isBefore(afterLogin);
    }

    @Test
    @DisplayName("should update lastLoggedInAt on subsequent logins")
    void shouldUpdateLastLoggedInAtOnSubsequentLogins() throws Exception {
        // given
        User user = createUser();
        user.recordLogin();
        Instant firstLogin = user.lastLoggedInAt();

        // Wait a bit to ensure different timestamp
        Thread.sleep(10);

        // when
        user.recordLogin();

        // then
        assertThat(user.lastLoggedInAt()).isNotNull();
        assertThat(user.lastLoggedInAt()).isAfter(firstLogin);
    }

    @Test
    @DisplayName("should update updatedAt when recordLogin is called")
    void shouldUpdateUpdatedAtWhenRecordLoginCalled() {
        // given
        User user = createUser();
        Instant originalUpdatedAt = user.updatedAt();

        // when
        user.recordLogin();

        // then
        assertThat(user.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("should allow multiple consecutive logins")
    void shouldAllowMultipleConsecutiveLogins() throws Exception {
        // given
        User user = createUser();

        // when - record multiple logins
        user.recordLogin();
        Instant firstLogin = user.lastLoggedInAt();

        Thread.sleep(10);
        user.recordLogin();
        Instant secondLogin = user.lastLoggedInAt();

        Thread.sleep(10);
        user.recordLogin();
        Instant thirdLogin = user.lastLoggedInAt();

        // then
        assertThat(firstLogin).isNotNull();
        assertThat(secondLogin).isAfter(firstLogin);
        assertThat(thirdLogin).isAfter(secondLogin);
    }

    @Test
    @DisplayName("should correctly reconstitute user with lastLoggedInAt")
    void shouldReconstituteUserWithLastLoggedInAt() {
        // given
        UserId userId = UserId.of(UUID.randomUUID().toString());
        Instant lastLoggedInAt = Instant.now().minusSeconds(600);
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now().minusSeconds(300);

        // when
        User user = User.reconstitute(
            userId,
            LOGIN_ID,
            EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            IdentityProvider.AUTH0,
            PROFILE_ID,
            ROLES,
            "auth0|123456",
            true,
            true,
            Instant.now().minusSeconds(900),
            lastLoggedInAt,
            Status.ACTIVE,
            LockType.NONE,
            null,
            null,
            null,
            createdAt,
            CREATED_BY,
            updatedAt
        );

        // then
        assertThat(user.lastLoggedInAt()).isEqualTo(lastLoggedInAt);
    }

    @Test
    @DisplayName("should reconstitute user with null lastLoggedInAt when not provided")
    void shouldReconstituteUserWithNullLastLoggedInAt() {
        // given
        UserId userId = UserId.of(UUID.randomUUID().toString());

        // when
        User user = User.reconstitute(
            userId,
            LOGIN_ID,
            EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            IdentityProvider.AUTH0,
            PROFILE_ID,
            ROLES,
            "auth0|123",
            true,
            true,
            Instant.now(),
            null,
            Status.ACTIVE,
            LockType.NONE,
            null,
            null,
            null,
            Instant.now().minusSeconds(3600),
            CREATED_BY,
            Instant.now()
        );

        // then
        assertThat(user.lastLoggedInAt()).isNull();
    }

    private User createUser() {
        return User.create(
            LOGIN_ID,
            EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            IdentityProvider.AUTH0,
            PROFILE_ID,
            ROLES,
            CREATED_BY
        );
    }
}
