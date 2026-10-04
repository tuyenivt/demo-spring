package com.example.kafka;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BankTransactionStreamsAppTests {

    private static final String INPUT_TOPIC = "streams-bank-transaction-input";
    private static final String OUTPUT_TOPIC = "streams-bank-transaction-output";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setup() {
        var config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        var app = new BankTransactionStreamsApp("dummy:1234");
        testDriver = new TopologyTestDriver(app.createTopology(INPUT_TOPIC, OUTPUT_TOPIC), config);
        // Same wire format as BankTransactionProducer: String key, JSON string value
        inputTopic = testDriver.createInputTopic(INPUT_TOPIC, Serdes.String().serializer(), Serdes.String().serializer());
        outputTopic = testDriver.createOutputTopic(OUTPUT_TOPIC, Serdes.String().deserializer(), Serdes.String().deserializer());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void aggregatesBalancePerCustomer() {
        inputTopic.pipeInput("john", """
                {"name":"john","amount":10,"time":"2026-01-01T10:00:00Z"}""");
        inputTopic.pipeInput("john", """
                {"name":"john","amount":25,"time":"2026-01-01T09:00:00Z"}""");
        inputTopic.pipeInput("alice", """
                {"name":"alice","amount":7,"time":"2026-01-02T08:00:00Z"}""");

        var balances = outputTopic.readKeyValuesToMap();

        var john = jsonMapper.readTree(balances.get("john"));
        assertEquals(2, john.get("count").asInt());
        assertEquals(35, john.get("balance").asInt());
        // Latest transaction time wins, even when events arrive out of order
        assertEquals("2026-01-01T10:00:00Z", john.get("time").asString());

        var alice = jsonMapper.readTree(balances.get("alice"));
        assertEquals(1, alice.get("count").asInt());
        assertEquals(7, alice.get("balance").asInt());
    }
}
