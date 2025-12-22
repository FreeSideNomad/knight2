package com.knight.application.persistence.batch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for Batch aggregate.
 */
@Entity
@Table(name = "batches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchEntity {

    @Id
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "batch_type", nullable = false, length = 50)
    private String batchType;

    @Column(name = "source_profile_id", nullable = false, length = 200)
    private String sourceProfileId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sequenceNumber ASC")
    private List<BatchItemEntity> items = new ArrayList<>();

    public void addItem(BatchItemEntity item) {
        items.add(item);
        item.setBatch(this);
    }
}
