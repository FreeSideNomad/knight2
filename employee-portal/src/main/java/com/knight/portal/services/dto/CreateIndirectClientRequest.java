package com.knight.portal.services.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class CreateIndirectClientRequest {
    private String parentClientId;
    private String profileId;
    private String businessName;
    private List<RelatedPersonRequest> relatedPersons;
}
