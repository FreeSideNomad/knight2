package com.knight.application.rest.accountgroups.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record ModifyAccountsRequest(
    @NotEmpty Set<String> accountIds
) {}
