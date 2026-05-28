package com.example.keyframevideo.config;

import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebCorsConfig implements WebMvcConfigurer {

    private final GenerationProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 暴露模型 b64_json 落盘后的图片，前端预览和 Seedance 都通过 URL 访问。
        String urlPrefix = properties.getAssets().getImageUrlPrefix();
        Path imageDir = Path.of(properties.getAssets().getImageDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(imageDir.toUri().toString());
    }
}
