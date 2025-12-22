package com.knight.application.rest.indirectclients;

import com.knight.application.persistence.indirectclients.repository.IndirectClientJpaRepository;
import com.knight.application.rest.indirectclients.dto.*;
import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.repository.ClientAccountRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.AccountSystem;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.Currency;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.OfiAccountType;
import com.knight.platform.sharedkernel.ProfileId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for indirect client operations.
 * Provides endpoints for managing indirect clients and their related persons.
 */
@RestController
@RequestMapping("/api/indirect-clients")
@RequiredArgsConstructor
@Validated
public class IndirectClientController {

    private final IndirectClientRepository indirectClientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final IndirectClientJpaRepository indirectClientJpaRepository;

    /**
     * Retrieves all indirect clients for a given parent client.
     *
     * @param clientId the parent client identifier
     * @return list of indirect clients
     */
    @GetMapping("/by-client/{clientId}")
    public List<IndirectClientDto> getByClient(
            @PathVariable @NotBlank(message = "Client ID is required") String clientId) {
        ClientId id = ClientId.of(clientId);
        return indirectClientRepository.findByParentClientId(id)
            .stream()
            .map(this::toDto)
            .toList();
    }

    /**
     * Retrieves all indirect clients for a given profile.
     *
     * @param profileId the profile identifier (URL encoded)
     * @return list of indirect clients
     */
    @GetMapping("/by-profile")
    public List<IndirectClientDto> getByProfile(
            @RequestParam @NotBlank(message = "Profile ID is required") String profileId) {
        ProfileId id = ProfileId.fromUrn(profileId);
        return indirectClientRepository.findByProfileId(id)
            .stream()
            .map(this::toDto)
            .toList();
    }

    /**
     * Retrieves detailed information about a specific indirect client.
     *
     * @param id the indirect client identifier
     * @return client details if found, 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<IndirectClientDetailDto> getById(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);
        return indirectClientRepository.findById(indirectClientId)
            .map(this::toDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new indirect client with optional related persons.
     *
     * @param request the creation request containing client details
     * @return response with the new client ID
     */
    @PostMapping
    public ResponseEntity<CreateIndirectClientResponse> create(@Valid @RequestBody CreateIndirectClientRequest request) {
        ClientId parentClientId = ClientId.of(request.parentClientId());
        ProfileId profileId = ProfileId.fromUrn(request.profileId());

        // Generate sequential ID for this parent client
        int nextSequence = indirectClientRepository.getNextSequenceForClient(parentClientId);
        IndirectClientId id = IndirectClientId.of(parentClientId, nextSequence);

        IndirectClient client = IndirectClient.create(
            id,
            parentClientId,
            profileId,
            request.businessName(),
            "api-user"  // TODO: Get from security context
        );

        // Add related persons if provided
        if (request.relatedPersons() != null) {
            for (RelatedPersonRequest personReq : request.relatedPersons()) {
                Email email = personReq.email() != null && !personReq.email().isBlank()
                    ? Email.of(personReq.email()) : null;
                Phone phone = personReq.phone() != null && !personReq.phone().isBlank()
                    ? Phone.of(personReq.phone()) : null;
                PersonRole role = PersonRole.valueOf(personReq.role());
                client.addRelatedPerson(personReq.name(), role, email, phone);
            }
        }

        indirectClientRepository.save(client);

        return ResponseEntity
            .created(URI.create("/api/indirect-clients/" + id.urn()))
            .body(new CreateIndirectClientResponse(id.urn()));
    }

