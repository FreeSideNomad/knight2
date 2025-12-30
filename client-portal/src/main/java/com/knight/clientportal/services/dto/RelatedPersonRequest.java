package com.knight.clientportal.services.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RelatedPersonRequest {
    private String name;
    private String role;
    private String email;
    private String phone;
}
