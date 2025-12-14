package com.knight.application.rest.clients;

import com.knight.application.rest.clients.dto.ClientAccountDto;
import com.knight.application.rest.clients.dto.ClientDetailDto;
import com.knight.application.rest.clients.dto.ClientSearchResponseDto;
import com.knight.application.rest.clients.dto.PageResultDto;
import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.api.PageResult;
import com.knight.domain.clients.api.queries.ClientAccountResponse;
import com.knight.domain.clients.api.queries.ClientDetailResponse;
import com.knight.domain.clients.api.queries.ClientSearchResult;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.platform.sharedkernel.ClientId;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for client-related operations.
 * Provides endpoints for searching clients, retrieving client details,
 * and accessing client accounts.
 */
@RestController
@RequestMapping("/api/clients")
@Validated
public class ClientController {

    private final ClientRepository clientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final ClientRestMapper mapper;

    public ClientController(
            ClientRepository clientRepository,
            ClientAccountRepository clientAccountRepository,
            ClientRestMapper mapper) {
        this.clientRepository = clientRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.mapper = mapper;
    }

    /**
     * Searches for clients based on optional type, name, and clientId filters with pagination.
     * If no filters are provided, returns empty result.
     *
     * @param type optional client system type filter (e.g., "srf", "cdr"). Filters by client ID prefix.
     * @param name optional name search pattern (case-insensitive partial match)
     * @param clientId optional client ID/number search pattern (case-insensitive partial match)
     * @param page page number (0-based, default 0)
     * @param size page size (default 20)
     * @return paginated list of matching clients
     */
    @GetMapping
    public ResponseEntity<PageResultDto<ClientSearchResponseDto>> searchClients(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Normalize type - represents client system prefix (srf, cdr), not domain ClientType
        String normalizedType = (type != null && !type.isBlank()) ? type.toLowerCase() : null;

        // Search by client ID if provided (with pagination)
        if (clientId != null && !clientId.isBlank()) {
            // If type is provided, ensure the clientId search includes the prefix
            String searchClientId = clientId;
            if (normalizedType != null && !clientId.toLowerCase().startsWith(normalizedType + ":")) {
                // Prepend type prefix if not already present
                searchClientId = normalizedType + ":" + clientId;
            }
            PageResult<Client> pageResult = clientRepository.searchByClientId(searchClientId, page, size);
            return ResponseEntity.ok(toPageResultDto(pageResult));
        }
        // Search by name if provided (with pagination)
        else if (name != null && !name.isBlank()) {
            // If type filter is provided, use combined search at database level
            if (normalizedType != null) {
                String prefix = normalizedType + ":";
                PageResult<Client> pageResult = clientRepository.searchByClientIdPrefixAndName(prefix, name, page, size);
                return ResponseEntity.ok(toPageResultDto(pageResult));
            }
            // No type filter - search by name only
            PageResult<Client> pageResult = clientRepository.searchByName(name, page, size);
            return ResponseEntity.ok(toPageResultDto(pageResult));
        }
        // Search by type only if provided - search by client ID prefix
        else if (normalizedType != null) {
            String prefix = normalizedType + ":";
            PageResult<Client> pageResult = clientRepository.searchByClientId(prefix, page, size);
            return ResponseEntity.ok(toPageResultDto(pageResult));
        } else {
            // If no filters, return empty result
            return ResponseEntity.ok(PageResultDto.of(List.of(), 0, size, 0));
        }
    }

    private PageResultDto<ClientSearchResponseDto> toPageResultDto(PageResult<Client> pageResult) {
        List<ClientSearchResponseDto> content = pageResult.content().stream()
                .map(this::toSearchResult)
                .map(mapper::toSearchResponseDto)
                .toList();
        return PageResultDto.of(content, pageResult.page(), pageResult.size(), pageResult.totalElements());
    }

    /**
     * Retrieves detailed information about a specific client.
     *
     * @param clientId the client identifier (URN format)
     * @return client details if found, 404 if not found
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<ClientDetailDto> getClient(
            @PathVariable @NotBlank(message = "Client ID is required") String clientId) {

        try {
            ClientId id = ClientId.of(clientId);
            Optional<Client> clientOpt = clientRepository.findById(id);

            if (clientOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Client client = clientOpt.get();
            ClientDetailResponse detailResponse = toDetailResponse(client);
            ClientDetailDto dto = mapper.toDetailDto(detailResponse);

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            // Invalid client ID format
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Retrieves accounts belonging to a specific client with pagination.
     *
     * @param clientId the client identifier (URN format)
     * @param page page number (0-based, default 0)
     * @param size page size (default 20)
     * @return paginated list of client accounts, 404 if client doesn't exist
     */
    @GetMapping("/{clientId}/accounts")
    public ResponseEntity<PageResultDto<ClientAccountDto>> getClientAccounts(
            @PathVariable @NotBlank(message = "Client ID is required") String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            ClientId id = ClientId.of(clientId);

            // Verify client exists
            if (!clientRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            PageResult<ClientAccount> pageResult = clientAccountRepository.findByClientId(id, page, size);

            List<ClientAccountDto> accountDtos = pageResult.content().stream()
                    .map(this::toAccountResponse)
                    .map(mapper::toAccountDto)
                    .toList();

            return ResponseEntity.ok(PageResultDto.of(accountDtos, page, size, pageResult.totalElements()));

        } catch (IllegalArgumentException e) {
            // Invalid client ID format
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods to convert domain aggregates to query responses

    private ClientSearchResult toSearchResult(Client client) {
        return new ClientSearchResult(
                client.clientId(),
                client.name(),
                getClientTypeString(client)
        );
    }

    private ClientDetailResponse toDetailResponse(Client client) {
        return new ClientDetailResponse(
                client.clientId(),
                client.name(),
                getClientTypeString(client),
                client.address(),
                client.createdAt(),
                client.updatedAt()
        );
    }

    private ClientAccountResponse toAccountResponse(ClientAccount account) {
        return new ClientAccountResponse(
                account.accountId(),
                account.clientId(),
                account.currency().code(),
                account.status(),
                account.createdAt()
        );
    }

    private String getClientTypeString(Client client) {
        // Determine if it's an SRF or CDR client based on the ClientId type
        String urn = client.clientId().urn();
        if (urn.startsWith("srf:")) {
            return "srf";
        } else if (urn.startsWith("cdr:")) {
            return "cdr";
        } else {
            return "indirect";
        }
    }
}
