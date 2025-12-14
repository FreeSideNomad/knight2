package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.Policy;
import com.knight.domain.policy.api.commands.PolicyCommands;
import com.knight.domain.policy.api.events.PolicyCreated;
import com.knight.domain.policy.api.queries.PolicyQueries;
import com.knight.domain.policy.repository.PolicyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service for Policy Management.
 * Orchestrates policy operations with transactions and event publishing.
 */
@Service
public class PolicyApplicationService implements PolicyCommands, PolicyQueries {

    private final PolicyRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public PolicyApplicationService(
        PolicyRepository repository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public String createPolicy(CreatePolicyCmd cmd) {
        Policy.PolicyType policyType = Policy.PolicyType.valueOf(cmd.policyType());

        Policy policy = Policy.create(
            policyType,
            cmd.subject(),
            cmd.action(),
            cmd.resource(),
            cmd.approverCount()
        );

        repository.save(policy);

        eventPublisher.publishEvent(new PolicyCreated(
            policy.id(),
            cmd.policyType(),
            cmd.subject(),
            Instant.now()
        ));

        return policy.id();
    }

    @Override
    @Transactional
    public void updatePolicy(UpdatePolicyCmd cmd) {
        Policy policy = repository.findById(cmd.policyId())
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + cmd.policyId()));

        policy.update(cmd.action(), cmd.resource(), cmd.approverCount());

        repository.save(policy);
    }

    @Override
    @Transactional
    public void deletePolicy(DeletePolicyCmd cmd) {
        repository.deleteById(cmd.policyId());
    }

    @Override
    public PolicySummary getPolicySummary(String policyId) {
        Policy policy = repository.findById(policyId)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        return new PolicySummary(
            policy.id(),
            policy.policyType().name(),
            policy.subject(),
            policy.action(),
            policy.resource(),
            policy.approverCount()
        );
    }
}
