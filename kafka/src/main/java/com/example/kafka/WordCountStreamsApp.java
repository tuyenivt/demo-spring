package com.example.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Produced;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class WordCountStreamsApp {

    private static final String DEFAULT_BOOTSTRAP_SERVER = "localhost:9092";
    private static final String APPLICATION_ID = "wordcount-streams-app";
    private static final String SHUTDOWN_HOOK_THREAD_NAME = "wordcount-streams-shutdown-hook";
    private final String bootstrapServer;

    public WordCountStreamsApp() {
        this(DEFAULT_BOOTSTRAP_SERVER);
    }

    public WordCountStreamsApp(String bootstrapServer) {
        this.bootstrapServer = bootstrapServer;
    }

    private Properties createStreamsProperties() {
        Properties properties = new Properties();
        properties.setProperty(StreamsConfig.APPLICATION_ID_CONFIG, APPLICATION_ID);
        properties.setProperty(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServer);
        properties.setProperty(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        properties.setProperty(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public Topology createTopology(String fromTopic, String toTopic) {
        StreamsBuilder builder = new StreamsBuilder();
        // 1 - <stream> from kafka
        KStream<String, String> wordCountInput = builder.stream(fromTopic);
        KTable<String, Long> count = wordCountInput
                // 2 - <map values> to lowercase
                .mapValues(line -> line.toLowerCase())
                // 3 - <flatmap values> split by space
                .flatMapValues(line -> Arrays.asList(line.split(" ")))
                // 4 - <select key> to apply a key (we discard the old key)
                .selectKey((ignoredKey, word) -> word)
                // 5 - <group by key> before aggregation
                .groupByKey()
                // 6 - <count> occurrences
                .count();
        // 7 - <to> in order to write the results back to kafka
        count.toStream().to(toTopic, Produced.with(Serdes.String(), Serdes.Long()));
        return builder.build();
    }

    public static void main(String[] args) {
        var inputTopic = "streams-plaintext-input";
        var outputTopic = "streams-wordcount-output";
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
        var app = new WordCountStreamsApp(bootstrapServer);
        // build the topology and start our streams
        var streams = new KafkaStreams(app.createTopology(inputTopic, outputTopic), app.createStreamsProperties());
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
