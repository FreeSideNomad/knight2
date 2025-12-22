package com.knight.portal.services.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RelatedPersonRequest {
    private String name;
    private String role;  // ADMIN or CONTACT
    private String email;
    private String phone;
}
