package com.knight.portal.services.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddOfiAccountRequest {
    private String bankCode;
    private String transitNumber;
    private String accountNumber;
    private String accountHolderName;
}
