package com.knight.domain.batch.api.commands;

import com.knight.platform.sharedkernel.BatchId;

/**
 * Commands for batch operations.
 */
public interface BatchCommands {

    /**
     * Start execution of a pending batch.
     */
    void startBatch(StartBatchCmd cmd);

    record StartBatchCmd(BatchId batchId) {}
}
