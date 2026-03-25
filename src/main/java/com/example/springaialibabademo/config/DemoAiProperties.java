package com.example.springaialibabademo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo.ai")
public record DemoAiProperties(
        String model,
        Double temperature,
        Double topP,
        String systemPrompt
) {
}
