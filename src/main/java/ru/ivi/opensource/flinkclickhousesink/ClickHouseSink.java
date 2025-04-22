package ru.ivi.opensource.flinkclickhousesink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.applied.ClickHouseSinkManager;
import ru.ivi.opensource.flinkclickhousesink.applied.Sink;

import java.util.Map;
import java.util.Properties;

public class ClickHouseSink<T> extends RichSinkFunction<T> {

    private static final Logger logger = LoggerFactory.getLogger(ClickHouseSink.class);

    private static final Object DUMMY_LOCK = new Object();

    private final Properties localProperties;

    private final Class<T> clazz;

    private volatile static ClickHouseSinkManager sinkManager;
    private transient Sink<T> sink;

    public ClickHouseSink(Properties properties, Class<T> clazz) {
        this.localProperties = properties;
        this.clazz = clazz;
    }

    @Override
    public void open(Configuration config) {
        if (sinkManager == null) {
            synchronized (DUMMY_LOCK) {
                if (sinkManager == null) {
                    Map<String, String> params = getRuntimeContext()
                            .getExecutionConfig()
                            .getGlobalJobParameters()
                            .toMap();

                    sinkManager = new ClickHouseSinkManager(params);
                }
            }
        }

        sink = sinkManager.buildSink(localProperties, clazz);
    }

    /**
     * Add a record to sink
     *
     * @param record  record
     * @param context ctx
     */
    @Override
    public void invoke(T record, Context context) {
        try {
            sink.put(record);
        } catch (Exception e) {
            logger.error("Error while sending data to ClickHouse, record = {}", record, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        if (sink != null) {
            sink.close();
        }

        if (sinkManager != null && sinkManager.isOpen()) {
            synchronized (DUMMY_LOCK) {
                if (sinkManager != null && sinkManager.isOpen()) {
                    sinkManager.close();
                    sinkManager = null;
                }
            }
        }

        super.close();
    }
}
