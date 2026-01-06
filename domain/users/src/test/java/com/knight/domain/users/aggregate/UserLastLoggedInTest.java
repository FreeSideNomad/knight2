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

    private static final String LOGIN_ID = "jdoe@king.com";
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
            true,   // emailVerified
            true,   // passwordSet
            true,   // mfaEnrolled
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
            true,   // emailVerified
            true,   // passwordSet
            true,   // mfaEnrolled
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

    // ==================== Sync Event Separation Tests (US-UM-002) ====================

    @Test
    @DisplayName("recordSync should only update lastSyncedAt, not lastLoggedInAt")
    void recordSyncShouldOnlyUpdateLastSyncedAt() {
        // given
        User user = createUser();

        // when
        user.recordSync();

        // then
        assertThat(user.lastSyncedAt()).isNotNull();
        assertThat(user.lastLoggedInAt()).isNull(); // Should remain null
    }

    @Test
    @DisplayName("recordLogin should only update lastLoggedInAt, not lastSyncedAt")
    void recordLoginShouldOnlyUpdateLastLoggedInAt() {
        // given
        User user = createUser();
        Instant initialSyncedAt = user.lastSyncedAt(); // should be null

        // when
        user.recordLogin();

        // then
        assertThat(user.lastLoggedInAt()).isNotNull();
        assertThat(user.lastSyncedAt()).isEqualTo(initialSyncedAt); // Should remain unchanged
    }

    @Test
    @DisplayName("login and sync events should track independently")
    void loginAndSyncEventsShouldTrackIndependently() throws Exception {
        // given
        User user = createUser();

        // when - record a sync, then a login
        user.recordSync();
        Instant syncTime = user.lastSyncedAt();
        Thread.sleep(10);

        user.recordLogin();
        Instant loginTime = user.lastLoggedInAt();

        // then
        assertThat(user.lastSyncedAt()).isEqualTo(syncTime); // Sync should not change
        assertThat(user.lastLoggedInAt()).isEqualTo(loginTime);
        assertThat(loginTime).isAfter(syncTime);
    }

    @Test
    @DisplayName("login and sync timestamps can have different values")
    void loginAndSyncTimestampsCanDiffer() throws Exception {
        // given
        User user = createUser();

        // when - record sync first, then login later
        user.recordSync();
        Instant firstSyncTime = user.lastSyncedAt();
        Thread.sleep(20);

        user.recordLogin();
        Instant loginTime = user.lastLoggedInAt();
        Thread.sleep(20);

        user.recordSync(); // Another sync event
        Instant secondSyncTime = user.lastSyncedAt();

        // then
        assertThat(secondSyncTime).isAfter(firstSyncTime);
        assertThat(loginTime).isAfter(firstSyncTime);
        assertThat(secondSyncTime).isAfter(loginTime); // Latest sync is after login
        assertThat(user.lastLoggedInAt()).isEqualTo(loginTime); // Login unchanged by sync
    }

    @Test
    @DisplayName("updateOnboardingStatus should update lastSyncedAt, not lastLoggedInAt")
    void updateOnboardingStatusShouldUpdateLastSyncedAt() {
        // given
        User user = createUser();
        user.markProvisioned("auth0|12345");

        // when
        user.updateOnboardingStatus(true, true, false);

        // then
        assertThat(user.lastSyncedAt()).isNotNull(); // Should be set
        assertThat(user.lastLoggedInAt()).isNull(); // Should remain null
    }

    @Test
    @DisplayName("recordSync should update updatedAt")
    void recordSyncShouldUpdateUpdatedAt() throws Exception {
        // given
        User user = createUser();
        Thread.sleep(10);
        Instant originalUpdatedAt = user.updatedAt();

        // when
        Thread.sleep(10);
        user.recordSync();

        // then
        assertThat(user.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    @DisplayName("multiple sync events should update lastSyncedAt each time")
    void multipleSyncEventsShouldUpdateLastSyncedAt() throws Exception {
        // given
        User user = createUser();

        // when
        user.recordSync();
        Instant firstSync = user.lastSyncedAt();
        Thread.sleep(10);

        user.recordSync();
        Instant secondSync = user.lastSyncedAt();
        Thread.sleep(10);

        user.recordSync();
        Instant thirdSync = user.lastSyncedAt();

        // then
        assertThat(secondSync).isAfter(firstSync);
        assertThat(thirdSync).isAfter(secondSync);
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
