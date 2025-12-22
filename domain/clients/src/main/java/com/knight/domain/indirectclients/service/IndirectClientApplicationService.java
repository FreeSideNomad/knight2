package com.knight.domain.indirectclients.service;

import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.api.commands.IndirectClientCommands;
import com.knight.domain.indirectclients.api.events.IndirectClientOnboarded;
import com.knight.domain.indirectclients.api.queries.IndirectClientQueries;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.platform.sharedkernel.IndirectClientId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service for Indirect Client Management.
 * Orchestrates indirect client operations with transactions and event publishing.
 */
@Service
public class IndirectClientApplicationService implements IndirectClientCommands, IndirectClientQueries {

    private final IndirectClientRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public IndirectClientApplicationService(
        IndirectClientRepository repository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public IndirectClientId createIndirectClient(CreateIndirectClientCmd cmd) {
        int nextSequence = repository.getNextSequenceForClient(cmd.parentClientId());
        IndirectClientId id = IndirectClientId.of(cmd.parentClientId(), nextSequence);

        IndirectClient client = IndirectClient.create(
            id,
            cmd.parentClientId(),
            cmd.profileId(),
            cmd.businessName(),
            "system" // createdBy - should come from security context
        );

        // Add related persons if provided
        if (cmd.relatedPersons() != null) {
            for (RelatedPersonData personData : cmd.relatedPersons()) {
                client.addRelatedPerson(
                    personData.name(),
                    personData.role(),
                    personData.email(),
                    personData.phone()
                );
            }
        }

        repository.save(client);

        eventPublisher.publishEvent(new IndirectClientOnboarded(
            id.urn(),
            cmd.parentClientId().urn(),
            cmd.businessName(),
            Instant.now()
        ));

        return id;
    }

    @Override
    @Transactional
    public void addRelatedPerson(AddRelatedPersonCmd cmd) {
        IndirectClient client = repository.findById(cmd.indirectClientId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Indirect client not found: " + cmd.indirectClientId().urn()));

        client.addRelatedPerson(cmd.name(), cmd.role(), cmd.email(), cmd.phone());

        repository.save(client);
    }

    @Override
    public IndirectClientSummary getIndirectClientSummary(IndirectClientId id) {
        IndirectClient client = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException(
                "Indirect client not found: " + id.urn()));

        return new IndirectClientSummary(
            client.id().urn(),
            client.businessName(),
            client.status().name(),
            client.relatedPersons().size()
        );
    }
}
