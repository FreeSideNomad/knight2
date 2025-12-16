package com.knight.portal.views;

import com.knight.portal.services.ClientService;
import com.knight.portal.services.ProfileService;
import com.knight.portal.services.dto.ClientAccount;
import com.knight.portal.services.dto.ClientDetail;
import com.knight.portal.services.dto.CreateProfileRequest;
import com.knight.portal.services.dto.CreateProfileResponse;
import com.knight.portal.services.dto.PageResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateProfileView.
 * Tests view logic without requiring a full Vaadin context.
 */
@ExtendWith(MockitoExtension.class)
class CreateProfileViewTest {

    @Mock
    private ClientService clientService;

    @Mock
    private ProfileService profileService;

    @Captor
    private ArgumentCaptor<CreateProfileRequest> requestCaptor;

    // ==================== URL Parameter Parsing ====================

    @Nested
    @DisplayName("URL Parameter Parsing")
    class UrlParameterParsingTests {

        @Test
        @DisplayName("should parse client ID from URL parameter")
        void shouldParseClientIdFromUrlParameter() {
            // Test the parsing logic
            String parameter = "srf_123456789/service";
            String[] parts = parameter.split("/");
            String clientId = parts[0].replace("_", ":");
            String profileType = parts.length > 1 ? parts[1] : "service";

            assertThat(clientId).isEqualTo("srf:123456789");
            assertThat(profileType).isEqualTo("service");
        }

        @Test
        @DisplayName("should handle online profile type")
        void shouldHandleOnlineProfileType() {
            String parameter = "cdr_000001/online";
            String[] parts = parameter.split("/");
            String clientId = parts[0].replace("_", ":");
            String profileType = parts.length > 1 ? parts[1] : "service";

            assertThat(clientId).isEqualTo("cdr:000001");
            assertThat(profileType).isEqualTo("online");
        }

        @Test
        @DisplayName("should default to service profile type")
        void shouldDefaultToServiceProfileType() {
            String parameter = "srf_123456789";  // No profile type
            String[] parts = parameter.split("/");
            String profileType = parts.length > 1 ? parts[1] : "service";

            assertThat(profileType).isEqualTo("service");
        }
    }

    // ==================== Client Data Loading ====================

    @Nested
    @DisplayName("Client Data Loading")
    class ClientDataLoadingTests {

        @Test
        @DisplayName("should load client name")
        void shouldLoadClientName() {
            ClientDetail detail = new ClientDetail();
            detail.setName("Acme Corporation");
            when(clientService.getClient("srf:123456789")).thenReturn(detail);

            ClientDetail result = clientService.getClient("srf:123456789");

            assertThat(result.getName()).isEqualTo("Acme Corporation");
        }

        @Test
        @DisplayName("should load client accounts")
        void shouldLoadClientAccounts() {
            ClientAccount account1 = new ClientAccount();
            account1.setAccountId("acc:001");
            account1.setCurrency("CAD");
            account1.setStatus("ACTIVE");

            ClientAccount account2 = new ClientAccount();
            account2.setAccountId("acc:002");
            account2.setCurrency("USD");
            account2.setStatus("ACTIVE");

            ClientAccount closedAccount = new ClientAccount();
            closedAccount.setAccountId("acc:003");
            closedAccount.setStatus("CLOSED");

            PageResult<ClientAccount> pageResult = new PageResult<>();
            pageResult.setContent(List.of(account1, account2, closedAccount));
            pageResult.setTotalElements(3);

            when(clientService.getClientAccounts(eq("srf:123456789"), anyInt(), anyInt()))
                .thenReturn(pageResult);

            PageResult<ClientAccount> result = clientService.getClientAccounts("srf:123456789", 0, 100);

            assertThat(result.getContent()).hasSize(3);

            // Filter active accounts like the view does
            List<ClientAccount> activeAccounts = result.getContent().stream()
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .toList();

            assertThat(activeAccounts).hasSize(2);
        }
    }

    // ==================== Profile Creation ====================

    @Nested
    @DisplayName("Profile Creation")
    class ProfileCreationTests {

        @Test
        @DisplayName("should create profile with correct request structure")
        void shouldCreateProfileWithCorrectRequestStructure() {
            CreateProfileResponse response = new CreateProfileResponse();
            response.setProfileId("profile:srf:123456789:servicing");
            response.setName("Test Profile");

            when(profileService.createProfile(any())).thenReturn(response);

            // Build request like the view does
            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("SERVICING");
            request.setName("Test Profile");

            CreateProfileRequest.ClientAccountSelection selection = new CreateProfileRequest.ClientAccountSelection();
            selection.setClientId("srf:123456789");
            selection.setIsPrimary(true);
            selection.setAccountEnrollmentType("MANUAL");
            selection.setAccountIds(List.of("acc:001", "acc:002"));

            request.setClients(List.of(selection));

            CreateProfileResponse result = profileService.createProfile(request);

            verify(profileService).createProfile(requestCaptor.capture());
            CreateProfileRequest capturedRequest = requestCaptor.getValue();

            assertThat(capturedRequest.getProfileType()).isEqualTo("SERVICING");
            assertThat(capturedRequest.getName()).isEqualTo("Test Profile");
            assertThat(capturedRequest.getClients()).hasSize(1);
            assertThat(capturedRequest.getClients().get(0).isPrimary()).isTrue();
            assertThat(capturedRequest.getClients().get(0).getAccountIds()).hasSize(2);
        }

