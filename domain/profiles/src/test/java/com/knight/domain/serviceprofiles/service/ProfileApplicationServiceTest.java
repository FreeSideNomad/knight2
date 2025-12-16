package com.knight.domain.serviceprofiles.service;

import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.aggregate.Profile.*;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.serviceprofiles.api.commands.ProfileCommands.*;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries.ServicingProfileSummary;
import com.knight.domain.serviceprofiles.api.events.ProfileCreated;
import com.knight.domain.serviceprofiles.api.queries.ProfileQueries.*;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProfileApplicationService.
 * Tests command handling and orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
class ProfileApplicationServiceTest {

    @Mock
    private ServicingProfileRepository profileRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientAccountRepository clientAccountRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ProfileApplicationService service;

    @Captor
    private ArgumentCaptor<Profile> profileCaptor;

    @Captor
    private ArgumentCaptor<ProfileCreated> eventCaptor;

    private static final ClientId PRIMARY_CLIENT_ID = new SrfClientId("123456789");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");

    // ==================== Create Profile ====================

    @Nested
    @DisplayName("Create Profile With Accounts")
    class CreateProfileWithAccountsTests {

        @Test
        @DisplayName("should create profile with accounts command")
        void shouldCreateProfileWithAccountsCommand() {
            // Arrange
            when(profileRepository.existsServicingProfileWithPrimaryClient(any())).thenReturn(false);

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Test Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1)
                )),
                "testUser"
            );

            // Act
            ProfileId result = service.createProfileWithAccounts(cmd);

            // Assert
            assertThat(result).isNotNull();
            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.name()).isEqualTo("Test Profile");
            assertThat(savedProfile.profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(savedProfile.accountEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should resolve accounts for automatic enrollment")
        void shouldResolveAccountsForAutomaticEnrollment() {
            // Arrange
            when(profileRepository.existsServicingProfileWithPrimaryClient(any())).thenReturn(false);

            ClientAccount account1 = mock(ClientAccount.class);
            when(account1.status()).thenReturn(AccountStatus.ACTIVE);
            when(account1.accountId()).thenReturn(ACCOUNT_ID_1);

            ClientAccount account2 = mock(ClientAccount.class);
            when(account2.status()).thenReturn(AccountStatus.ACTIVE);
            when(account2.accountId()).thenReturn(ACCOUNT_ID_2);

            ClientAccount closedAccount = mock(ClientAccount.class);
            when(closedAccount.status()).thenReturn(AccountStatus.CLOSED);

            when(clientAccountRepository.findByClientId(PRIMARY_CLIENT_ID))
                .thenReturn(List.of(account1, account2, closedAccount));

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Auto Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.AUTOMATIC,
                    List.of()  // Empty for AUTOMATIC
                )),
                "testUser"
            );

            // Act
            service.createProfileWithAccounts(cmd);

            // Assert
            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            // Should only include active accounts (2), not the closed one
            assertThat(savedProfile.accountEnrollments()).hasSize(2);
        }

        @Test
        @DisplayName("should use client name when profile name is blank")
        void shouldUseClientNameWhenProfileNameBlank() {
            // Arrange
            when(profileRepository.existsServicingProfileWithPrimaryClient(any())).thenReturn(false);

            Client mockClient = mock(Client.class);
            when(mockClient.name()).thenReturn("Acme Corporation");
            when(clientRepository.findById(PRIMARY_CLIENT_ID)).thenReturn(Optional.of(mockClient));

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "",  // Blank name
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            // Act
            service.createProfileWithAccounts(cmd);

            // Assert
            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.name()).isEqualTo("Acme Corporation");
        }

        @Test
        @DisplayName("should validate primary client uniqueness for servicing profiles")
        void shouldValidatePrimaryClientUniquenessForServicing() {
            // Arrange
            when(profileRepository.existsServicingProfileWithPrimaryClient(PRIMARY_CLIENT_ID)).thenReturn(true);

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Duplicate Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            // Act & Assert
            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createProfileWithAccounts(cmd))
                .withMessageContaining("already primary");
        }

        @Test
        @DisplayName("should skip uniqueness check for online profiles")
        void shouldSkipUniquenessCheckForOnlineProfiles() {
            // Arrange - don't stub the exists check since it shouldn't be called for ONLINE

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.ONLINE,
                "Online Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            // Act
            service.createProfileWithAccounts(cmd);

            // Assert
            verify(profileRepository, never()).existsServicingProfileWithPrimaryClient(any());
            verify(profileRepository).save(any());
        }

        @Test
        @DisplayName("should publish ProfileCreated event")
        void shouldPublishProfileCreatedEvent() {
            // Arrange
            when(profileRepository.existsServicingProfileWithPrimaryClient(any())).thenReturn(false);

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Event Test Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            // Act
            service.createProfileWithAccounts(cmd);

            // Assert
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            ProfileCreated event = eventCaptor.getValue();
            assertThat(event.name()).isEqualTo("Event Test Profile");
            assertThat(event.profileType()).isEqualTo("SERVICING");
            assertThat(event.createdBy()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("should reject command with no primary client")
        void shouldRejectCommandWithNoPrimaryClient() {
            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "Invalid Profile",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    false,  // Not primary
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createProfileWithAccounts(cmd))
                .withMessageContaining("primary");
        }
    }

    // ==================== Query Methods ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodsTests {

        @Test
        @DisplayName("should get profile summary")
        void shouldGetProfileSummary() {
            // Arrange
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            // Act
            ProfileSummary summary = service.getProfileSummary(profileId);

            // Assert
            assertThat(summary.profileId()).isEqualTo(profileId.urn());
            assertThat(summary.name()).isEqualTo("Mock Profile");
        }

        @Test
        @DisplayName("should throw when profile not found")
        void shouldThrowWhenProfileNotFound() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getProfileSummary(profileId))
                .withMessageContaining("not found");
        }

        @Test
        @DisplayName("should find primary profiles")
        void shouldFindPrimaryProfiles() {
            Profile mockProfile = createMockProfile();
            when(profileRepository.findByPrimaryClient(PRIMARY_CLIENT_ID))
                .thenReturn(List.of(mockProfile));

            List<ProfileSummary> results = service.findPrimaryProfiles(PRIMARY_CLIENT_ID);

            assertThat(results).hasSize(1);
            verify(profileRepository).findByPrimaryClient(PRIMARY_CLIENT_ID);
        }

        @Test
        @DisplayName("should find secondary profiles")
        void shouldFindSecondaryProfiles() {
            when(profileRepository.findBySecondaryClient(PRIMARY_CLIENT_ID))
                .thenReturn(List.of());

            List<ProfileSummary> results = service.findSecondaryProfiles(PRIMARY_CLIENT_ID);

            assertThat(results).isEmpty();
            verify(profileRepository).findBySecondaryClient(PRIMARY_CLIENT_ID);
        }

        @Test
        @DisplayName("should find all profiles for client")
        void shouldFindAllProfilesForClient() {
            Profile mockProfile = createMockProfile();
            when(profileRepository.findByClient(PRIMARY_CLIENT_ID))
                .thenReturn(List.of(mockProfile));

            List<ProfileSummary> results = service.findAllProfilesForClient(PRIMARY_CLIENT_ID);

            assertThat(results).hasSize(1);
            verify(profileRepository).findByClient(PRIMARY_CLIENT_ID);
        }
    }

    // ==================== Command Methods ====================

    @Nested
    @DisplayName("Enroll Service")
    class EnrollServiceTests {

        @Test
        @DisplayName("should enroll service to profile")
        void shouldEnrollServiceToProfile() {
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            EnrollServiceCmd cmd = new EnrollServiceCmd(profileId, "PAYMENT", "{\"key\": \"value\"}");
            service.enrollService(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should throw when profile not found for enroll service")
        void shouldThrowWhenProfileNotFoundForEnrollService() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            EnrollServiceCmd cmd = new EnrollServiceCmd(profileId, "PAYMENT", "{}");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.enrollService(cmd))
                .withMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Enroll Account")
    class EnrollAccountTests {

        @Test
        @DisplayName("should enroll account to profile")
        void shouldEnrollAccountToProfile() {
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            EnrollAccountCmd cmd = new EnrollAccountCmd(profileId, PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            service.enrollAccount(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should throw when profile not found for enroll account")
        void shouldThrowWhenProfileNotFoundForEnrollAccount() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            EnrollAccountCmd cmd = new EnrollAccountCmd(profileId, PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.enrollAccount(cmd))
                .withMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Enroll Account To Service")
    class EnrollAccountToServiceTests {

        @Test
        @DisplayName("should enroll account to service")
        void shouldEnrollAccountToService() {
            Profile mockProfile = createMockProfile();
            // First enroll account to profile
            mockProfile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            // Then add a service
            mockProfile.enrollService("PAYMENT", "{}");
            ProfileId profileId = mockProfile.profileId();
            EnrollmentId serviceEnrollmentId = mockProfile.serviceEnrollments().get(0).enrollmentId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            EnrollAccountToServiceCmd cmd = new EnrollAccountToServiceCmd(
                profileId, serviceEnrollmentId, PRIMARY_CLIENT_ID, ACCOUNT_ID_1
            );
            service.enrollAccountToService(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should throw when profile not found for enroll account to service")
        void shouldThrowWhenProfileNotFoundForEnrollAccountToService() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            EnrollAccountToServiceCmd cmd = new EnrollAccountToServiceCmd(
                profileId, EnrollmentId.generate(), PRIMARY_CLIENT_ID, ACCOUNT_ID_1
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.enrollAccountToService(cmd))
                .withMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Suspend Profile")
    class SuspendProfileTests {

        @Test
        @DisplayName("should suspend profile")
        void shouldSuspendProfile() {
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            SuspendProfileCmd cmd = new SuspendProfileCmd(profileId, "Compliance review");
            service.suspendProfile(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should throw when profile not found for suspend")
        void shouldThrowWhenProfileNotFoundForSuspend() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            SuspendProfileCmd cmd = new SuspendProfileCmd(profileId, "Test reason");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.suspendProfile(cmd))
                .withMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Deprecated Methods")
    class DeprecatedMethodsTests {

        @Test
        @DisplayName("should get servicing profile summary")
        @SuppressWarnings("deprecation")
        void shouldGetServicingProfileSummary() {
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            ServicingProfileSummary summary = service.getServicingProfileSummary(profileId);

            assertThat(summary.profileUrn()).isEqualTo(profileId.urn());
            assertThat(summary.status()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should create servicing profile using deprecated method")
        @SuppressWarnings("deprecation")
        void shouldCreateServicingProfileDeprecated() {
            ProfileId result = service.createServicingProfile(PRIMARY_CLIENT_ID, "testUser");

            assertThat(result).isNotNull();
            verify(profileRepository).save(any(Profile.class));
            verify(eventPublisher).publishEvent(any(ProfileCreated.class));
        }

        @Test
        @DisplayName("should create online profile using deprecated method")
        @SuppressWarnings("deprecation")
        void shouldCreateOnlineProfileDeprecated() {
            ProfileId result = service.createOnlineProfile(PRIMARY_CLIENT_ID, "testUser");

            assertThat(result).isNotNull();
            verify(profileRepository).save(any(Profile.class));
            verify(eventPublisher).publishEvent(any(ProfileCreated.class));
        }
    }

    // ==================== Helper Methods ====================

    private Profile createMockProfile() {
        List<ClientEnrollmentRequest> requests = List.of(
            new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of())
        );

        return Profile.createWithAccounts(
            ProfileType.SERVICING,
            "Mock Profile",
            requests,
            "testUser"
        );
    }
}
