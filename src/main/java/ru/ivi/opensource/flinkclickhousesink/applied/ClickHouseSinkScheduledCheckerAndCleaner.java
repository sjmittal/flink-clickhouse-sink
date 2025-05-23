package ru.ivi.opensource.flinkclickhousesink.applied;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkCommonParams;
import ru.ivi.opensource.flinkclickhousesink.util.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ClickHouseSinkScheduledCheckerAndCleaner implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSinkScheduledCheckerAndCleaner.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final List<ClickHouseSinkBuffer<?>> clickHouseSinkBuffers = new ArrayList<>();

    public ClickHouseSinkScheduledCheckerAndCleaner(ClickHouseSinkCommonParams props) {
        ThreadFactory factory = ThreadUtil.threadFactory("clickhouse-writer-checker-and-cleaner");
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(factory);
        scheduledExecutorService.scheduleWithFixedDelay(getTask(), props.getTimeout(), props.getTimeout(), TimeUnit.SECONDS);
        logger.info("Build Sink scheduled checker, timeout (sec) = {}", props.getTimeout());
    }

    public void addSinkBuffer(ClickHouseSinkBuffer<?> clickHouseSinkBuffer) {
        synchronized (this) {
            clickHouseSinkBuffers.add(clickHouseSinkBuffer);
        }
        logger.debug("Add sinkBuffer, target table = {}", clickHouseSinkBuffer.getTargetTable());
    }

    private Runnable getTask() {
        return () -> {
            synchronized (this) {
                clickHouseSinkBuffers.forEach(ClickHouseSinkBuffer::tryAddToQueue);
            }
        };
    }

    @Override
    public void close() throws Exception {
        logger.info("ClickHouseSinkScheduledCheckerAndCleaner is shutting down.");
        ThreadUtil.shutdownExecutorService(scheduledExecutorService);
        logger.info("ClickHouseSinkScheduledCheckerAndCleaner shutdown complete.");
    }
}
