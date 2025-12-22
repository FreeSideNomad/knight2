package com.knight.domain.batch.types;

/**
 * Status of a batch operation.
 */
public enum BatchStatus {
    PENDING("Pending", "Validated, waiting to execute"),
    IN_PROGRESS("In Progress", "Currently processing items"),
    COMPLETED("Completed", "All items processed successfully"),
    COMPLETED_WITH_ERRORS("Completed with Errors", "Some items failed during processing"),
    FAILED("Failed", "Batch-level failure occurred")
    ;

    private final String displayName;
    private final String description;

    BatchStatus(String displayName, String description) {
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
