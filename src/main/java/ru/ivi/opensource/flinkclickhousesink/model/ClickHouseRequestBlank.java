package ru.ivi.opensource.flinkclickhousesink.model;

import java.util.List;

public class ClickHouseRequestBlank<T> {
    private final List<T> values;
    private final String targetTable;
    private int attemptCounter;
    private long requestTime;

    public ClickHouseRequestBlank(List<T> values, String targetTable) {
        this.values = values;
        this.targetTable = targetTable;
        this.attemptCounter = 0;
        this.requestTime = System.currentTimeMillis();
    }

    public List<T> getValues() {
        return values;
    }

    public void incrementCounter() {
        this.attemptCounter++;
    }

    public int getAttemptCounter() {
        return attemptCounter;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public long getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(long requestTime) {
        this.requestTime = requestTime;
    }

    public static final class Builder<T> {
        private List<T> values;
        private String targetTable;

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

        public ClickHouseRequestBlank<T> build() {
            return new ClickHouseRequestBlank<>(values, targetTable);
        }
    }

    @Override
    public String toString() {
        return "ClickHouseRequestBlank{" +
                "values=" + values +
                ", targetTable='" + targetTable  +
                ", attemptCounter=" + attemptCounter +
                ", requestTime=" + requestTime +
                '}';
    }
}
