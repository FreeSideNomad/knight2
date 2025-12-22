-- =====================================================
-- BATCH PROCESSING TABLES
-- Supports async batch operations like payor enrolment
-- =====================================================

-- Batches table
CREATE TABLE batches (
    batch_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_type VARCHAR(50) NOT NULL,              -- PAYOR_ENROLMENT, etc.
    source_profile_id VARCHAR(200) NOT NULL,      -- Profile that initiated the batch
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_items INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    started_at DATETIME2,
    completed_at DATETIME2,

    CONSTRAINT CHK_batch_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'
    )),
    CONSTRAINT CHK_batch_type CHECK (batch_type IN (
        'PAYOR_ENROLMENT'
    )),
    CONSTRAINT FK_batches_profile FOREIGN KEY (source_profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_batches_profile ON batches(source_profile_id);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_created ON batches(created_at DESC);

-- Batch Items table
CREATE TABLE batch_items (
    batch_item_id UNIQUEIDENTIFIER PRIMARY KEY,
    batch_id UNIQUEIDENTIFIER NOT NULL,
    sequence_number INT NOT NULL,
    input_data NVARCHAR(MAX) NOT NULL,            -- JSON input for this item
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    result_data NVARCHAR(MAX),                    -- JSON result (entity IDs on success)
    error_message NVARCHAR(2000),                 -- Error details on failure
    processed_at DATETIME2,

    CONSTRAINT CHK_batch_item_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    )),
    CONSTRAINT FK_batch_items_batch FOREIGN KEY (batch_id)
        REFERENCES batches(batch_id) ON DELETE CASCADE,
    CONSTRAINT UQ_batch_item_sequence UNIQUE (batch_id, sequence_number)
);

CREATE INDEX idx_batch_items_batch ON batch_items(batch_id);
CREATE INDEX idx_batch_items_status ON batch_items(status);
