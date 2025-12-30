package com.knight.indirectportal.services.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddOfiAccountRequest {
    private String bankCode;
    private String transitNumber;
    private String accountNumber;
    private String accountHolderName;
}
