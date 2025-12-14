package com.knight.portal.services.dto;

import lombok.Data;

@Data
public class ClientDetail {
    private String clientId;
    private String name;
    private String type;
    private Address address;
}
