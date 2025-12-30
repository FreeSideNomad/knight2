package com.knight.domain.indirectclients.api.commands;

import com.knight.domain.indirectclients.types.PersonRole;
import com.knight.domain.indirectclients.types.Email;
import com.knight.domain.indirectclients.types.Phone;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;

/**
 * Command interface for Indirect Client Management.
 * Defines contract for creating and managing indirect clients (payors).
 */
public interface IndirectClientCommands {

    IndirectClientId createIndirectClient(CreateIndirectClientCmd cmd);

    record CreateIndirectClientCmd(
        ClientId parentClientId,
        ProfileId parentProfileId,
        String name,
        List<RelatedPersonData> relatedPersons
    ) {}

    record RelatedPersonData(
        String name,
        PersonRole role,
        Email email,
        Phone phone
    ) {}

    void addRelatedPerson(AddRelatedPersonCmd cmd);

    record AddRelatedPersonCmd(
        IndirectClientId indirectClientId,
        String name,
        PersonRole role,
        Email email,
        Phone phone
    ) {}
}