    /**
     * Adds a related person to an existing indirect client.
     *
     * @param id the indirect client identifier
     * @param request the related person details
     * @return 200 if successful, 404 if client not found
     */
    @PostMapping("/{id}/persons")
    public ResponseEntity<Void> addRelatedPerson(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id,
            @Valid @RequestBody RelatedPersonRequest request) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .map(client -> {
                Email email = request.email() != null && !request.email().isBlank()
                    ? Email.of(request.email()) : null;
                Phone phone = request.phone() != null && !request.phone().isBlank()
                    ? Phone.of(request.phone()) : null;
                PersonRole role = PersonRole.valueOf(request.role());

                client.addRelatedPerson(request.name(), role, email, phone);
                indirectClientRepository.save(client);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Updates an existing related person.
     *
     * @param id the indirect client identifier
     * @param personId the person identifier
     * @param request the updated person details
     * @return 200 if successful, 404 if client or person not found
     */
    @PutMapping("/{id}/persons/{personId}")
    public ResponseEntity<Void> updateRelatedPerson(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id,
            @PathVariable @NotBlank(message = "Person ID is required") String personId,
            @Valid @RequestBody RelatedPersonRequest request) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .map(client -> {
                try {
                    Email email = request.email() != null && !request.email().isBlank()
                        ? Email.of(request.email()) : null;
                    Phone phone = request.phone() != null && !request.phone().isBlank()
                        ? Phone.of(request.phone()) : null;
                    PersonRole role = PersonRole.valueOf(request.role());

                    client.updateRelatedPerson(
                        com.knight.domain.indirectclients.types.PersonId.of(personId),
                        request.name(), role, email, phone);
                    indirectClientRepository.save(client);
                    return ResponseEntity.ok().<Void>build();
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().<Void>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Removes a related person from an indirect client.
     *
     * @param id the indirect client identifier
     * @param personId the person identifier
     * @return 200 if successful, 404 if client or person not found, 400 if business rules violated
     */
    @DeleteMapping("/{id}/persons/{personId}")
    public ResponseEntity<Void> removeRelatedPerson(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id,
            @PathVariable @NotBlank(message = "Person ID is required") String personId) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        return indirectClientRepository.findById(indirectClientId)
            .map(client -> {
                try {
                    client.removeRelatedPerson(
                        com.knight.domain.indirectclients.types.PersonId.of(personId));
                    indirectClientRepository.save(client);
                    return ResponseEntity.ok().<Void>build();
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.notFound().<Void>build();
                } catch (IllegalStateException e) {
                    return ResponseEntity.badRequest().<Void>build();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Adds an OFI account to an indirect client.
     *
     * @param id the indirect client identifier
     * @param request the account details
     * @return 201 with account details if successful, 404 if client not found
     */
    @PostMapping("/{id}/accounts")
    public ResponseEntity<OfiAccountDto> addOfiAccount(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id,
            @Valid @RequestBody AddOfiAccountRequest request) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        // Get the indirect client to verify it exists
        return indirectClientRepository.findById(indirectClientId)
            .flatMap(client -> {
                // Look up the database UUID for this indirect client
                return indirectClientJpaRepository.findByIndirectClientUrn(id)
                    .map(entity -> {
                        try {
                            // Create OFI account ID in format: OFI:CAN:bank(3):transit(5):accountNumber(12)
                            String paddedAccountNumber = String.format("%012d", Long.parseLong(request.accountNumber()));
                            String segments = request.bankCode() + ":" + request.transitNumber() + ":" + paddedAccountNumber;
                            ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, OfiAccountType.CAN.name(), segments);

                            // Create and save the OFI account
                            ClientAccount ofiAccount = ClientAccount.createOfiAccount(
                                accountId,
                                entity.getId(),
                                Currency.CAD,
                                request.accountHolderName()
                            );
                            clientAccountRepository.save(ofiAccount);

                            OfiAccountDto dto = toOfiAccountDto(ofiAccount);
                            return ResponseEntity
                                .created(URI.create("/api/indirect-clients/" + id + "/accounts/" + accountId.urn()))
                                .body(dto);
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            return ResponseEntity.badRequest().<OfiAccountDto>build();
                        }
                    });
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves all OFI accounts for an indirect client.
     *
     * @param id the indirect client identifier
     * @return list of accounts if successful, 404 if client not found
     */
    @GetMapping("/{id}/accounts")
    public ResponseEntity<List<OfiAccountDto>> getOfiAccounts(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        // Verify indirect client exists and get its database UUID
        return indirectClientRepository.findById(indirectClientId)
            .flatMap(client ->
                indirectClientJpaRepository.findByIndirectClientUrn(id)
                    .map(entity -> {
                        List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(entity.getId())
                            .stream()
                            .map(this::toOfiAccountDto)
                            .toList();
                        return ResponseEntity.ok(accounts);
                    })
            )
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Deactivates (closes) an OFI account.
     *
     * @param id the indirect client identifier
     * @param accountId the account identifier (URL encoded URN)
     * @return 200 if successful, 404 if client or account not found
     */
    @DeleteMapping("/{id}/accounts/{accountId}")
    public ResponseEntity<Void> deactivateOfiAccount(
            @PathVariable @NotBlank(message = "Indirect client ID is required") String id,
            @PathVariable @NotBlank(message = "Account ID is required") String accountId) {
        IndirectClientId indirectClientId = IndirectClientId.fromUrn(id);

        // Verify indirect client exists
        return indirectClientRepository.findById(indirectClientId)
            .flatMap(client -> {
                try {
                    ClientAccountId clientAccountId = ClientAccountId.of(accountId);
                    return clientAccountRepository.findById(clientAccountId)
                        .map(account -> {
                            account.close();
                            clientAccountRepository.save(account);
                            return ResponseEntity.ok().<Void>build();
                        });
                } catch (IllegalArgumentException e) {
                    return java.util.Optional.<ResponseEntity<Void>>empty();
                }
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // Helper methods to convert domain aggregates to DTOs

    private IndirectClientDto toDto(IndirectClient client) {
        return new IndirectClientDto(
            client.id().urn(),
            client.parentClientId().urn(),
            client.clientType().name(),
            client.businessName(),
            client.status().name(),
            client.relatedPersons().size(),
            client.createdAt()
        );
    }

    private IndirectClientDetailDto toDetailDto(IndirectClient client) {
        List<RelatedPersonDto> persons = client.relatedPersons().stream()
            .map(p -> new RelatedPersonDto(
                p.personId().value().toString(),
                p.name(),
                p.role().name(),
                p.email() != null ? p.email().value() : null,
                p.phone() != null ? p.phone().value() : null,
                p.addedAt()
            ))
            .toList();

        // Look up OFI accounts from ClientAccountRepository
        List<OfiAccountDto> accounts = indirectClientJpaRepository.findByIndirectClientUrn(client.id().urn())
            .map(entity -> clientAccountRepository.findByIndirectClientId(entity.getId())
                .stream()
                .map(this::toOfiAccountDto)
                .toList())
            .orElse(List.of());

        return new IndirectClientDetailDto(
            client.id().urn(),
            client.parentClientId().urn(),
            client.profileId().urn(),
            client.clientType().name(),
            client.businessName(),
            client.status().name(),
            persons,
            accounts,
            client.createdAt(),
            client.updatedAt()
        );
    }

    private OfiAccountDto toOfiAccountDto(ClientAccount account) {
        // Parse OFI account segments: bank(3):transit(5):accountNumber(12)
        String segments = account.accountId().accountNumberSegments();
        String[] parts = segments.split(":");
        String bankCode = parts.length > 0 ? parts[0] : "";
        String transitNumber = parts.length > 1 ? parts[1] : "";
        String accountNumber = parts.length > 2 ? parts[2] : "";

        // Format as readable account number
        String formattedAccountId = String.format("%s-%s-%s", bankCode, transitNumber, accountNumber);

        return new OfiAccountDto(
            account.accountId().urn(),
            bankCode,
            transitNumber,
            accountNumber,
            account.accountHolderName(),
            account.status().name(),
            formattedAccountId,
            account.createdAt()
        );
    }
}
