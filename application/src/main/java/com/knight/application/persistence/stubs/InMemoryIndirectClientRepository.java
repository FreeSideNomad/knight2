package com.knight.application.persistence.stubs;

import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of IndirectClientRepository for development/testing.
 */
@Repository
public class InMemoryIndirectClientRepository implements IndirectClientRepository {

    private final Map<String, IndirectClient> store = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sequences = new ConcurrentHashMap<>();

    @Override
    public void save(IndirectClient client) {
        store.put(client.id().urn(), client);
    }

    @Override
    public Optional<IndirectClient> findById(IndirectClientId id) {
        return Optional.ofNullable(store.get(id.urn()));
    }

    @Override
    public int getNextSequenceForClient(ClientId parentClientId) {
        return sequences.computeIfAbsent(parentClientId.urn(), k -> new AtomicInteger(0))
                .incrementAndGet();
    }
}
