package com.example.springaialibabademo.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;

@Configuration
public class ChatClientConfig {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个专业、友好、简洁的 AI 助手。
            请优先使用中文回答，并在需要时给出清晰的步骤和示例。
            回答应尽量聚焦问题本身，避免不必要的冗长展开。
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, DemoAiProperties properties) {
        String systemPrompt = StringUtils.hasText(properties.systemPrompt())
                ? properties.systemPrompt()
                : DEFAULT_SYSTEM_PROMPT;

        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(properties.model())
                .temperature(properties.temperature())
                .topP(properties.topP())
                .build();

        return builder
                .defaultSystem(systemPrompt)
                .defaultOptions(options)
                .build();
    }
}
