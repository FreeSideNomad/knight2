package com.knight.indirectportal.services;

import com.knight.indirectportal.services.dto.AddOfiAccountRequest;
import com.knight.indirectportal.services.dto.IndirectClientDetail;
import com.knight.indirectportal.services.dto.OfiAccountDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IndirectClientServiceTest {

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

    private IndirectClientService indirectClientService;

    @BeforeEach
    void setUp() {
        indirectClientService = new IndirectClientService(webClient);
    }

    @Test
    void getMyClientDetails_returnsClientDetail() {
        // Given
        IndirectClientDetail expectedDetail = createClientDetail("client-1", "Test Payor");

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/me");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedDetail)).when(responseSpec).bodyToMono(IndirectClientDetail.class);

        // When
        IndirectClientDetail result = indirectClientService.getMyClientDetails();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("client-1");
        assertThat(result.getBusinessName()).isEqualTo("Test Payor");
    }

    @Test
    void getMyClientDetails_returnsNullOnError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/me");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Connection error")))
            .when(responseSpec).bodyToMono(IndirectClientDetail.class);

        // When
        IndirectClientDetail result = indirectClientService.getMyClientDetails();

        // Then
        assertThat(result).isNull();
    }

    @Test
    void getMyAccounts_returnsListOfAccounts() {
        // Given
        List<OfiAccountDto> expectedAccounts = List.of(
            createOfiAccount("acc-1", "12345678", "Checking Account"),
            createOfiAccount("acc-2", "87654321", "Savings Account")
        );

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/accounts");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.just(expectedAccounts)).when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<OfiAccountDto> result = indirectClientService.getMyAccounts();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAccountId()).isEqualTo("acc-1");
        assertThat(result.get(1).getAccountId()).isEqualTo("acc-2");
    }

    @Test
    void getMyAccounts_returnsEmptyListOnError() {
        // Given
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/accounts");
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.error(new RuntimeException("Connection error")))
            .when(responseSpec).bodyToMono(any(ParameterizedTypeReference.class));

        // When
        List<OfiAccountDto> result = indirectClientService.getMyAccounts();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void addOfiAccount_addsAccount() {
        // Given
        AddOfiAccountRequest request = new AddOfiAccountRequest("001", "12345", "12345678", "John Doe");

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        doReturn(requestBodySpec).when(requestBodyUriSpec).uri("/accounts");
        doReturn(requestHeadersSpec).when(requestBodySpec).bodyValue(request);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.empty()).when(responseSpec).bodyToMono(Void.class);

        // When/Then - no exception means success
        indirectClientService.addOfiAccount(request);

        verify(webClient).post();
    }

    @Test
    void removeOfiAccount_removesAccount() {
        // Given
        String accountId = "acc-1";

        when(webClient.delete()).thenReturn(requestHeadersUriSpec);
        doReturn(requestHeadersSpec).when(requestHeadersUriSpec).uri("/accounts/{accountId}", accountId);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        doReturn(Mono.empty()).when(responseSpec).bodyToMono(Void.class);

        // When/Then - no exception means success
        indirectClientService.removeOfiAccount(accountId);

        verify(webClient).delete();
    }

    private IndirectClientDetail createClientDetail(String id, String businessName) {
        IndirectClientDetail detail = new IndirectClientDetail();
        detail.setId(id);
        detail.setBusinessName(businessName);
        detail.setStatus("ACTIVE");
        return detail;
    }

    private OfiAccountDto createOfiAccount(String accountId, String accountNumber, String holderName) {
        OfiAccountDto account = new OfiAccountDto();
        account.setAccountId(accountId);
        account.setFormattedAccountId(accountNumber);
        account.setAccountHolderName(holderName);
        account.setStatus("ACTIVE");
        return account;
    }
}
