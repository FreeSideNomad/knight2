package com.knight.domain.batch.types;

/**
 * Status of an individual batch item.
 */
public enum BatchItemStatus {
    PENDING("Pending", "Not yet processed"),
    IN_PROGRESS("In Progress", "Currently being processed"),
    SUCCESS("Success", "Processed successfully"),
    FAILED("Failed", "Processing failed")
    ;

    private final String displayName;
    private final String description;

    BatchItemStatus(String displayName, String description) {
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
