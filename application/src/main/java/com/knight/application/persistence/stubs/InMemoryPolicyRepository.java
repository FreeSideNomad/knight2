package com.knight.application.persistence.stubs;

import com.knight.domain.policy.aggregate.Policy;
import com.knight.domain.policy.repository.PolicyRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of PolicyRepository for development/testing.
 */
@Repository
public class InMemoryPolicyRepository implements PolicyRepository {

    private final Map<String, Policy> store = new ConcurrentHashMap<>();

    @Override
    public void save(Policy policy) {
        store.put(policy.id(), policy);
    }

    @Override
    public Optional<Policy> findById(String policyId) {
        return Optional.ofNullable(store.get(policyId));
    }

    @Override
    public void deleteById(String policyId) {
        store.remove(policyId);
    }
}
