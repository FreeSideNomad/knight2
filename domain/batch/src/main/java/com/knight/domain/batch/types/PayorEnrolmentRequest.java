package com.knight.domain.batch.types;

import java.util.List;

/**
 * Request data for payor enrolment from JSON file.
 */
public record PayorEnrolmentRequest(
        String businessName,
        String externalReference,
        List<PersonRequest> persons
) {
    /**
     * Related person data within the request.
     */
    public record PersonRequest(
            String name,
            String email,
            String role,  // ADMIN or CONTACT
            String phone
    ) {}
}
