package com.knight.domain.batch.types;

/**
 * Types of batch operations supported.
 */
public enum BatchType {
    PAYOR_ENROLMENT("Payor Enrolment", "Bulk import of payors from JSON file")
    ;

    private final String displayName;
    private final String description;

    BatchType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
