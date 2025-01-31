package ru.ivi.opensource.flinkclickhousesink.applied;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseRequestBlank;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkCommonParams;
import ru.ivi.opensource.flinkclickhousesink.util.ThreadUtil;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ClickHouseWriter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseWriter.class);
    private static final Gson gson = new Gson();

    private final transient Client client;
    private final transient S3Client s3Client;

    private final BlockingQueue<ClickHouseRequestBlank<?>> commonQueue;
    private final AtomicLong unprocessedRequestsCounter = new AtomicLong();
    private final ClickHouseSinkCommonParams sinkParams;

    private ExecutorService service;
    private List<WriterTask> tasks;

    public ClickHouseWriter(ClickHouseSinkCommonParams sinkParams, Client client) {
        this.sinkParams = sinkParams;
        this.commonQueue = new LinkedBlockingQueue<>(sinkParams.getQueueMaxCapacity());
        this.client = client;

        s3Client = S3Client.builder()
          .credentialsProvider(InstanceProfileCredentialsProvider.create())
          .build();

        initDirAndExecutors();
    }

    private void initDirAndExecutors() {
        try {
            buildComponents();
        } catch (Exception e) {
            logger.error("Error while starting CH writer", e);
            throw new RuntimeException(e);
        }
    }


    private void buildComponents() {
        logger.info("Building components");

        ThreadFactory threadFactory = ThreadUtil.threadFactory("clickhouse-writer");
        service = Executors.newFixedThreadPool(sinkParams.getNumWriters(), threadFactory);

        int numWriters = sinkParams.getNumWriters();
        tasks = Lists.newArrayListWithCapacity(numWriters);
        for (int i = 0; i < numWriters; i++) {
            WriterTask task = new WriterTask(i, client, s3Client, commonQueue, sinkParams, unprocessedRequestsCounter);
            tasks.add(task);
            service.submit(task);
        }
    }

    public void put(ClickHouseRequestBlank<?> params) {
        boolean offered = commonQueue.offer(params);
        if (!offered) {
            logFailedRecords(params);
        } else {
            unprocessedRequestsCounter.incrementAndGet();
        }
    }

    private void logFailedRecords(ClickHouseRequestBlank<?> requestBlank) {
        String pathName = String.format("failed_records/%s", requestBlank.getTargetTable());
        String batchKey = String.format("%s/%s_", pathName, System.currentTimeMillis());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            List<?> records = requestBlank.getValues();
            for (Object record: records) {
                try {
                    outputStream.write(gson.toJson(record).getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    //
                }
            }

            PutObjectRequest putObjectRequest =
              PutObjectRequest.builder()
                .bucket(sinkParams.getFailedRecordsPath())
                .key(batchKey + UUID.randomUUID())
                .contentLength((long) outputStream.size())
                .build();

            try (ByteArrayInputStream inputStream =
                   new ByteArrayInputStream(outputStream.toByteArray())) {
                s3Client.putObject(
                  putObjectRequest, RequestBody.fromInputStream(inputStream, outputStream.size()));
                logger.info("Successful send data on s3, path = {}, batch size = {} ", pathName, requestBlank.getValues().size());
            } catch (Exception e) {
                logger.error("Unknown exception while publishing data on s3 with path {} to S3", batchKey, e);
            }
        } catch (Exception e) {
            logger.error("Unknown exception while publishing data on s3 with path {} to stream", batchKey, e);
        }
    }

    private void waitUntilAllRequestsDone() throws InterruptedException {
        try {
            if (unprocessedRequestsCounter.get() > 0 || commonQueue.size() > 0) {
                logger.info("request queue size: {}, pending requests size: {}", commonQueue.size(), unprocessedRequestsCounter.get());
                Thread.sleep(sinkParams.getTimeout() * 1000L);
            }
        } finally {
            stopWriters();
        }
    }

    private void stopWriters() {
        logger.info("Stopping writers.");
        if (tasks != null && tasks.size() > 0) {
            tasks.forEach(WriterTask::setStopWorking);
        }
        logger.info("Writers stopped.");
    }

    @Override
    public void close() throws Exception {
        logger.info("ClickHouseWriter is shutting down.");
        try {
            waitUntilAllRequestsDone();
        } finally {
            ThreadUtil.shutdownExecutorService(service);
            s3Client.close();
            client.close();
            logger.info("{} shutdown complete.", ClickHouseWriter.class.getSimpleName());
        }
    }

    static class WriterTask implements Runnable {
        private static final Logger logger = LoggerFactory.getLogger(WriterTask.class);

        private final BlockingQueue<ClickHouseRequestBlank<?>> queue;
        private final AtomicLong queueCounter;
        private final ClickHouseSinkCommonParams sinkSettings;
        private final Client client;
        private final S3Client s3Client;
        private final int id;

        private volatile boolean isWorking;

        WriterTask(int id,
                   Client client,
                   S3Client s3Client,
                   BlockingQueue<ClickHouseRequestBlank<?>> queue,
                   ClickHouseSinkCommonParams settings,
                   AtomicLong queueCounter) {
            this.id = id;
            this.sinkSettings = settings;
            this.queue = queue;
            this.client = client;
            this.s3Client = s3Client;
            this.queueCounter = queueCounter;
        }

        @Override
        public void run() {
            try {
                isWorking = true;

                logger.info("Start writer task, id = {}", id);
                while (isWorking || queue.size() > 0) {
                    ClickHouseRequestBlank<?> blank = queue.poll(300, TimeUnit.MILLISECONDS);
                    if (blank != null) {
                        logger.info(
                          "Task id = {} Ready to load data to {}, batch size = {}, pending queue size = {}",
                          id,
                          blank.getTargetTable(),
                          blank.getValues().size(),
                          queueCounter.get());
                        blank.setRequestTime(System.currentTimeMillis());
                        try {
                            CompletableFuture<InsertResponse> future =
                              client.insert(blank.getTargetTable(), blank.getValues());
                            complete(blank, future);
                        }  catch (Exception e) {
                            logger.error("Task id = {} Error while inserting data", id, e);
                            queueCounter.decrementAndGet();
                            handleUnsuccessfulResponse(e, blank);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Task id = {} Error while inserting data", id, e);
                throw new RuntimeException(e);
            } finally {
                logger.info("Task id = {} is finished", id);
            }
        }

        private void complete(ClickHouseRequestBlank<?> requestBlank, CompletableFuture<InsertResponse> future) {
            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    handleUnsuccessfulResponse(throwable, requestBlank);
                } else {
                    logger.info("Task id = {} Successful send data to ClickHouse, pending queue size = {}, batch size = {}, target table = {}, current attempt = {}, time = {}",
                      id,
                      queueCounter.get(),
                      requestBlank.getValues().size(),
                      requestBlank.getTargetTable(),
                      requestBlank.getAttemptCounter(),
                      System.currentTimeMillis() - requestBlank.getRequestTime());
                }

                queueCounter.decrementAndGet();
            });
        }

        private void handleUnsuccessfulResponse(Throwable throwable, ClickHouseRequestBlank<?> requestBlank) {
            int currentCounter = requestBlank.getAttemptCounter();
            if (currentCounter >= sinkSettings.getMaxRetries()) {
                logger.warn("Task id = {} Failed to send data to ClickHouse, cause: limit of attempts is exceeded." +
                        " ClickHouse response = {}. Ready to flush data on s3.", id, throwable.getMessage());
                logFailedRecords(requestBlank);
            } else {
                requestBlank.incrementCounter();
                logger.warn("Task id = {} Next attempt to send data to ClickHouse, table = {}, batch size = {}, current attempt num = {}, max attempt num = {}, response = {}",
                        id,
                        requestBlank.getTargetTable(),
                        requestBlank.getValues().size(),
                        requestBlank.getAttemptCounter(),
                        sinkSettings.getMaxRetries(),
                        throwable.getMessage());
                boolean offered = queue.offer(requestBlank);
                if (!offered) {
                    logFailedRecords(requestBlank);
                } else {
                    queueCounter.incrementAndGet();
                }
            }
        }

        private void logFailedRecords(ClickHouseRequestBlank<?> requestBlank) {
            String pathName = String.format("failed_records/%s", requestBlank.getTargetTable());
            String batchKey = String.format("%s/%s_", pathName, System.currentTimeMillis());

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                List<?> records = requestBlank.getValues();
                for (Object record: records) {
                    try {
                        outputStream.write(gson.toJson(record).getBytes(StandardCharsets.UTF_8));
                    } catch (Exception e) {
                        //
                    }
                }

                PutObjectRequest putObjectRequest =
                  PutObjectRequest.builder()
                    .bucket(sinkSettings.getFailedRecordsPath())
                    .key(batchKey + UUID.randomUUID())
                    .contentLength((long) outputStream.size())
                    .build();

                try (ByteArrayInputStream inputStream =
                       new ByteArrayInputStream(outputStream.toByteArray())) {
                    s3Client.putObject(
                      putObjectRequest, RequestBody.fromInputStream(inputStream, outputStream.size()));
                    logger.info("Task id = {} Successful send data on s3, path = {}, batch size = {} ", id, pathName, requestBlank.getValues().size());
                } catch (Exception e) {
                    logger.error("Task id = {} Unknown exception while publishing data on s3 with path {} to S3", id, batchKey, e);
                }
            } catch (Exception e) {
                logger.error("Task id = {} Unknown exception while publishing data on s3 with path {} to stream", id, batchKey, e);
            }
        }

        void setStopWorking() {
            isWorking = false;
        }
    }
}