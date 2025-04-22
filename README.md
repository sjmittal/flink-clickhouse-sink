
# Flink-ClickHouse-Sink

## Description

[Flink](https://github.com/apache/flink) sink for [ClickHouse](https://github.com/ClickHouse/ClickHouse) database. 
Powered by [Clickhouse V2 Client](https://github.com/ClickHouse/clickhouse-java/tree/main/client-v2).

High-performance library for loading data to ClickHouse. 

- It has two triggers for loading data: _by timeout_ and _by buffer size_.
- Failures are written to S3.

##### Version map
| flink  | flink-clickhouse-sink | 
|:------:|:---------------------:| 
| 1.19.* |         1.4.7         |
| 1.20.* |         1.4.7         |

### Install

##### Maven Central

```xml
<dependency>
  <groupId>ru.ivi.opensource</groupId>
  <artifactId>flink-clickhouse-sink</artifactId>
  <version>1.4.7</version>
</dependency>
```

## Usage
### Properties
The flink-clickhouse-sink uses two parts of configuration properties: 
common and for each sink in you operators chain.

**The common part** (use like global):

 `clickhouse.sink.num-writers` - number of writers, which build and send requests, 
 
 `clickhouse.sink.queue-max-capacity` - max capacity (batches) of blank's queue,
 
 `clickhouse.sink.timeout-sec` - timeout for loading data,
 
 `clickhouse.sink.retries` - max number of retries,
 
 `clickhouse.sink.failed-records-path`- s3 bucket for failed records,

 `clickhouse.sink.failed-records-region`- s3 region for failed records,

 `clickhouse.sink.failed-records-access-key`- s3 access key for failed records,

 `clickhouse.sink.failed-records-secret-key`- s3 secret key for failed records,

**The sink part** (use in chain):

 `clickhouse.sink.target-table` - target table in ClickHouse,
 
 `clickhouse.sink.max-buffer-size`- buffer size.

### In code

#### Configuration: global parameters

At first, you add global parameters for the Flink environment:
```java
StreamExecutionEnvironment environment = StreamExecutionEnvironment.createLocalEnvironment();
Map<String, String> globalParameters = new HashMap<>();

// ClickHouse cluster properties
globalParameters.put(ClickHouseClusterSettings.CLICKHOUSE_HOSTS, ...);
globalParameters.put(ClickHouseClusterSettings.CLICKHOUSE_USER, ...);
globalParameters.put(ClickHouseClusterSettings.CLICKHOUSE_PASSWORD, ...);

// sink common
globalParameters.put(ClickHouseSinkConst.TIMEOUT_SEC, ...);
globalParameters.put(ClickHouseSinkConst.FAILED_RECORDS_PATH, ...);
globalParameters.put(ClickHouseSinkConst.NUM_WRITERS, ...);
globalParameters.put(ClickHouseSinkConst.NUM_RETRIES, ...);
globalParameters.put(ClickHouseSinkConst.QUEUE_MAX_CAPACITY, ...);

// set global paramaters
ParameterTool parameters = ParameterTool.fromMap(buildGlobalParameters(config));
environment.getConfig().setGlobalJobParameters(parameters);

```


## Roadmap
- [ ] handle "failed-records" for other providers
- [ ] add test cases
