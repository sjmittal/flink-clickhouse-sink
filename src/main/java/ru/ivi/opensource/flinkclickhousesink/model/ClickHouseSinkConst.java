package ru.ivi.opensource.flinkclickhousesink.model;

public final class ClickHouseSinkConst {
    private ClickHouseSinkConst() {
    }

    public static final String TARGET_TABLE_NAME = "clickhouse.sink.target-table";
    public static final String MAX_BUFFER_SIZE = "clickhouse.sink.max-buffer-size";

    public static final String NUM_WRITERS = "clickhouse.sink.num-writers";
    public static final String QUEUE_MAX_CAPACITY = "clickhouse.sink.queue-max-capacity";
    public static final String TIMEOUT_SEC = "clickhouse.sink.timeout-sec";
    public static final String NUM_RETRIES = "clickhouse.sink.retries";
    public static final String ASYNC_INSERT = "clickhouse.sink.async-insert";
    public static final String FAILED_RECORDS_ENDPOINT = "clickhouse.sink.failed-records-endpoint";
    public static final String FAILED_RECORDS_PATH = "clickhouse.sink.failed-records-path";
    public static final String FAILED_RECORDS_REGION = "clickhouse.sink.failed-records-region";
    public static final String FAILED_RECORDS_ACCESS_KEY = "clickhouse.sink.failed-records-access-key";
    public static final String FAILED_RECORDS_SECRET_KEY = "clickhouse.sink.failed-records-secret-key";

}
