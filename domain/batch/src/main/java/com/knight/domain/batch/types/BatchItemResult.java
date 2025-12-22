package com.knight.domain.batch.types;

import java.util.List;

/**
 * Result data for a successfully processed batch item.
 */
public record BatchItemResult(
        String indirectClientId,
        String profileId,
        List<String> userIds
) {}
