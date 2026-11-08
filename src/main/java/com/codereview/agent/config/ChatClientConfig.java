package com.codereview.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties(ReviewProperties.class)
public class ChatClientConfig {

    /** The auto-configured ChatClient.Builder is prototype-scoped, so each bean gets its own. */
    @Bean
    ChatClient reviewChatClient(ChatClient.Builder builder,
            @Value("classpath:/prompts/system-code-review.st") Resource systemPrompt) throws IOException {
        return builder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .build();
    }
}