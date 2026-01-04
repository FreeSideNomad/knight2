package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.CreateUserGroupRequest;
import com.knight.indirectportal.services.dto.UserGroupDetail;
import com.knight.indirectportal.services.dto.UserGroupDetail.UserGroupMember;
import com.knight.indirectportal.services.dto.UserGroupSummary;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserGroupServiceTest {

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

    private UserGroupService userGroupService;

    @BeforeEach
    void setUp() {
        userGroupService = new UserGroupService(webClient);
    }

    @Test
    void getGroups_returnsListOfGroups() {
        // Given
        List<UserGroupSummary> expectedGroups = List.of(
            createGroupSummary("group-1", "Admins", 5),
            createGroupSummary("group-2", "Viewers", 10)
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/groups");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroups)).when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<UserGroupSummary> result = userGroupService.getGroups();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Admins");
        assertThat(result.get(1).getName()).isEqualTo("Viewers");
    }

    @Test
    void getGroups_returnsEmptyListOnError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/groups");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Connection error")))
            .when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<UserGroupSummary> result = userGroupService.getGroups();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getGroupById_returnsGroupDetail() {
        // Given
        String groupId = "group-1";
        UserGroupDetail expectedDetail = createGroupDetail(groupId, "Admins", Set.of("user-1", "user-2"));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(UserGroupDetail.class);

        // When
        UserGroupDetail result = userGroupService.getGroupById(groupId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getName()).isEqualTo("Admins");
        assertThat(result.getMembers()).hasSize(2);
    }

    @Test
    void getGroupById_returnsNullOnError() {
        // Given
        String groupId = "non-existent";

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Not found")))
            .when(responseSpec).bodyToMono(UserGroupDetail.class);

        // When
        UserGroupDetail result = userGroupService.getGroupById(groupId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void createGroup_createsAndReturnsGroup() {
        // Given
        CreateUserGroupRequest request = new CreateUserGroupRequest("New Group", "A new group");
        UserGroupSummary expectedGroup = createGroupSummary("group-new", "New Group", 0);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/groups");
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(request);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroup)).when(responseSpec).bodyToMono(UserGroupSummary.class);

        // When
        UserGroupSummary result = userGroupService.createGroup(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("New Group");
    }

    @Test
    void updateGroup_updatesAndReturnsGroup() {
        // Given
        String groupId = "group-1";
        String newName = "Updated Name";
        String newDescription = "Updated description";
        UserGroupSummary expectedGroup = createGroupSummary(groupId, newName, 5);

        when(webClient.put()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/groups/{groupId}", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedGroup)).when(responseSpec).bodyToMono(UserGroupSummary.class);

        // When
        UserGroupSummary result = userGroupService.updateGroup(groupId, newName, newDescription);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(newName);
    }

    @Test
    void deleteGroup_deletesGroup() {
        // Given
        String groupId = "group-1";

        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/groups/{groupId}", groupId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(ResponseEntity.ok().build())).when(responseSpec).toBodilessEntity();

        // When/Then - no exception means success
        userGroupService.deleteGroup(groupId);

        verify(webClient).delete();
    }

    @Test
    void addMembers_addsAndReturnsUpdatedGroup() {
        // Given
        String groupId = "group-1";
        Set<String> userIds = Set.of("user-3", "user-4");
        UserGroupDetail expectedDetail = createGroupDetail(groupId, "Admins",
            Set.of("user-1", "user-2", "user-3", "user-4"));

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/groups/{groupId}/members", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(UserGroupDetail.class);

        // When
        UserGroupDetail result = userGroupService.addMembers(groupId, userIds);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMembers()).hasSize(4);
    }

    @Test
    void removeMembers_removesAndReturnsUpdatedGroup() {
        // Given
        String groupId = "group-1";
        Set<String> userIds = Set.of("user-2");
        UserGroupDetail expectedDetail = createGroupDetail(groupId, "Admins", Set.of("user-1"));

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/groups/{groupId}/members", groupId);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(any());
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(UserGroupDetail.class);

        // When
        UserGroupDetail result = userGroupService.removeMembers(groupId, userIds);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMembers()).hasSize(1);
    }

    private UserGroupSummary createGroupSummary(String groupId, String name, int memberCount) {
        UserGroupSummary summary = new UserGroupSummary();
        summary.setGroupId(groupId);
        summary.setName(name);
        summary.setMemberCount(memberCount);
        summary.setCreatedAt(Instant.now());
        return summary;
    }

    private UserGroupDetail createGroupDetail(String groupId, String name, Set<String> memberIds) {
        UserGroupDetail detail = new UserGroupDetail();
        detail.setGroupId(groupId);
        detail.setName(name);
        Set<UserGroupMember> members = memberIds.stream()
            .map(this::createMember)
            .collect(Collectors.toSet());
        detail.setMembers(members);
        detail.setCreatedAt(Instant.now());
        return detail;
    }

    private UserGroupMember createMember(String userId) {
        UserGroupMember member = new UserGroupMember();
        member.setUserId(userId);
        member.setAddedAt(Instant.now());
        return member;
    }
}
