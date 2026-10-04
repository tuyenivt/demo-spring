package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConsumerApp {

    private final Logger log = LoggerFactory.getLogger(ConsumerApp.class);
    private final String bootstrapServer;
    private final String groupId;

    public ConsumerApp(String bootstrapServer, String groupId) {
        this.bootstrapServer = bootstrapServer;
        this.groupId = groupId;
    }

    private Properties createConsumerProperties() {
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServer);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, this.groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    public void consumeMessage(List<String> topics) {
        // create consumer
        var consumer = new KafkaConsumer<String, String>(this.createConsumerProperties());

        // subscribe consumer to our topic(s)
        consumer.subscribe(topics);

        // poll new data
        while (true) {
            var records = consumer.poll(Duration.ofMillis(100));
            for (var r : records) {
                log.info("Key: {}, Value: {}, Partition: {}, Offset: {}", r.key(), r.value(), r.partition(), r.offset());
            }
        }
    }

    public void consumeMessageWithThread(List<String> topics) {
        // latch for dealing with multiple thread
        var latch = new CountDownLatch(1);

        // create consumer runnable
        log.info("Creating the consumer thread");
        var consumerRunnable = new ConsumerRunnable(
                this.bootstrapServer,
                this.createConsumerProperties(),
                this.groupId,
                topics,
                latch);

        // start the thread
        var thread = new Thread(consumerRunnable);
        thread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Caught shutdown hook");
            (consumerRunnable).shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("Application has exited");
        }));

        // add a shutdown hook
        try {
            latch.await();
        } catch (InterruptedException e) {
            log.error("Application got interrupted: ", e);
        } finally {
            log.info("Application is closing");
        }
    }

    public class ConsumerRunnable implements Runnable {

        private final Logger log = LoggerFactory.getLogger(ConsumerRunnable.class);
        private final String bootstrapServer;
        private final String groupId;
        private final CountDownLatch latch;
        private KafkaConsumer<String, String> consumer;

        public ConsumerRunnable(String bootstrapServer,
                                Properties properties,
                                String groupId,
                                List<String> topics,
                                CountDownLatch latch) {
            this.bootstrapServer = bootstrapServer;
            this.groupId = groupId;
            this.latch = latch;
            // create consumer
            consumer = new KafkaConsumer<>(properties);
            // subscribe consumer to our topic(s)
            consumer.subscribe(topics);
        }

        @Override
        public void run() {
            // poll new data
            try {
                while (true) {
                    var records = consumer.poll(Duration.ofMillis(100));
                    for (var r : records) {
                        log.info("Key: {}, Value: {}, Partition: {}, Offset: {}", r.key(), r.value(), r.partition(), r.offset());
                    }
                }
            } catch (WakeupException e) {
                log.info("Received shutdown signal!");
            } finally {
                consumer.close();
                // tell our main code we're done with the consumer
                latch.countDown();
            }
        }

        public void shutdown() {
            // the wakeup() method is a special method to interrupt consumer.poll()
            // it will throw exception WakeupException
            consumer.wakeup();
        }
    }

    public void consumeMessageAssignAndSeek(String topic, long offsetToReadFrom, int numberOfMessagesToRead) {
        var properties = this.createConsumerProperties();
        // this don't need the group_id
        properties.remove(ConsumerConfig.GROUP_ID_CONFIG);
        // create consumer
        var consumer = new KafkaConsumer<String, String>(this.createConsumerProperties());

        // assign and seek are mostly used to replay data or fetch a specific message

        // assign
        var partitionToReadFrom = new TopicPartition(topic, 0);
        consumer.assign(List.of(partitionToReadFrom));

        // seek
        consumer.seek(partitionToReadFrom, offsetToReadFrom);

        var isKeepReading = true;
        var numberOfMessagesReadSoFar = 0;

        // poll new data
        while (isKeepReading) {
            var records = consumer.poll(Duration.ofMillis(100));
            for (var r : records) {
                log.info("Key: {}, Value: {}, Partition: {}, Offset: {}", r.key(), r.value(), r.partition(), r.offset());
                numberOfMessagesReadSoFar++;
                if (numberOfMessagesReadSoFar >= numberOfMessagesToRead) {
                    isKeepReading = false; // stop reading
                    break; // exist while loop
                }
            }
        }

        log.info("Exiting the application");
    }

    public void consumeMessageWithManualCommit(List<String> topics) {
        var properties = this.createConsumerProperties();
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // disable auto commit of offsets
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10"); // poll 10 records at a time
        // create consumer
        var consumer = new KafkaConsumer<String, String>(properties);

        // subscribe consumer to our topic(s)
        consumer.subscribe(topics);

        // poll new data
        while (true) {
            var records = consumer.poll(Duration.ofMillis(100));
            log.info("Received {} records", records.count());
            for (var r : records) {
                log.info("Key: {}, Value: {}, Partition: {}, Offset: {}", r.key(), r.value(), r.partition(), r.offset());
                try {
                    TimeUnit.MICROSECONDS.sleep(10L); // introduce a small delay
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.info("Committing offsets...");
            consumer.commitSync();
            log.info("Offsets have bean committed");
            try {
                TimeUnit.SECONDS.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
