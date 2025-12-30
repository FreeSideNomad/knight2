package com.knight.indirectportal.services.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelatedPersonDto {
    private String personId;
    private String name;
    private String role;
    private String email;
    private String phone;
    private Instant addedAt;
}
