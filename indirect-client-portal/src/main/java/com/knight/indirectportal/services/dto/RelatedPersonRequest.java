package com.knight.indirectportal.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelatedPersonRequest {
    private String name;
    private String role;
    private String email;
    private String phone;
}
