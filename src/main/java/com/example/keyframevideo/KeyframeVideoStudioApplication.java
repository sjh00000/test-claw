package com.example.keyframevideo;

import com.example.keyframevideo.config.GenerationProperties;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(GenerationProperties.class)
@MapperScan("com.example.keyframevideo.mapper")
public class KeyframeVideoStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeyframeVideoStudioApplication.class, args);
    }

    @Bean
    public ApplicationRunner proxyDiagnosticsRunner() {
        return args -> {
            // Java 进程不一定自动继承浏览器/VPN代理；启动时打印代理相关配置，便于判断 image-2 请求是否可能直连。
            log.info("JVM 代理配置，java.net.useSystemProxies={}, https.proxyHost={}, https.proxyPort={}, http.proxyHost={}, http.proxyPort={}",
                    System.getProperty("java.net.useSystemProxies"),
                    System.getProperty("https.proxyHost"),
                    System.getProperty("https.proxyPort"),
                    System.getProperty("http.proxyHost"),
                    System.getProperty("http.proxyPort"));
        };
    }
}
