package com.example.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class AiConfiguration {

    @Value("classpath:prompts/system-prompt.st")
    private Resource systemPromptResource;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore, ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem(systemPromptResource)
                .build();
    }
}
