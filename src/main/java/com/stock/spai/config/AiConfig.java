package com.stock.spai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        // 使用新版推薦的寫法：先蓋好 Options
        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
                //.model("gemini-1.5-flash-latest")
                .temperature(0.7)
                .build();

        // 注入到 Builder 裡面
        return builder
                .defaultOptions(options)
                .build();
    }
}