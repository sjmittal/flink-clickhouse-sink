package ru.ivi.opensource.flinkclickhousesink.applied;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.metrics.Metric;
import com.clickhouse.client.api.metrics.OperationMetrics;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseRequestBlank;
import ru.ivi.opensource.flinkclickhousesink.model.ClickHouseSinkCommonParams;
import ru.ivi.opensource.flinkclickhousesink.util.ThreadUtil;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.clickhouse.client.api.metrics.ServerMetrics.ELAPSED_TIME;

public class ClickHouseWriter implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClickHouseWriter.class);
    private static final Gson gson = new Gson();

    private final transient List<Client> clients;
    private final transient S3Client s3Client;

    private final BlockingQueue<ClickHouseRequestBlank<?>> commonQueue;
    private final AtomicLong unprocessedRequestsCounter = new AtomicLong();
    private final ClickHouseSinkCommonParams sinkParams;

    private ExecutorService service;
    private WriterTask task;

    public ClickHouseWriter(ClickHouseSinkCommonParams sinkParams, List<Client> clients) {
        this.sinkParams = sinkParams;
        this.commonQueue = new LinkedBlockingQueue<>(sinkParams.getQueueMaxCapacity());
        this.clients = clients;

        if (sinkParams.getFailedRecordsEndpoint() != null) {
            s3Client = S3Client.builder()
              .endpointOverride(URI.create(sinkParams.getFailedRecordsEndpoint()))
              .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                  sinkParams.getFailedRecordsAccessKey(), sinkParams.getFailedRecordsSecretKey())))
              .region(Region.of(sinkParams.getFailedRecordsRegion()))
              .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
              .httpClient(UrlConnectionHttpClient.create())
              .build();
        } else if (sinkParams.getFailedRecordsRegion() != null) {
            s3Client = S3Client
              .builder()
              .region(Region.of(sinkParams.getFailedRecordsRegion()))
              .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                  sinkParams.getFailedRecordsAccessKey(), sinkParams.getFailedRecordsSecretKey())))
              .build();
        } else {
            s3Client = S3Client.builder()
              .credentialsProvider(InstanceProfileCredentialsProvider.create())
              .build();
        }

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
        service = Executors.newFixedThreadPool(1, threadFactory);

        task = new WriterTask(clients, s3Client, commonQueue, sinkParams, unprocessedRequestsCounter);
        service.submit(task);
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
            stopWriter();
        }
    }

    private void stopWriter() {
        logger.info("Stopping writer.");
        task.setStopWorking();
        logger.info("Writer stopped.");
    }

    @Override
    public void close() throws Exception {
        logger.info("ClickHouseWriter is shutting down.");
        try {
            waitUntilAllRequestsDone();
        } finally {
            ThreadUtil.shutdownExecutorService(service);
            s3Client.close();
            clients.forEach(Client::close);
            logger.info("{} shutdown complete.", ClickHouseWriter.class.getSimpleName());
        }
    }

    static class WriterTask implements Runnable {
        private static final Logger logger = LoggerFactory.getLogger(WriterTask.class);

        private final BlockingQueue<ClickHouseRequestBlank<?>> queue;
        private final AtomicLong queueCounter;
        private final ClickHouseSinkCommonParams sinkSettings;
        private final List<Client> clients;
        private final S3Client s3Client;
        private volatile boolean isWorking;

        WriterTask(List<Client> clients,
                   S3Client s3Client,
                   BlockingQueue<ClickHouseRequestBlank<?>> queue,
                   ClickHouseSinkCommonParams settings,
                   AtomicLong queueCounter) {
            this.sinkSettings = settings;
            this.queue = queue;
            this.clients = clients;
            this.s3Client = s3Client;
            this.queueCounter = queueCounter;
        }

        @Override
        public void run() {
            logger.info("Start writer task");
            try {
                isWorking = true;
                while (true) {
                    try {
                        if (!isWorking && queue.isEmpty()) {
                            logger.info("Writer task exiting gracefully");
                            break;
                        }
                        Map<String, List<Object>> blanks = new HashMap<>();
                        Map<String, Integer> clientIndexes = new HashMap<>();
                        List<ClickHouseRequestBlank<?>> removedBlanks = new ArrayList<>();
                        ClickHouseRequestBlank<?> first = queue.poll(1, TimeUnit.SECONDS);
                        if (first != null) {
                            removedBlanks.add(first);
                            queue.drainTo(removedBlanks);
                        } else {
                            continue;
                        }
                        for (ClickHouseRequestBlank<?> blank : removedBlanks) {
                            if (blank == null) {
                                logger.warn("Null blank encountered");
                                continue;
                            }
                            List<?> values = blank.getValues();
                            if (values == null || values.isEmpty()) {
                                logger.warn("Empty values for table {}", blank.getTargetTable());
                                continue;
                            }
                            blanks.computeIfAbsent(blank.getTargetTable(), k -> new ArrayList<>()).addAll(values);
                            clientIndexes.computeIfAbsent(blank.getTargetTable(), k -> blank.getClientIndex());
                            queueCounter.decrementAndGet();
                        }
                        for (Map.Entry<String, List<Object>> entry : blanks.entrySet()) {
                            try {
                                logger.info(
                                  "Task Ready to load data to {}, batch size = {}, pending queue size = {}",
                                  entry.getKey(),
                                  entry.getValue().size(),
                                  queueCounter.get()
                                );
                                long requestStartTime = System.currentTimeMillis();
                                Client client = clients.get(clientIndexes.get(entry.getKey()));
                                CompletableFuture<InsertResponse> future =
                                  client.insert(entry.getKey(), entry.getValue());
                                complete(requestStartTime, entry, future);
                            } catch (Exception e) {
                                logger.error("Task Error while inserting data", e);
                                logFailedRecords(entry);
                            }
                        }
                    } catch (Throwable t) {
                        logger.error("Writer task recovered from error", t);
                    }
                }
            } finally {
                logger.info("Task is finished");
            }
        }

        private void complete(long requestStartTime, Map.Entry<String, List<Object>> requestBlank, CompletableFuture<InsertResponse> future) {
            future.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    logger.error("Task Complete Error while inserting data", throwable);
                    logFailedRecords(requestBlank);
                } else {
                    OperationMetrics metrics = response.getMetrics();
                    Metric elapsedTime = metrics.getMetric(ELAPSED_TIME);
                    logger.info("Task Successful send data to ClickHouse, pending queue size = {}, batch size = {}, target table = {}, time = {}",
                      queueCounter.get(),
                      requestBlank.getValue().size(),
                      requestBlank.getKey(),
                      elapsedTime != null && elapsedTime.getLong() > 0 ?
                        TimeUnit.MILLISECONDS.convert(elapsedTime.getLong(), TimeUnit.NANOSECONDS) :
                        System.currentTimeMillis() - requestStartTime);
                }
            });
        }

        private void logFailedRecords(Map.Entry<String, List<Object>> requestBlank) {
            String pathName = String.format("failed_records/%s", requestBlank.getKey());
            String batchKey = String.format("%s/%s_", pathName, System.currentTimeMillis());

            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                List<?> records = requestBlank.getValue();
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
                    logger.info("Task Successful send data on s3, path = {}, batch size = {} ", pathName, requestBlank.getValue().size());
                } catch (Exception e) {
                    logger.error("Task Unknown exception while publishing data on s3 with path {} to S3", batchKey, e);
                }
            } catch (Exception e) {
                logger.error("Task Unknown exception while publishing data on s3 with path {} to stream", batchKey, e);
            }
        }

        void setStopWorking() {
            isWorking = false;
        }
    }
}