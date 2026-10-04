# Kafka Subproject

## Overview

This is a Kafka demonstration module showcasing native Kafka APIs (not Spring Kafka annotations) for producers, consumers, and Kafka Streams. It covers various real-world patterns and configurations.

## Project Structure

```
kafka/
├── build.gradle
├── README.md
└── src/
    ├── main/java/com/example/kafka/
    │   ├── ProducerApp.java              # Native Kafka producer examples
    │   ├── ConsumerApp.java              # Native Kafka consumer examples
    │   ├── StreamsApp.java               # Basic filtering stream
    │   ├── WordCountStreamsApp.java      # Word counting stream
    │   ├── FavouriteColourStreamsApp.java # Stateful aggregation stream
    │   ├── BankTransactionStreamsApp.java # JSON aggregation with exactly-once
    │   └── BankTransactionProducer.java   # JSON message producer
    └── test/java/com/example/kafka/
        ├── ProducerAppTests.java
        ├── ConsumerAppTests.java
        ├── StreamsAppTests.java
        ├── WordCountStreamsAppTests.java      # TopologyTestDriver, no broker needed
        ├── BankTransactionStreamsAppTests.java # TopologyTestDriver, no broker needed
        └── FavouriteColourStreamsAppRunner.java
```

## Dependencies

- `org.springframework.boot:spring-boot-starter-kafka` - Kafka clients + Spring Kafka (used for `JacksonJsonSerde` only)
- `org.apache.kafka:kafka-streams` - Native Kafka Streams API
- `tools.jackson.core:jackson-databind` - JSON processing (Jackson 3)
- `org.projectlombok:lombok`

## Key Components

### 1. ProducerApp

Native Kafka producer with multiple configuration profiles:

| Method                                       | Purpose                      | Key Config                                    |
|----------------------------------------------|------------------------------|-----------------------------------------------|
| `produceMessage()`                           | Basic async producer         | Default settings                              |
| `produceMessageWithCallback()`               | Async with delivery callback | Logs metadata on success/failure              |
| `produceMessageKey()`                        | Keyed messages (sync)        | Uses `.get()` for blocking                    |
| `produceMessageWithSafeProducer()`           | Idempotent producer          | `acks=all`, `idempotence=true`, `retries=MAX` |
| `produceMessageWithHighThroughputProducer()` | Throughput optimized         | SNAPPY compression, 20ms linger, 32KB batch   |

### 2. ConsumerApp

Native Kafka consumer with various consumption patterns:

| Method                             | Purpose                               | Key Config                               |
|------------------------------------|---------------------------------------|------------------------------------------|
| `consumeMessage()`                 | Basic polling loop                    | `AUTO_OFFSET_RESET=EARLIEST`             |
| `consumeMessageWithThread()`       | Multi-threaded with graceful shutdown | `CountDownLatch`, `WakeupException`      |
| `consumeMessageAssignAndSeek()`    | Manual partition assignment           | Seek to specific offset, read N messages |
| `consumeMessageWithManualCommit()` | Manual offset commit                  | `AUTO_COMMIT=false`, `commitSync()`      |

### 3. Kafka Streams Applications

#### WordCountStreamsApp
- **Input**: `streams-plaintext-input` (plain text)
- **Output**: `streams-wordcount-output` (word -> count)
- **Topology**: stream → lowercase → split → selectKey → groupByKey → count → to

#### FavouriteColourStreamsApp
- **Input**: `favouritecolour-streams-input` (CSV: `user,colour`)
- **Output**: `favouritecolour-streams-output` (colour -> count)
- **Features**: Filters bad data, only accepts red/blue/green, tracks user preference changes
- **Two approaches**: Direct transform vs. intermediary topic with compaction

#### BankTransactionStreamsApp
- **Input**: `streams-bank-transaction-input` (JSON: `{name, amount, time}`)
- **Output**: `streams-bank-transaction-output` (JSON: `{count, balance, time}`)
- **Features**: `EXACTLY_ONCE_V2` processing guarantee, stateful aggregation with KeyValueStore
- **Serde**: `JacksonJsonSerde<JsonNode>` with `noTypeInfo()` + `ignoreTypeHeaders()` - plain JSON on the wire, no `__TypeId__` headers

## Topics Reference

| Topic                             | Format      | Purpose                            |
|-----------------------------------|-------------|------------------------------------|
| `first_topic`                     | String      | Basic producer/consumer testing    |
| `streams-plaintext-input`         | Plain text  | WordCount input                    |
| `streams-wordcount-output`        | String/Long | WordCount output                   |
| `favouritecolour-streams-input`   | CSV         | FavouriteColour input              |
| `favouritecolour-streams-output`  | String/Long | FavouriteColour output (compacted) |
| `streams-bank-transaction-input`  | JSON        | BankTransaction input              |
| `streams-bank-transaction-output` | JSON        | BankTransaction output (compacted) |

## Configuration Patterns

### Safe Producer (No Data Loss)
```java
props.put(ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ACKS_CONFIG, "all");
props.put(RETRIES_CONFIG, Integer.MAX_VALUE);
props.put(MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
```

### High Throughput Producer
```java
props.put(COMPRESSION_TYPE_CONFIG, "snappy");
props.put(LINGER_MS_CONFIG, 20);
props.put(BATCH_SIZE_CONFIG, 32 * 1024);
```

### Manual Commit Consumer
```java
props.put(ENABLE_AUTO_COMMIT_CONFIG, false);
props.put(MAX_POLL_RECORDS_CONFIG, 10);
// After processing batch:
consumer.commitSync();
```

### Exactly-Once Streams
```java
props.put(PROCESSING_GUARANTEE_CONFIG, EXACTLY_ONCE_V2);
```

## Running the Demos

See `README.md` for detailed instructions on:
1. Creating required topics
2. Starting console consumers
3. Running stream applications
4. Producing test messages

## Default Bootstrap Server

All applications default to `localhost:9092`. Override via constructor or command-line arguments.
