package com.knight.portal.services.dto;

import lombok.Data;

@Data
public class ClientAccount {
    private String accountId;
    private String clientId;
    private String currency;
    private String status;
    private String createdAt;
}
