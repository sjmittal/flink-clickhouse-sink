package ru.ivi.opensource.flinkclickhousesink.model;

import com.google.common.base.Preconditions;

import java.util.Map;

import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.FAILED_RECORDS_PATH;
import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.NUM_RETRIES;
import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.NUM_WRITERS;
import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.QUEUE_MAX_CAPACITY;
import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.TIMEOUT_SEC;

public class ClickHouseSinkCommonParams {

    private final ClickHouseClusterSettings clickHouseClusterSettings;
    private final String failedRecordsPath;
    private final int numWriters;
    private final int queueMaxCapacity;
    private final int timeout;
    private final int maxRetries;

    public ClickHouseSinkCommonParams(Map<String, String> params) {
        this.clickHouseClusterSettings = new ClickHouseClusterSettings(params);
        this.numWriters = Integer.parseInt(params.get(NUM_WRITERS));
        this.queueMaxCapacity = Integer.parseInt(params.get(QUEUE_MAX_CAPACITY));
        this.maxRetries = Integer.parseInt(params.get(NUM_RETRIES));
        this.timeout = Integer.parseInt(params.get(TIMEOUT_SEC));
        this.failedRecordsPath = params.get(FAILED_RECORDS_PATH);

        Preconditions.checkNotNull(failedRecordsPath);
        Preconditions.checkArgument(queueMaxCapacity > 0);
        Preconditions.checkArgument(numWriters > 0);
        Preconditions.checkArgument(timeout > 0);
        Preconditions.checkArgument(maxRetries > 0);
    }

    public int getNumWriters() {
        return numWriters;
    }

    public int getQueueMaxCapacity() {
        return queueMaxCapacity;
    }

    public ClickHouseClusterSettings getClickHouseClusterSettings() {
        return clickHouseClusterSettings;
    }

    public int getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public String getFailedRecordsPath() {
        return failedRecordsPath;
    }

    @Override
    public String toString() {
        return "ClickHouseSinkCommonParams{" +
                "clickHouseClusterSettings=" + clickHouseClusterSettings +
                ", failedRecordsPath='" + failedRecordsPath + '\'' +
                ", numWriters=" + numWriters +
                ", queueMaxCapacity=" + queueMaxCapacity +
                ", timeout=" + timeout +
                ", maxRetries=" + maxRetries +
                '}';
    }
}
