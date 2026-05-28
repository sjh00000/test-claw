package com.example.keyframevideo;

import com.example.keyframevideo.config.GenerationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GenerationProperties.class)
public class KeyframeVideoStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeyframeVideoStudioApplication.class, args);
    }
}
