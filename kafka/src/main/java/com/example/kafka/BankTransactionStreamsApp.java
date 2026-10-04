package com.example.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.kafka.support.serializer.JacksonJsonSerde;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class BankTransactionStreamsApp {

    private static final String DEFAULT_BOOTSTRAP_SERVER = "localhost:9092";
    private static final String APPLICATION_ID = "bank-transaction-streams-app";
    private static final String QUERYABLE_STORE_NAME = "bank-transaction-aggregate";
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "bank-transaction-streams-shutdown-hook";
    private final String bootstrapServer;

    public BankTransactionStreamsApp(String bootstrapServer) {
        this.bootstrapServer = bootstrapServer;
    }

    private Properties createProperties() {
        var properties = new Properties();
        properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServer);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        return properties;
    }

    public Topology createTopology(String fromTopic, String toTopic) {
        // Plain JSON on the wire (no type headers), matching what BankTransactionProducer sends
        Serde<JsonNode> jsonSerde = new JacksonJsonSerde<>(JsonNode.class).noTypeInfo().ignoreTypeHeaders();

        var builder = new StreamsBuilder();
        var bankTransactionInput = builder.stream(fromTopic, Consumed.with(Serdes.String(), jsonSerde));

        var initialBalance = JsonNodeFactory.instance.objectNode();
        initialBalance.put("count", 0);
        initialBalance.put("balance", 0);
        initialBalance.put("time", Instant.ofEpochMilli(0L).toString());

        var bankBalance = bankTransactionInput
                .groupByKey(Grouped.with(Serdes.String(), jsonSerde))
                .aggregate(
                        () -> initialBalance,
                        (key, transaction, balance) -> newBalance(transaction, balance),
                        Materialized.<String, JsonNode, KeyValueStore<Bytes, byte[]>>as(QUERYABLE_STORE_NAME)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(jsonSerde)
                );
        bankBalance.toStream().to(toTopic, Produced.with(Serdes.String(), jsonSerde));
        return builder.build();
    }

    private JsonNode newBalance(JsonNode transaction, JsonNode balance) {
        ObjectNode newBalance = JsonNodeFactory.instance.objectNode();
        newBalance.put("count", balance.get("count").asInt() + 1);
        newBalance.put("balance", balance.get("balance").asInt() + transaction.get("amount").asInt());

        var balanceEpoch = Instant.parse(balance.get("time").asString()).toEpochMilli();
        var transactionEpoch = Instant.parse(transaction.get("time").asString()).toEpochMilli();
        var newBalanceInstant = Instant.ofEpochMilli(Math.max(balanceEpoch, transactionEpoch));
        newBalance.put("time", newBalanceInstant.toString());
        return newBalance;
    }

    public static void main(String[] args) {
        var inputTopic = "streams-bank-transaction-input";
        var outputTopic = "streams-bank-transaction-output";
        if (args.length > 2) {
            outputTopic = args[2];
        }
        if (args.length > 1) {
            inputTopic = args[1];
        }
        var bootstrapServer = DEFAULT_BOOTSTRAP_SERVER;
        if (args.length > 0) {
            bootstrapServer = args[0];
        }
        var app = new BankTransactionStreamsApp(bootstrapServer);
        // build the topology and start our streams
        var streams = new KafkaStreams(app.createTopology(inputTopic, outputTopic), app.createProperties());
        var latch = new CountDownLatch(1);

        // shutdown hook to correctly close the streams application
        // attach shutdown handler to catch control Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            // printed the topology
            log.info(streams.toString());
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
    }
}
