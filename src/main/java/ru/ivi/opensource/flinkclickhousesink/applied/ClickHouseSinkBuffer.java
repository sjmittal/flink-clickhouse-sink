package ru.ivi.opensource.flinkclickhousesink.applied;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseRequestBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClickHouseSinkBuffer<T> implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSinkBuffer.class);

    private final ClickHouseWriter writer;
    private final String targetTable;
    private final int maxFlushBufferSize;
    private final long timeoutMillis;
    private final List<T> localValues;
    private final Class<T> clazz;

    private volatile long lastAddTimeMillis = System.currentTimeMillis();

    private ClickHouseSinkBuffer(
            ClickHouseWriter chWriter,
            long timeout,
            int maxBuffer,
            String table,
            Class<T> clazz
    ) {
        writer = chWriter;
        localValues = new ArrayList<>();
        timeoutMillis = timeout;
        maxFlushBufferSize = maxBuffer;
        targetTable = table;
        this.clazz = clazz;

        logger.info("Instance ClickHouse Sink class = {}, target table = {}, buffer size = {}", this.clazz, this.targetTable, this.maxFlushBufferSize);
    }

    String getTargetTable() {
        return targetTable;
    }

    public void put(T recordAsCSV) {
        tryAddToQueue();
        localValues.add(recordAsCSV);
    }

    synchronized void tryAddToQueue() {
        if (flushCondition()) {
            addToQueue();
            lastAddTimeMillis = System.currentTimeMillis();
        }
    }

    private void addToQueue() {
        List<T> deepCopy = buildDeepCopy(localValues);
        ClickHouseRequestBlank<?> params = ClickHouseRequestBlank.Builder
                .aBuilder(clazz)
                .withValues(deepCopy)
                .withTargetTable(targetTable)
                .build();

        logger.debug("Build blank with params: buffer size = {}, target table  = {}", params.getValues().size(), params.getTargetTable());
        writer.put(params);

        localValues.clear();
    }

    private boolean flushCondition() {
        return localValues.size() > 0 && (checkSize() || checkTime());
    }

    private boolean checkSize() {
        return localValues.size() >= maxFlushBufferSize;
    }

    private boolean checkTime() {
        long current = System.currentTimeMillis();
        return current - lastAddTimeMillis > timeoutMillis;
    }

    private static <T> List<T> buildDeepCopy(List<T> original) {
        return List.copyOf(original);
    }

    @Override
    public void close() {
        logger.info("ClickHouse sink buffer is shutting down.");
        if (localValues != null && localValues.size() > 0) {
            addToQueue();
        }
        logger.info("ClickHouse sink buffer shutdown complete.");
    }

    public static final class Builder<T> {
        private String targetTable;
        private int maxFlushBufferSize;
        private int timeoutSec;

        private final Class<T> clazz;

        private Builder(Class<T> clazz) {
            this.clazz = clazz;
        }

        public static <T> Builder<T> aClickHouseSinkBuffer(Class<T> clazz) {
            return new Builder<>(clazz);
        }

        public Builder<T> withTargetTable(String targetTable) {
            this.targetTable = targetTable;
            return this;
        }

        public Builder<T> withMaxFlushBufferSize(int maxFlushBufferSize) {
            this.maxFlushBufferSize = maxFlushBufferSize;
            return this;
        }

        public Builder<T> withTimeoutSec(int timeoutSec) {
            this.timeoutSec = timeoutSec;
            return this;
        }


        public ClickHouseSinkBuffer<T> build(ClickHouseWriter writer) {

            Preconditions.checkNotNull(targetTable);
            Preconditions.checkArgument(maxFlushBufferSize > 0);
            Preconditions.checkArgument(timeoutSec > 0);

            return new ClickHouseSinkBuffer<>(
                    writer,
                    TimeUnit.SECONDS.toMillis(this.timeoutSec),
                    this.maxFlushBufferSize,
                    this.targetTable,
                    clazz
            );
        }
    }
}
