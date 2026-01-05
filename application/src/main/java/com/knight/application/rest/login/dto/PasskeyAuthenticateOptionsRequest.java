package com.knight.application.rest.login.dto;

public record PasskeyAuthenticateOptionsRequest(
    String loginId  // Optional - can be null for discoverable credentials
) {}
