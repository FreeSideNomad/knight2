package com.knight.application.rest.indirect;

import com.knight.application.rest.users.dto.UserDetailDto;

/**
 * User detail with optional Auth0 raw JSON data.
 */
public record UserDetailWithAuth0Dto(
    UserDetailDto user,
    String auth0RawJson
) {}
