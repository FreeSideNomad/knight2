package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.CreateUserRequest;
import com.knight.indirectportal.services.dto.UserDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(webClient);
    }

    @Test
    void getUsers_returnsListOfUsers() {
        // Given
        List<UserDetail> expectedUsers = List.of(
            createUserDetail("user-1", "john@example.com", "John", "Doe"),
            createUserDetail("user-2", "jane@example.com", "Jane", "Doe")
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/users");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedUsers)).when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<UserDetail> result = userService.getUsers();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getEmail()).isEqualTo("john@example.com");
        assertThat(result.get(1).getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void getUsers_returnsEmptyListOnError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/users");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Connection error")))
            .when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<UserDetail> result = userService.getUsers();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getUser_returnsUserDetail() {
        // Given
        String userId = "user-1";
        UserDetail expectedUser = createUserDetail(userId, "john@example.com", "John", "Doe");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/users/{userId}", userId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedUser)).when(responseSpec).bodyToMono(UserDetail.class);

        // When
        UserDetail result = userService.getUser(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void getUser_returnsNullOnError() {
        // Given
        String userId = "non-existent";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/users/{userId}", userId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Not found")))
            .when(responseSpec).bodyToMono(UserDetail.class);

        // When
        UserDetail result = userService.getUser(userId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void getUserById_callsGetUser() {
        // Given
        String userId = "user-1";
        UserDetail expectedUser = createUserDetail(userId, "john@example.com", "John", "Doe");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/users/{userId}", userId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedUser)).when(responseSpec).bodyToMono(UserDetail.class);

        // When
        UserDetail result = userService.getUserById(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
    }

    @Test
    void createUser_createsAndReturnsUser() {
        // Given
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new@example.com");
        request.setFirstName("New");
        request.setLastName("User");
        request.setRoles(Set.of("VIEWER"));
        UserDetail expectedUser = createUserDetail("user-new", "new@example.com", "New", "User");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users");
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(request);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedUser)).when(responseSpec).bodyToMono(UserDetail.class);

        // When
        UserDetail result = userService.createUser(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void lockUser_withLockType_locksUser() {
        // Given
        String userId = "user-1";
        String lockType = "ADMIN";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/lock", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then - no exception means success
        userService.lockUser(userId, lockType);

        verify(webClient).post();
    }

    @Test
    void lockUser_withDefaultLockType_usesClientType() {
        // Given
        String userId = "user-1";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/lock", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When
        userService.lockUser(userId);

        // Then
        verify(webClient).post();
    }

    @Test
    void unlockUser_unlocksUser() {
        // Given
        String userId = "user-1";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/unlock", userId);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then
        userService.unlockUser(userId);

        verify(webClient).post();
    }

    @Test
    void deactivateUser_withReason_deactivatesUser() {
        // Given
        String userId = "user-1";
        String reason = "User requested deletion";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/deactivate", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then
        userService.deactivateUser(userId, reason);

        verify(webClient).post();
    }

    @Test
    void deactivateUser_withoutReason_deactivatesUser() {
        // Given
        String userId = "user-1";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/deactivate", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When
        userService.deactivateUser(userId);

        // Then
        verify(webClient).post();
    }

    @Test
    void resendInvitation_sendsInvitation() {
        // Given
        String userId = "user-1";

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/resend-invitation", userId);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then
        userService.resendInvitation(userId);

        verify(webClient).post();
    }

    @Test
    void updateUserRoles_updatesAndReturnsUser() {
        // Given
        String userId = "user-1";
        Set<String> newRoles = Set.of("ADMIN", "VIEWER");
        UserDetail expectedUser = createUserDetail(userId, "john@example.com", "John", "Doe");
        expectedUser.setRoles(List.of("ADMIN", "VIEWER"));

        when(webClient.put()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/roles", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedUser)).when(responseSpec).bodyToMono(UserDetail.class);

        // When
        UserDetail result = userService.updateUserRoles(userId, newRoles);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRoles()).containsExactlyInAnyOrder("ADMIN", "VIEWER");
    }

    @Test
    void resetMfa_resetsMfaForUser() {
        // Given
        String userId = "user-1";
        String reason = "Lost authenticator device";
        boolean notifyUser = true;

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/users/{userId}/mfa/reset", userId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then
        userService.resetMfa(userId, reason, notifyUser);

        verify(webClient).post();
    }

    private UserDetail createUserDetail(String userId, String email, String firstName, String lastName) {
        UserDetail user = new UserDetail();
        user.setUserId(userId);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        return user;
    }
}
