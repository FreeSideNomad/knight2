package com.knight.application.persistence.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for BatchItem within a Batch.
 */
@Entity
@Table(name = "batch_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchItemEntity {

    @Id
    @Column(name = "batch_item_id", nullable = false)
    private UUID batchItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private BatchEntity batch;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "input_data", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String inputData;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "result_data", columnDefinition = "NVARCHAR(MAX)")
    private String resultData;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "processed_at")
    private Instant processedAt;
}
