package com.example.keyframevideo.service;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.config.GenerationProperties;
import com.example.keyframevideo.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedImageStorageService {

    private final GenerationProperties properties;

    public String saveBase64Png(String b64Json) {
        if (StrUtil.isBlank(b64Json)) {
            throw new BusinessException("图片 base64 内容为空");
        }
        try {
            return saveBase64Png(writer -> writer.write(b64Json));
        } catch (IllegalArgumentException ex) {
            log.warn("b64_json 解码失败，reason={}", ex.getMessage());
            throw new BusinessException("图片 base64 解码失败", ex);
        }
    }

    public String saveBase64Png(Base64ContentWriter base64ContentWriter) {
        Path tempBase64Path = null;
        try {
            String filename = UUID.randomUUID() + ".png";
            Path imageDir = Path.of(properties.getAssets().getImageDir()).toAbsolutePath().normalize();
            Files.createDirectories(imageDir);
            Path imagePath = imageDir.resolve(filename).normalize();
            tempBase64Path = Files.createTempFile(imageDir, filename, ".b64.tmp");
            // b64_json 可能非常大，先把 JSON 字符串值流式写入临时文件，避免在内存里构造完整响应对象。
            try (Writer writer = Files.newBufferedWriter(tempBase64Path, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                base64ContentWriter.writeTo(writer);
            }
            // 再通过 Base64 解码流写出 PNG，避免额外创建完整图片 byte[] 副本。
            try (var base64InputStream = Base64.getDecoder().wrap(Files.newInputStream(tempBase64Path));
                 var outputStream = Files.newOutputStream(imagePath)) {
                base64InputStream.transferTo(outputStream);
            }
            long byteSize = Files.size(imagePath);
            String imageUrl = buildPublicImageUrl(filename);
            log.info("b64_json 图片流式落盘成功，filename={}, byteSize={}, imageUrl={}", filename, byteSize, imageUrl);
            return imageUrl;
        } catch (IllegalArgumentException ex) {
            log.warn("b64_json 解码失败，reason={}", ex.getMessage());
            throw new BusinessException("图片 base64 解码失败", ex);
        } catch (IOException ex) {
            log.warn("b64_json 图片流式落盘失败，reason={}", ex.getMessage());
            throw new BusinessException("图片保存失败", ex);
        } finally {
            if (tempBase64Path != null) {
                try {
                    Files.deleteIfExists(tempBase64Path);
                } catch (IOException ex) {
                    log.warn("b64_json 临时文件删除失败，path={}, reason={}", tempBase64Path, ex.getMessage());
                }
            }
        }
    }

    private String buildPublicImageUrl(String filename) {
        String urlPrefix = properties.getAssets().getImageUrlPrefix();
        String normalizedPrefix = urlPrefix.startsWith("/") ? urlPrefix : "/" + urlPrefix;
        String publicBaseUrl = properties.getAssets().getPublicBaseUrl();
        if (StrUtil.isNotBlank(publicBaseUrl)) {
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

    @FunctionalInterface
    public interface Base64ContentWriter {

        // 将 b64_json 字符串内容写入目标 Writer，调用方可用 Jackson 流式输出，避免创建大字符串。
        void writeTo(Writer writer) throws IOException;
    }
}
