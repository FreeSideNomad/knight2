package com.knight.domain.serviceprofiles.service;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
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
import java.util.Set;

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
    private ClientAccountRepository clientAccountRepository;

    @Mock
    private ClientNameResolver clientNameResolver;

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
            when(clientNameResolver.resolveName(PRIMARY_CLIENT_ID)).thenReturn(Optional.of("Acme Corporation"));

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

    // ==================== Secondary Client Management ====================

    @Nested
    @DisplayName("Add Secondary Client")
    class AddSecondaryClientTests {

        @Test
        @DisplayName("should add secondary client with manual enrollment")
        void shouldAddSecondaryClientWithManualEnrollment() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollService("PAYMENT", "{}"); // Activate profile
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            ClientId secondaryClientId = new SrfClientId("987654321");
            ProfileCommands.AddSecondaryClientCmd cmd = new ProfileCommands.AddSecondaryClientCmd(
                profileId,
                secondaryClientId,
                AccountEnrollmentType.MANUAL,
                List.of(ACCOUNT_ID_1)
            );

            service.addSecondaryClient(cmd);

            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.clientEnrollments()).hasSize(2);
        }

        @Test
        @DisplayName("should add secondary client with automatic enrollment")
        void shouldAddSecondaryClientWithAutomaticEnrollment() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollService("PAYMENT", "{}");
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            ClientId secondaryClientId = new SrfClientId("987654321");

            ClientAccount account = mock(ClientAccount.class);
            when(account.status()).thenReturn(AccountStatus.ACTIVE);
            when(account.accountId()).thenReturn(ACCOUNT_ID_1);
            when(clientAccountRepository.findByClientId(secondaryClientId)).thenReturn(List.of(account));

            ProfileCommands.AddSecondaryClientCmd cmd = new ProfileCommands.AddSecondaryClientCmd(
                profileId,
                secondaryClientId,
                AccountEnrollmentType.AUTOMATIC,
                List.of()
            );

            service.addSecondaryClient(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should handle null account IDs for manual enrollment")
        void shouldHandleNullAccountIdsForManualEnrollment() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollService("PAYMENT", "{}");
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            ClientId secondaryClientId = new SrfClientId("987654321");
            ProfileCommands.AddSecondaryClientCmd cmd = new ProfileCommands.AddSecondaryClientCmd(
                profileId,
                secondaryClientId,
                AccountEnrollmentType.MANUAL,
                null  // null account IDs
            );

            service.addSecondaryClient(cmd);

            verify(profileRepository).save(any(Profile.class));
        }

        @Test
        @DisplayName("should throw when profile not found for add secondary client")
        void shouldThrowWhenProfileNotFoundForAddSecondaryClient() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            ProfileCommands.AddSecondaryClientCmd cmd = new ProfileCommands.AddSecondaryClientCmd(
                profileId,
                new SrfClientId("987654321"),
                AccountEnrollmentType.MANUAL,
                List.of()
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addSecondaryClient(cmd))
                .withMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Remove Secondary Client")
    class RemoveSecondaryClientTests {

        @Test
        @DisplayName("should remove secondary client")
        void shouldRemoveSecondaryClient() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollService("PAYMENT", "{}");
            ClientId secondaryClientId = new SrfClientId("987654321");
            mockProfile.addSecondaryClient(secondaryClientId, AccountEnrollmentType.MANUAL, List.of());
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));

            ProfileCommands.RemoveSecondaryClientCmd cmd = new ProfileCommands.RemoveSecondaryClientCmd(
                profileId,
                secondaryClientId
            );

            service.removeSecondaryClient(cmd);

            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.clientEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should throw when profile not found for remove secondary client")
        void shouldThrowWhenProfileNotFoundForRemoveSecondaryClient() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            ProfileCommands.RemoveSecondaryClientCmd cmd = new ProfileCommands.RemoveSecondaryClientCmd(
                profileId,
                new SrfClientId("987654321")
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeSecondaryClient(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Indirect Client Resolution ====================

    @Nested
    @DisplayName("Indirect Client Resolution")
    class IndirectClientResolutionTests {

        @Test
        @DisplayName("should use indirect client name when profile name is blank")
        void shouldUseIndirectClientNameWhenProfileNameBlank() {
            IndirectClientId indirectClientId = IndirectClientId.generate();
            when(clientNameResolver.resolveName(indirectClientId)).thenReturn(Optional.of("Payor Inc."));

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.INDIRECT,
                "",  // Blank name - should use indirect client name
                List.of(new ClientAccountSelection(
                    indirectClientId,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            service.createProfileWithAccounts(cmd);

            verify(profileRepository).save(profileCaptor.capture());
            Profile savedProfile = profileCaptor.getValue();
            assertThat(savedProfile.name()).isEqualTo("Payor Inc.");
        }

        @Test
        @DisplayName("should throw when indirect client not found")
        void shouldThrowWhenIndirectClientNotFound() {
            IndirectClientId indirectClientId = IndirectClientId.generate();
            when(clientNameResolver.resolveName(indirectClientId)).thenReturn(Optional.empty());

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.INDIRECT,
                "",
                List.of(new ClientAccountSelection(
                    indirectClientId,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createProfileWithAccounts(cmd))
                .withMessageContaining("Client not found");
        }

        @Test
        @DisplayName("should throw when regular client not found")
        void shouldThrowWhenRegularClientNotFound() {
            when(clientNameResolver.resolveName(PRIMARY_CLIENT_ID)).thenReturn(Optional.empty());

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.SERVICING,
                "",
                List.of(new ClientAccountSelection(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )),
                "testUser"
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createProfileWithAccounts(cmd))
                .withMessageContaining("Client not found");
        }

        @Test
        @DisplayName("should find active accounts for indirect client")
        void shouldFindActiveAccountsForIndirectClient() {
            IndirectClientId indirectClientId = IndirectClientId.generate();

            ClientAccount account = mock(ClientAccount.class);
            when(account.status()).thenReturn(AccountStatus.ACTIVE);
            when(account.accountId()).thenReturn(ACCOUNT_ID_1);
            when(clientAccountRepository.findByIndirectClientId(indirectClientId.urn())).thenReturn(List.of(account));

            CreateProfileWithAccountsCmd cmd = new CreateProfileWithAccountsCmd(
                ProfileType.INDIRECT,
                "Indirect Profile",
                List.of(new ClientAccountSelection(
                    indirectClientId,
                    true,
                    AccountEnrollmentType.AUTOMATIC,
                    List.of()
                )),
                "testUser"
            );

            service.createProfileWithAccounts(cmd);

            verify(clientAccountRepository).findByIndirectClientId(indirectClientId.urn());
            verify(profileRepository).save(profileCaptor.capture());
            assertThat(profileCaptor.getValue().accountEnrollments()).hasSize(1);
        }
    }

    // ==================== Search Methods ====================

    @Nested
    @DisplayName("Search Methods")
    class SearchMethodsTests {

        @Test
        @DisplayName("should search by primary client")
        void shouldSearchByPrimaryClient() {
            Profile mockProfile = createMockProfile();
            ServicingProfileRepository.PageResult<Profile> pageResult = new ServicingProfileRepository.PageResult<>(
                List.of(mockProfile), 1L, 0, 10
            );
            when(profileRepository.searchByPrimaryClient(PRIMARY_CLIENT_ID, Set.of("SERVICING"), 0, 10))
                .thenReturn(pageResult);

            PageResult<ProfileSummary> result = service.searchByPrimaryClient(PRIMARY_CLIENT_ID, Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1L);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
        }

        @Test
        @DisplayName("should search by client")
        void shouldSearchByClient() {
            Profile mockProfile = createMockProfile();
            ServicingProfileRepository.PageResult<Profile> pageResult = new ServicingProfileRepository.PageResult<>(
                List.of(mockProfile), 1L, 0, 10
            );
            when(profileRepository.searchByClient(PRIMARY_CLIENT_ID, Set.of("SERVICING", "ONLINE"), 0, 10))
                .thenReturn(pageResult);

            PageResult<ProfileSummary> result = service.searchByClient(PRIMARY_CLIENT_ID, Set.of("SERVICING", "ONLINE"), 0, 10);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("should search by client name - primary only")
        void shouldSearchByClientNamePrimaryOnly() {
            Profile mockProfile = createMockProfile();
            ServicingProfileRepository.PageResult<Profile> pageResult = new ServicingProfileRepository.PageResult<>(
                List.of(mockProfile), 1L, 0, 10
            );
            when(profileRepository.searchByPrimaryClientName("Acme", Set.of("SERVICING"), 0, 10))
                .thenReturn(pageResult);

            PageResult<ProfileSummary> result = service.searchByClientName("Acme", true, Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).hasSize(1);
            verify(profileRepository).searchByPrimaryClientName("Acme", Set.of("SERVICING"), 0, 10);
        }

        @Test
        @DisplayName("should search by client name - all clients")
        void shouldSearchByClientNameAllClients() {
            Profile mockProfile = createMockProfile();
            ServicingProfileRepository.PageResult<Profile> pageResult = new ServicingProfileRepository.PageResult<>(
                List.of(mockProfile), 1L, 0, 10
            );
            when(profileRepository.searchByClientName("Corp", Set.of("SERVICING"), 0, 10))
                .thenReturn(pageResult);

            PageResult<ProfileSummary> result = service.searchByClientName("Corp", false, Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).hasSize(1);
            verify(profileRepository).searchByClientName("Corp", Set.of("SERVICING"), 0, 10);
        }

        @Test
        @DisplayName("should calculate total pages correctly")
        void shouldCalculateTotalPagesCorrectly() {
            Profile mockProfile = createMockProfile();
            ServicingProfileRepository.PageResult<Profile> pageResult = new ServicingProfileRepository.PageResult<>(
                List.of(mockProfile), 25L, 0, 10
            );
            when(profileRepository.searchByPrimaryClient(PRIMARY_CLIENT_ID, Set.of(), 0, 10))
                .thenReturn(pageResult);

            PageResult<ProfileSummary> result = service.searchByPrimaryClient(PRIMARY_CLIENT_ID, Set.of(), 0, 10);

            assertThat(result.totalPages()).isEqualTo(3); // ceil(25/10) = 3
        }
    }

    // ==================== Profile Detail ====================

    @Nested
    @DisplayName("Profile Detail")
    class ProfileDetailTests {

        @Test
        @DisplayName("should get profile detail with all enrollments")
        void shouldGetProfileDetailWithAllEnrollments() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollService("PAYMENT", "{\"key\":\"value\"}");
            mockProfile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));
            when(clientNameResolver.resolveNameOrDefault(PRIMARY_CLIENT_ID, "Unknown")).thenReturn("Test Client");

            ProfileDetail detail = service.getProfileDetail(profileId);

            assertThat(detail.profileId()).isEqualTo(profileId.urn());
            assertThat(detail.name()).isEqualTo("Mock Profile");
            assertThat(detail.profileType()).isEqualTo("SERVICING");
            assertThat(detail.clientEnrollments()).hasSize(1);
            assertThat(detail.clientEnrollments().get(0).clientName()).isEqualTo("Test Client");
            assertThat(detail.serviceEnrollments()).hasSize(1);
            assertThat(detail.serviceEnrollments().get(0).serviceType()).isEqualTo("PAYMENT");
            assertThat(detail.accountEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should handle unknown client name gracefully")
        void shouldHandleUnknownClientNameGracefully() {
            Profile mockProfile = createMockProfile();
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));
            when(clientNameResolver.resolveNameOrDefault(PRIMARY_CLIENT_ID, "Unknown")).thenReturn("Unknown");

            ProfileDetail detail = service.getProfileDetail(profileId);

            assertThat(detail.clientEnrollments().get(0).clientName()).isEqualTo("Unknown");
        }

        @Test
        @DisplayName("should throw when profile not found for detail")
        void shouldThrowWhenProfileNotFoundForDetail() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:999999999");
            when(profileRepository.findById(profileId)).thenReturn(Optional.empty());

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.getProfileDetail(profileId))
                .withMessageContaining("not found");
        }

        @Test
        @DisplayName("should include service enrollment ID in account enrollment info")
        void shouldIncludeServiceEnrollmentIdInAccountEnrollmentInfo() {
            Profile mockProfile = createMockProfile();
            mockProfile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            Profile.ServiceEnrollment svcEnrollment = mockProfile.enrollService("PAYMENT", "{}");
            mockProfile.enrollAccountToService(svcEnrollment.enrollmentId(), PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            ProfileId profileId = mockProfile.profileId();
            when(profileRepository.findById(profileId)).thenReturn(Optional.of(mockProfile));
            when(clientNameResolver.resolveNameOrDefault(PRIMARY_CLIENT_ID, "Unknown")).thenReturn("Test Client");

            ProfileDetail detail = service.getProfileDetail(profileId);

            // Profile-level enrollment should have null serviceEnrollmentId
            AccountEnrollmentInfo profileLevelEnrollment = detail.accountEnrollments().stream()
                .filter(ae -> ae.serviceEnrollmentId() == null)
                .findFirst()
                .orElseThrow();
            assertThat(profileLevelEnrollment.serviceEnrollmentId()).isNull();

            // Service-level enrollment should have non-null serviceEnrollmentId
            AccountEnrollmentInfo serviceLevelEnrollment = detail.accountEnrollments().stream()
                .filter(ae -> ae.serviceEnrollmentId() != null)
                .findFirst()
                .orElseThrow();
            assertThat(serviceLevelEnrollment.serviceEnrollmentId()).isEqualTo(svcEnrollment.enrollmentId().toString());
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
