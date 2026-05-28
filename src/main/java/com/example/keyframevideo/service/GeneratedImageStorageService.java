package com.example.keyframevideo.service;

import com.example.keyframevideo.config.GenerationProperties;
import com.example.keyframevideo.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedImageStorageService {

    private final GenerationProperties properties;

    public String saveBase64Png(String b64Json) {
        if (!StringUtils.hasText(b64Json)) {
            throw new BusinessException("图片 base64 内容为空");
        }
        try {
            byte[] imageBytes = Base64.getDecoder().decode(b64Json.getBytes(StandardCharsets.UTF_8));
            String filename = UUID.randomUUID() + ".png";
            Path imageDir = Path.of(properties.getAssets().getImageDir()).toAbsolutePath().normalize();
            Files.createDirectories(imageDir);
            Path imagePath = imageDir.resolve(filename).normalize();
            // b64_json 来自模型响应，统一落成本地 png 文件，再返回可访问 URL。
            Files.write(imagePath, imageBytes);
            String imageUrl = buildPublicImageUrl(filename);
            log.info("b64_json 图片落盘成功，filename={}, byteSize={}, imageUrl={}", filename, imageBytes.length, imageUrl);
            return imageUrl;
        } catch (IllegalArgumentException ex) {
            log.warn("b64_json 解码失败，reason={}", ex.getMessage());
            throw new BusinessException("图片 base64 解码失败", ex);
        } catch (IOException ex) {
            log.warn("b64_json 图片落盘失败，reason={}", ex.getMessage());
            throw new BusinessException("图片保存失败", ex);
        }
    }

    private String buildPublicImageUrl(String filename) {
        String urlPrefix = properties.getAssets().getImageUrlPrefix();
        String normalizedPrefix = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        String publicBaseUrl = properties.getAssets().getPublicBaseUrl();
        if (StringUtils.hasText(publicBaseUrl)) {
            return publicBaseUrl.replaceAll("/+$", "") + normalizedPrefix + "/" + filename;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            return baseUrl + normalizedPrefix + "/" + filename;
        }
        // 非 Web 请求线程兜底，保证仍返回完整 URL。
        return "http://localhost:8080" + normalizedPrefix + "/" + filename;
    }
}
