package com.knight.application.service;

import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.repository.ClientRepository;
import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.domain.indirectclients.repository.IndirectClientRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ClientNameResolver;
import com.knight.platform.sharedkernel.IndirectClientId;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Implementation of ClientNameResolver that handles both regular and indirect clients.
 */
@Service
public class ClientNameResolverImpl implements ClientNameResolver {

    private final ClientRepository clientRepository;
    private final IndirectClientRepository indirectClientRepository;

    public ClientNameResolverImpl(
            ClientRepository clientRepository,
            IndirectClientRepository indirectClientRepository) {
        this.clientRepository = clientRepository;
        this.indirectClientRepository = indirectClientRepository;
    }

    @Override
    public Optional<String> resolveName(ClientId clientId) {
        String urn = clientId.urn();
        if (urn.startsWith("ind:")) {
            // This is an indirect client - look up from IndirectClientRepository
            IndirectClientId indirectClientId = IndirectClientId.fromUrn(urn);
            return indirectClientRepository.findById(indirectClientId)
                .map(IndirectClient::name);
        } else {
            // Regular client - look up from ClientRepository
            return clientRepository.findById(clientId)
                .map(Client::name);
        }
    }
}
