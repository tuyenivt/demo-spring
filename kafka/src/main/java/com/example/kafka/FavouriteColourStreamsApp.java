package com.example.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class FavouriteColourStreamsApp {

    private static final String APPLICATION_ID = "favouritecolour-streams-app";
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "favouritecolour-streams-shutdown-hook";
    private final String bootstrapServer;

    public FavouriteColourStreamsApp(String bootstrapServer) {
        this.bootstrapServer = bootstrapServer;
    }

    private Properties createStreamsProperties() {
        var properties = new Properties();
        properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServer);
        properties.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        properties.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public void transform(String fromTopic, String toTopic) {
        var builder = new StreamsBuilder();
        // 1 - get stream from kafka
        var input = builder.<String, String>stream(fromTopic);
        var count = input
                // 2 - filter bad data
                .filter((defaultKey, line) -> line.contains(","))
                // 3 - select user as key is string at left side of comma character (we discard the old key)
                .selectKey((ignoredKey, line) -> line.split(",")[0].trim().toLowerCase())
                // 4 - map values to get colour as value (string at right side of comma character)
                .mapValues(line -> line.split(",")[1].trim().toLowerCase())
                // 5 - filter keep only colour "green", "red", or "blue"
                .filter((user, colour) -> Arrays.asList("green", "red", "blue").contains(colour))
                // 6 - group by user to discard the old value
                .groupByKey()
                // 7 - discard the old value
                .reduce((oldColour, newColour) -> newColour)
                // 8 - output new stream to continue process data
                .toStream()
                // 9 - select colour as new key (we discard the old key - user)
                .selectKey((user, colour) -> colour)
                // 10 - group by colour before aggregation
                .groupByKey()
                // 11 - <count> occurrences
                .count();
        // 12 - <to> in order to write the results back to kafka
        count.toStream().to(toTopic, Produced.with(Serdes.String(), Serdes.Long()));

        // build the topology and start our streams
        var streams = new KafkaStreams(builder.build(), this.createStreamsProperties());
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

    public void transformWithIntermediaryTopic(String fromTopic, String toTopic) {
        var builder = new StreamsBuilder();
        // 1 - get stream from kafka
        var input = builder.<String, String>stream(fromTopic);
        var mapping = input
                // 2 - filter bad data
                .filter((defaultKey, line) -> line.contains(","))
                // 3 - select user as key is string at left side of comma character (we discard the old key)
                .selectKey((ignoredKey, line) -> line.split(",")[0].trim().toLowerCase())
                // 4 - map values to get colour as value (string at right side of comma character)
                .mapValues(line -> line.split(",")[1].trim().toLowerCase())
                // 5 - filter keep only colour "green", "red", or "blue"
                .filter((user, colour) -> Arrays.asList("green", "red", "blue").contains(colour));

        var intermediaryTopic = fromTopic + "-mapped";
        mapping.to(intermediaryTopic);

        builder.table(intermediaryTopic);
        var count = mapping
                // 6 - select colour as new key (we discard the old key - user)
                .selectKey((user, colour) -> colour)
                // 7 - group by colour before aggregation
                .groupByKey()
                // 8 - <count> occurrences
                .count();
        // 9 - <to> in order to write the results back to kafka
        count.toStream().to(toTopic, Produced.with(Serdes.String(), Serdes.Long()));

        // build the topology and start our streams
        var streams = new KafkaStreams(builder.build(), this.createStreamsProperties());
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
