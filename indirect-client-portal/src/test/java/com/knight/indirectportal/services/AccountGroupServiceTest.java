package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.AccountGroupDetail;
import com.knight.indirectportal.services.dto.AccountGroupSummary;
import com.knight.indirectportal.services.dto.CreateAccountGroupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
class AccountGroupServiceTest {

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

    private AccountGroupService accountGroupService;

    @BeforeEach
    void setUp() {
        accountGroupService = new AccountGroupService(webClient);
    }

    @Test
    void getGroups_returnsListOfGroups() {
        // Given
        List<AccountGroupSummary> expectedGroups = List.of(
            createGroupSummary("group-1", "Primary Accounts", 3),
            createGroupSummary("group-2", "Secondary Accounts", 5)
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/account-groups");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroups)).when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<AccountGroupSummary> result = accountGroupService.getGroups();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Primary Accounts");
        assertThat(result.get(1).getName()).isEqualTo("Secondary Accounts");
    }

    @Test
    void getGroups_returnsEmptyListOnError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/account-groups");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Connection error")))
            .when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<AccountGroupSummary> result = accountGroupService.getGroups();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getGroupById_returnsGroupDetail() {
        // Given
        String groupId = "group-1";
        AccountGroupDetail expectedDetail = createGroupDetail(groupId, "Primary Accounts", Set.of("acc-1", "acc-2"));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/account-groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(AccountGroupDetail.class);

        // When
        AccountGroupDetail result = accountGroupService.getGroupById(groupId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getName()).isEqualTo("Primary Accounts");
        assertThat(result.getAccountIds()).containsExactlyInAnyOrder("acc-1", "acc-2");
    }

    @Test
    void getGroupById_returnsNullOnError() {
        // Given
        String groupId = "non-existent";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/account-groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Not found")))
            .when(responseSpec).bodyToMono(AccountGroupDetail.class);

        // When
        AccountGroupDetail result = accountGroupService.getGroupById(groupId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void createGroup_createsAndReturnsGroup() {
        // Given
        CreateAccountGroupRequest request = new CreateAccountGroupRequest("New Account Group", "For new accounts");
        AccountGroupSummary expectedGroup = createGroupSummary("group-new", "New Account Group", 0);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/account-groups");
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(request);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroup)).when(responseSpec).bodyToMono(AccountGroupSummary.class);

        // When
        AccountGroupSummary result = accountGroupService.createGroup(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Account Group");
    }

    @Test
    void updateGroup_updatesAndReturnsGroup() {
        // Given
        String groupId = "group-1";
        String newName = "Updated Name";
        String newDescription = "Updated description";
        AccountGroupSummary expectedGroup = createGroupSummary(groupId, newName, 3);

        when(webClient.put()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/account-groups/{groupId}", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroup)).when(responseSpec).bodyToMono(AccountGroupSummary.class);

        // When
        AccountGroupSummary result = accountGroupService.updateGroup(groupId, newName, newDescription);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(newName);
    }

    @Test
    void deleteGroup_deletesGroup() {
        // Given
        String groupId = "group-1";

        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/account-groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then - no exception means success
        accountGroupService.deleteGroup(groupId);

        verify(webClient).delete();
    }

    @Test
    void addAccounts_addsAndReturnsUpdatedGroup() {
        // Given
        String groupId = "group-1";
        Set<String> accountIds = Set.of("acc-3", "acc-4");
        AccountGroupDetail expectedDetail = createGroupDetail(groupId, "Primary Accounts",
            Set.of("acc-1", "acc-2", "acc-3", "acc-4"));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/account-groups/{groupId}/accounts", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(AccountGroupDetail.class);

        // When
        AccountGroupDetail result = accountGroupService.addAccounts(groupId, accountIds);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountIds()).hasSize(4);
    }

    @Test
    void removeAccounts_removesAndReturnsUpdatedGroup() {
        // Given
        String groupId = "group-1";
        Set<String> accountIds = Set.of("acc-2");
        AccountGroupDetail expectedDetail = createGroupDetail(groupId, "Primary Accounts", Set.of("acc-1"));

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/account-groups/{groupId}/accounts", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(AccountGroupDetail.class);

        // When
        AccountGroupDetail result = accountGroupService.removeAccounts(groupId, accountIds);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountIds()).containsExactly("acc-1");
    }

    private AccountGroupSummary createGroupSummary(String groupId, String name, int accountCount) {
        AccountGroupSummary summary = new AccountGroupSummary();
        summary.setGroupId(groupId);
        summary.setName(name);
        summary.setAccountCount(accountCount);
        summary.setCreatedAt(Instant.now());
        return summary;
    }

    private AccountGroupDetail createGroupDetail(String groupId, String name, Set<String> accountIds) {
        AccountGroupDetail detail = new AccountGroupDetail();
        detail.setGroupId(groupId);
        detail.setName(name);
        detail.setAccountIds(accountIds);
        detail.setCreatedAt(Instant.now());
        return detail;
    }
}
