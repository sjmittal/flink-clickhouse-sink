package ru.ivi.opensource.flinkclickhousesink.model;

import java.util.List;

public class ClickHouseRequestBlank<T> {
    private final List<T> values;
    private final String targetTable;
    private final int clientIndex;
    private final int maxFlushBufferSize;

    public ClickHouseRequestBlank(List<T> values, String targetTable, int clientIndex, int maxFlushBufferSize) {
        this.values = values;
        this.targetTable = targetTable;
        this.clientIndex = clientIndex;
        this.maxFlushBufferSize = maxFlushBufferSize;
    }

    public List<T> getValues() {
        return values;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public int getClientIndex() {
        return clientIndex;
    }

    public int getMaxFlushBufferSize() {
        return maxFlushBufferSize;
    }

    public static final class Builder<T> {
        private List<T> values;
        private String targetTable;

        private int clientIndex;

        private int maxFlushBufferSize;

        private Builder(Class<T> clazz) {
        }

        public static <T> Builder<T> aBuilder(Class<T> clazz) {
            return new Builder<>(clazz);
        }

        public Builder<T> withValues(List<T> values) {
            this.values = values;
            return this;
        }

        public Builder<T> withTargetTable(String targetTable) {
            this.targetTable = targetTable;
            return this;
        }

        public Builder<T> withClientIndex(int clientIndex) {
            this.clientIndex = clientIndex;
            return this;
        }

        public Builder<T> withMaxFlushBufferSize(int maxFlushBufferSize) {
            this.maxFlushBufferSize = maxFlushBufferSize;
            return this;
        }

        public ClickHouseRequestBlank<T> build() {
            return new ClickHouseRequestBlank<>(values, targetTable, clientIndex, maxFlushBufferSize);
        }
    }

    @Override
    public String toString() {
        return "ClickHouseRequestBlank{" +
                "values=" + values +
                ", targetTable='" + targetTable  +
                '}';
    }
}
