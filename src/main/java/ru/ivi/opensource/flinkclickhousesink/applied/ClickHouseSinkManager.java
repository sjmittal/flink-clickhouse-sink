package ru.ivi.opensource.flinkclickhousesink.applied;

import com.clickhouse.client.api.Client;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkCommonParams;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;

import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.MAX_BUFFER_SIZE;
import static ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkConst.TARGET_TABLE_NAME;

public class ClickHouseSinkManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSinkManager.class);

    private final ClickHouseWriter clickHouseWriter;
    private final ClickHouseSinkScheduledCheckerAndCleaner clickHouseSinkScheduledCheckerAndCleaner;
    private final ClickHouseSinkCommonParams sinkParams;
    private final Client client;
    private volatile boolean isClosed = false;

    public ClickHouseSinkManager(Map<String, String> globalParams) {
        sinkParams = new ClickHouseSinkCommonParams(globalParams);
        client = new Client.Builder()
          .addEndpoint(sinkParams.getClickHouseClusterSettings().getRandomHostUrl())
          .setUsername(sinkParams.getClickHouseClusterSettings().getUser())
          .setPassword(sinkParams.getClickHouseClusterSettings().getPassword())
          .setDefaultDatabase(sinkParams.getClickHouseClusterSettings().getUser())
          .setConnectionRequestTimeout(60, ChronoUnit.SECONDS)
          .setConnectTimeout(60, ChronoUnit.SECONDS)
          .setSocketTimeout(30, ChronoUnit.SECONDS)
          .build();
        clickHouseWriter = new ClickHouseWriter(sinkParams, client);
        clickHouseSinkScheduledCheckerAndCleaner = new ClickHouseSinkScheduledCheckerAndCleaner(sinkParams);
        logger.info("Build sink writer's manager. params = {}", sinkParams);
    }

    public <T> Sink<T> buildSink(Properties localProperties, Class<T> clazz) {
        String targetTable = localProperties.getProperty(TARGET_TABLE_NAME);
        int maxFlushBufferSize = Integer.parseInt(localProperties.getProperty(MAX_BUFFER_SIZE));

        return buildSink(targetTable, maxFlushBufferSize, clazz);
    }

    public <T> Sink<T> buildSink(String targetTable, int maxBufferSize, Class<T> clazz) {
        Preconditions.checkNotNull(clickHouseSinkScheduledCheckerAndCleaner);
        Preconditions.checkNotNull(clickHouseWriter);

        ClickHouseSinkBuffer<T> clickHouseSinkBuffer = ClickHouseSinkBuffer.Builder
                .aClickHouseSinkBuffer(clazz)
                .withTargetTable(targetTable)
                .withMaxFlushBufferSize(maxBufferSize)
                .withTimeoutSec(sinkParams.getTimeout())
                .build(clickHouseWriter);
        client.register(clazz, client.getTableSchema(targetTable));

        logger.info("Registered sink for table = {}, class = {}", targetTable, clazz);

        clickHouseSinkScheduledCheckerAndCleaner.addSinkBuffer(clickHouseSinkBuffer);

        return new Sink<>(clickHouseSinkBuffer);
    }

    public boolean isOpen() {
        return !isClosed;
    }

    @Override
    public void close() throws Exception {
        logger.info("ClickHouse sink manager is shutting down.");
        clickHouseSinkScheduledCheckerAndCleaner.close();
        clickHouseWriter.close();
        isClosed = true;
        logger.info("ClickHouse sink manager shutdown complete.");
    }
}