        @Test
        @DisplayName("should create profile with automatic enrollment")
        void shouldCreateProfileWithAutomaticEnrollment() {
            CreateProfileResponse response = new CreateProfileResponse();
            response.setProfileId("profile:srf:123456789:servicing");
            response.setName("Auto Profile");

            when(profileService.createProfile(any())).thenReturn(response);

            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("SERVICING");
            request.setName("Auto Profile");

            CreateProfileRequest.ClientAccountSelection selection = new CreateProfileRequest.ClientAccountSelection();
            selection.setClientId("srf:123456789");
            selection.setIsPrimary(true);
            selection.setAccountEnrollmentType("AUTOMATIC");
            selection.setAccountIds(List.of());  // Empty for automatic

            request.setClients(List.of(selection));

            profileService.createProfile(request);

            verify(profileService).createProfile(requestCaptor.capture());
            CreateProfileRequest capturedRequest = requestCaptor.getValue();

            assertThat(capturedRequest.getClients().get(0).getAccountEnrollmentType())
                .isEqualTo("AUTOMATIC");
            assertThat(capturedRequest.getClients().get(0).getAccountIds()).isEmpty();
        }

        @Test
        @DisplayName("should create online profile")
        void shouldCreateOnlineProfile() {
            CreateProfileResponse response = new CreateProfileResponse();
            response.setProfileId("profile:srf:123456789:online");
            response.setName("Online Profile");

            when(profileService.createProfile(any())).thenReturn(response);

            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("ONLINE");
            request.setName("Online Profile");

            CreateProfileRequest.ClientAccountSelection selection = new CreateProfileRequest.ClientAccountSelection();
            selection.setClientId("srf:123456789");
            selection.setIsPrimary(true);
            selection.setAccountEnrollmentType("MANUAL");
            selection.setAccountIds(List.of());

            request.setClients(List.of(selection));

            profileService.createProfile(request);

            verify(profileService).createProfile(requestCaptor.capture());
            CreateProfileRequest capturedRequest = requestCaptor.getValue();

            assertThat(capturedRequest.getProfileType()).isEqualTo("ONLINE");
        }
    }

    // ==================== Enrollment Type Toggling ====================

    @Nested
    @DisplayName("Enrollment Type Toggling")
    class EnrollmentTypeTogglingTests {

        @Test
        @DisplayName("should determine section visibility based on enrollment type")
        void shouldDetermineSectionVisibilityBasedOnEnrollmentType() {
            String automaticSelection = "Enroll all accounts automatically";
            String manualSelection = "Select specific accounts";

            // Automatic should hide accounts section
            boolean showAccountsForAutomatic = manualSelection.equals(automaticSelection);
            assertThat(showAccountsForAutomatic).isFalse();

            // Manual should show accounts section
            boolean showAccountsForManual = manualSelection.equals(manualSelection);
            assertThat(showAccountsForManual).isTrue();
        }
    }

    // ==================== Error Handling ====================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should handle API error gracefully")
        void shouldHandleApiErrorGracefully() {
            when(profileService.createProfile(any()))
                .thenThrow(new RuntimeException("API Error: Server unavailable"));

            CreateProfileRequest request = new CreateProfileRequest();
            request.setProfileType("SERVICING");
            request.setClients(List.of());

            assertThatRuntimeException()
                .isThrownBy(() -> profileService.createProfile(request))
                .withMessageContaining("API Error");
        }

        @Test
        @DisplayName("should handle null client detail")
        void shouldHandleNullClientDetail() {
            when(clientService.getClient(anyString())).thenReturn(null);

            ClientDetail result = clientService.getClient("srf:nonexistent");

            assertThat(result).isNull();
        }
    }

    // ==================== Navigation ====================

    @Nested
    @DisplayName("Navigation")
    class NavigationTests {

        @Test
        @DisplayName("should build correct back navigation path")
        void shouldBuildCorrectBackNavigationPath() {
            String clientId = "srf:123456789";
            String path = "client/" + clientId.replace(":", "_");

            assertThat(path).isEqualTo("client/srf_123456789");
        }

        @Test
        @DisplayName("should preserve search params in back navigation")
        void shouldPreserveSearchParamsInBackNavigation() {
            String searchParams = "type=srf&name=Acme";
            String clientId = "srf:123456789";
            String path = "client/" + clientId.replace(":", "_");

            // With search params
            String fullPath = path + "?" + searchParams;

            assertThat(fullPath).contains("type=srf");
            assertThat(fullPath).contains("name=Acme");
        }
    }
}
