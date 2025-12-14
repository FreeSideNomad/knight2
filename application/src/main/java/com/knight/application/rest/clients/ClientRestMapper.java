package com.knight.application.rest.clients;

import com.knight.application.rest.clients.dto.AddressDto;
import com.knight.application.rest.clients.dto.ClientAccountDto;
import com.knight.application.rest.clients.dto.ClientDetailDto;
import com.knight.application.rest.clients.dto.ClientSearchResponseDto;
import com.knight.domain.clients.api.queries.ClientAccountResponse;
import com.knight.domain.clients.api.queries.ClientDetailResponse;
import com.knight.domain.clients.api.queries.ClientSearchResult;
import com.knight.platform.sharedkernel.Address;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between domain DTOs and REST DTOs.
 * Handles the translation layer between the domain model and REST API.
 */
@Component
public class ClientRestMapper {

    /**
     * Converts domain ClientSearchResult to REST DTO.
     *
     * @param result domain search result
     * @return REST search response DTO
     */
    public ClientSearchResponseDto toSearchResponseDto(ClientSearchResult result) {
        return new ClientSearchResponseDto(
            result.clientId().urn(),
            result.name(),
            result.clientType()
        );
    }

    /**
     * Converts domain ClientDetailResponse to REST DTO.
     *
     * @param response domain detail response
     * @return REST detail DTO
     */
    public ClientDetailDto toDetailDto(ClientDetailResponse response) {
        return new ClientDetailDto(
            response.clientId().urn(),
            response.name(),
            response.clientType(),
            toAddressDto(response.address()),
            response.createdAt(),
            response.updatedAt()
        );
    }

    /**
     * Converts domain Address to REST DTO.
     *
     * @param address domain address
     * @return REST address DTO
     */
    public AddressDto toAddressDto(Address address) {
        return new AddressDto(
            address.addressLine1(),
            address.addressLine2(),
            address.city(),
            address.stateProvince(),
            address.zipPostalCode(),
            address.countryCode()
        );
    }

    /**
     * Converts domain ClientAccountResponse to REST DTO.
     *
     * @param response domain account response
     * @return REST account DTO
     */
    public ClientAccountDto toAccountDto(ClientAccountResponse response) {
        return new ClientAccountDto(
            response.accountId().urn(),
            response.clientId().urn(),
            response.currency(),
            response.status().name(),
            response.createdAt()
        );
    }
}
