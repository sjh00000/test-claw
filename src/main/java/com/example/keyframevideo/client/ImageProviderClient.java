package com.example.keyframevideo.client;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.keyframevideo.bo.ProviderConfigBO;
import com.example.keyframevideo.config.GenerationProperties;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.OssStorageService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
public class ImageProviderClient {

    private final GenerationProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final OssStorageService ossStorageService;
    private final ScheduledExecutorService imageCallMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "image-2-call-monitor");
        thread.setDaemon(true);
        return thread;
    });

    public ImageProviderClient(
            GenerationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            OssStorageService ossStorageService) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.ossStorageService = ossStorageService;
    }

    public String generate(String prompt, List<ReferenceImage> referenceImages, String imageSize, String imageQuality, ProviderConfigBO providerConfigBO) {
        if (CollectionUtil.isEmpty(referenceImages)) {
            // 无参考图时走纯文生图接口；有参考图时才走 edits multipart。
            return generateReferenceImage(prompt, imageSize, imageQuality, providerConfigBO);
        }
        return generateWithReferences(prompt, referenceImages, imageSize, imageQuality, providerConfigBO);
    }

    private String generateWithReferences(
            String prompt,
            List<ReferenceImage> referenceImages,
            String imageSize,
            String imageQuality,
            ProviderConfigBO providerConfigBO) {
        GenerationProperties.ImageProvider provider = resolveImageProvider(providerConfigBO);
        if (!isImageProviderConfigured(provider)) {
            // 厂商 base-url、api-key、model 任一缺失都不发起真实调用，避免带空模型或凭证请求外部服务。
            log.info("gpt-image2 未配置厂商参数，返回占位参考图编辑结果");
            return placeholderImage(prompt);
        }

        String imageUrl;
        Instant startedAt = Instant.now();
        ScheduledFuture<?> monitorFuture = startImageCallMonitor("gpt-image2 编辑接口", null, startedAt);
        try {
            String finalPrompt = buildPromptWithReferenceNames(prompt, referenceImages);
            MultipartBodyBuilder bodyBuilder = buildMultipartBody(provider, referenceImages, finalPrompt, imageSize, imageQuality);
            log.info("请求 image-2 编辑接口，endpoint={}, model={}, size={}, quality={}, actualPrompt={}, referenceImageCount={}, referenceImages={}",
                    provider.getEditEndpoint(), provider.getModel(), resolveSize(imageSize), resolveQuality(imageQuality),
                    finalPrompt, referenceImages.size(), summarizeReferenceImages(referenceImages));
            imageUrl = restClientBuilder
                    .baseUrl(provider.getBaseUrl())
                    .requestFactory(createImageRequestFactory())
                    .build()
                    .post()
                    .uri(provider.getEditEndpoint())
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(bodyBuilder.build())
                    .exchange((request, clientResponse) -> parseImageResponse(clientResponse, "gpt-image2 编辑接口", null));
        } catch (RestClientResponseException ex) {
            log.warn("gpt-image2 调用失败，httpStatus={}, responseBody={}",
                    ex.getStatusCode(), sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException("gpt-image2 调用失败", ex);
        } catch (BusinessException ex) {
            log.warn("gpt-image2 调用失败，reason={}", ex.getMessage());
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("gpt-image2 调用超时或网络不可达，timeoutSeconds={}, reason={}",
                    provider.getRequestTimeoutSeconds(), ex.getMessage());
            throw new BusinessException("image-2 调用超过 " + provider.getRequestTimeoutSeconds() + " 秒，请重新生成图片", ex);
        } catch (Exception ex) {
            log.warn("gpt-image2 调用失败，reason={}", ex.getMessage());
            throw new BusinessException("gpt-image2 调用失败", ex);
        } finally {
            stopImageCallMonitor(monitorFuture);
        }

        if (StrUtil.isBlank(imageUrl)) {
            log.warn("gpt-image2 响应缺少图片地址");
            throw new BusinessException("gpt-image2 响应中没有可用图片地址");
        }
        log.info("gpt-image2 调用成功，elapsedSeconds={}", elapsedSeconds(startedAt));
        return imageUrl;
    }

    public String generateReferenceImage(String prompt, String imageSize, String imageQuality, ProviderConfigBO providerConfigBO) {
        GenerationProperties.ImageProvider provider = resolveImageProvider(providerConfigBO);
        if (!isImageProviderConfigured(provider)) {
            // 厂商 base-url、api-key、model 任一缺失都不发起真实调用，保证前端可以先完成参考图流程联调。
            log.info("image-2 未配置厂商参数，返回占位主体参考图");
            return placeholderImage(prompt);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", provider.getModel());
        payload.put("prompt", prompt);
        payload.put("size", resolveSize(imageSize));
        payload.put("quality", resolveQuality(imageQuality));
        payload.put("n", 1);

        String imageUrl;
        Instant startedAt = Instant.now();
        ScheduledFuture<?> monitorFuture = startImageCallMonitor("image-2 生成接口", null, startedAt);
        try {
            log.info("请求 image-2 生成接口，endpoint={}, model={}, size={}, quality={}, actualPrompt={}",
                    provider.getGenerationEndpoint(), provider.getModel(), resolveSize(imageSize), resolveQuality(imageQuality), prompt);
            imageUrl = restClientBuilder
                    .baseUrl(provider.getBaseUrl())
                    .requestFactory(createImageRequestFactory())
                    .build()
                    .post()
                    .uri(provider.getGenerationEndpoint())
                    .header("Authorization", "Bearer " + provider.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, clientResponse) -> parseImageResponse(clientResponse, "image-2 生成接口", null));
        } catch (RestClientResponseException ex) {
            log.warn("image-2 主体参考图生成失败，httpStatus={}, responseBody={}",
                    ex.getStatusCode(), sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException("image-2 主体参考图生成失败", ex);
        } catch (BusinessException ex) {
            log.warn("image-2 主体参考图生成失败，reason={}", ex.getMessage());
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("image-2 主体参考图生成超时或网络不可达，timeoutSeconds={}, reason={}",
                    provider.getRequestTimeoutSeconds(), ex.getMessage());
            throw new BusinessException("image-2 调用超过 " + provider.getRequestTimeoutSeconds() + " 秒，请重新生成参考图", ex);
        } catch (Exception ex) {
            log.warn("image-2 主体参考图生成失败，reason={}", ex.getMessage());
            throw new BusinessException("image-2 主体参考图生成失败", ex);
        } finally {
            stopImageCallMonitor(monitorFuture);
        }

        if (StrUtil.isBlank(imageUrl)) {
            log.warn("image-2 主体参考图响应缺少图片地址");
            throw new BusinessException("image-2 响应中没有可用参考图地址");
        }
        log.info("image-2 主体参考图生成成功，elapsedSeconds={}", elapsedSeconds(startedAt));
        return imageUrl;
    }

    private ScheduledFuture<?> startImageCallMonitor(String scene, Integer frameIndex, Instant startedAt) {
        // image-2 是同步长耗时调用；心跳日志用于区分“仍在等待厂商响应”和“请求已经失败但前端未刷新”。
        return imageCallMonitor.scheduleAtFixedRate(() -> log.info("{} 调用等待中，frameIndex={}, elapsedSeconds={}",
                scene, frameIndex, elapsedSeconds(startedAt)), 10, 10, TimeUnit.SECONDS);
    }

    private void stopImageCallMonitor(ScheduledFuture<?> monitorFuture) {
        if (monitorFuture != null) {
            monitorFuture.cancel(false);
        }
    }

    private boolean isImageProviderConfigured(GenerationProperties.ImageProvider provider) {
        return StrUtil.isNotBlank(provider.getBaseUrl())
                && StrUtil.isNotBlank(provider.getApiKey())
                && StrUtil.isNotBlank(provider.getModel());
    }

    private GenerationProperties.ImageProvider resolveImageProvider(ProviderConfigBO providerConfigBO) {
        GenerationProperties.ImageProvider baseProvider = properties.getImageProvider();
        GenerationProperties.ImageProvider provider = new GenerationProperties.ImageProvider();
        provider.setBaseUrl(resolveConfigValue(providerConfigBO == null ? null : providerConfigBO.getBaseUrl(), baseProvider.getBaseUrl()));
        provider.setApiKey(resolveConfigValue(providerConfigBO == null ? null : providerConfigBO.getApiKey(), baseProvider.getApiKey()));
        provider.setModel(resolveConfigValue(providerConfigBO == null ? null : providerConfigBO.getModel(), baseProvider.getModel()));
        provider.setEditEndpoint(baseProvider.getEditEndpoint());
        provider.setGenerationEndpoint(baseProvider.getGenerationEndpoint());
        provider.setSize(baseProvider.getSize());
        provider.setQuality(baseProvider.getQuality());
        provider.setRequestTimeoutSeconds(baseProvider.getRequestTimeoutSeconds());
        return provider;
    }

    private String resolveConfigValue(String requestValue, String propertyValue) {
        return StrUtil.isNotBlank(requestValue) ? requestValue.trim() : propertyValue;
    }

    private long elapsedSeconds(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toSeconds();
    }

    private MultipartBodyBuilder buildMultipartBody(
            GenerationProperties.ImageProvider provider,
            List<ReferenceImage> referenceImages,
            String finalPrompt,
            String imageSize,
            String imageQuality) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("model", provider.getModel());
        bodyBuilder.part("prompt", finalPrompt);
        bodyBuilder.part("size", resolveSize(imageSize));
        bodyBuilder.part("quality", resolveQuality(imageQuality));

        // image2 edits 接口按 image[] 接收参考图；前端输入 URL，后端下载后转成 multipart 文件。
        for (ReferenceImage referenceImage : referenceImages) {
            String imageUrl = referenceImage.getImageUrl();
            byte[] imageBytes = downloadReferenceImage(imageUrl);
            String filename = sanitizeFilename(referenceImage.getName()) + ".png";
            bodyBuilder.part("image[]", new NamedByteArrayResource(imageBytes, filename))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "form-data; name=\"image[]\"; filename=\"" + filename + "\"")
                    .contentType(MediaType.IMAGE_PNG);
        }
        return bodyBuilder;
    }

    private byte[] downloadReferenceImage(String imageUrl) {
        try {
            if (imageUrl.startsWith("data:image/")) {
                byte[] imageBytes = decodeDataImage(imageUrl);
                log.info("参考图解析成功，byteSize={}", imageBytes.length);
                return imageBytes;
            }
            byte[] imageBytes = restClientBuilder
                    .requestFactory(createImageRequestFactory())
                    .build()
                    .get()
                    .uri(URI.create(imageUrl))
                    .retrieve()
                    .body(byte[].class);
            if (imageBytes == null || imageBytes.length == 0) {
                throw new BusinessException("参考图内容为空");
            }
            log.info("参考图下载成功，byteSize={}", imageBytes.length);
            return imageBytes;
        } catch (Exception ex) {
            log.warn("参考图下载失败，imageUrl={}, reason={}", imageUrl, ex.getMessage());
            throw new BusinessException("参考图下载失败：" + imageUrl, ex);
        }
    }

    private byte[] decodeDataImage(String dataImage) {
        int commaIndex = dataImage.indexOf(',');
        if (commaIndex < 0) {
            throw new BusinessException("参考图 base64 格式不正确");
        }
        String base64 = dataImage.substring(commaIndex + 1);
        return Base64.getDecoder().decode(base64.getBytes(StandardCharsets.UTF_8));
    }

    private String parseImageResponse(ClientHttpResponse clientResponse, String scene, Integer frameIndex) throws IOException {
        if (clientResponse.getStatusCode().isError()) {
            String responseBody = StreamUtils.copyToString(clientResponse.getBody(), StandardCharsets.UTF_8);
            String providerMessage = extractProviderErrorMessage(responseBody);
            log.warn("{} 调用失败，frameIndex={}, httpStatus={}, providerMessage={}, responseBody={}",
                    scene, frameIndex, clientResponse.getStatusCode(), providerMessage, sanitizeRawResponseForLog(responseBody));
            throw new BusinessException(scene + "调用失败：" + providerMessage);
        }
        try {
            // image-2 经常返回超长 b64_json；这里只流式扫描图片字段，避免把完整响应构造成 Map。
            String imageUrl = extractImageUrlFromStream(clientResponse, scene, frameIndex);
            log.info("{} 响应解析成功，frameIndex={}, hasImage={}", scene, frameIndex, StrUtil.isNotBlank(imageUrl));
            return imageUrl;
        } catch (Exception ex) {
            log.warn("{} 响应解析失败，frameIndex={}, httpStatus={}, reason={}",
                    scene, frameIndex, clientResponse.getStatusCode(), ex.getMessage());
            throw new BusinessException(scene + "响应解析失败", ex);
        }
    }

    private String extractImageUrlFromStream(ClientHttpResponse clientResponse, String scene, Integer frameIndex) throws IOException {
        try (var parser = objectMapper.getFactory().createParser(clientResponse.getBody())) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    continue;
                }
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (valueToken != JsonToken.VALUE_STRING) {
                    continue;
                }
                if ("url".equals(fieldName) || "image_url".equals(fieldName)) {
                    String url = parser.getValueAsString();
                    if (StrUtil.isNotBlank(url)) {
                        // 生产建议优先使用 data[0].url，拿到 URL 后立即返回，避免继续扫描后续大字段。
                        log.info("{} 响应命中图片 URL，frameIndex={}", scene, frameIndex);
                        return url;
                    }
                }
                if ("b64_json".equals(fieldName)) {
                    // 厂商若返回 base64，立即上传 OSS 并返回访问 URL，任务表不再保存大体积 base64。
                    return convertB64JsonToImageUrl(parser, scene, frameIndex);
                }
            }
        }
        return null;
    }

    private String placeholderImage(String prompt) {
        return "https://placehold.co/1024x1024/png?text=" + prompt.replace(" ", "+");
    }

    private String convertB64JsonToImageUrl(com.fasterxml.jackson.core.JsonParser parser, String scene, Integer frameIndex) throws IOException {
        String b64Json = parser.getValueAsString();
        log.info("{} 响应命中 b64_json，frameIndex={}, outputMode=base64, base64Length={}",
                scene, frameIndex, b64Json.length());
        try {
            // 这里不落本地文件，直接把内存中的 PNG 字节交给 OSS 服务，避免服务器磁盘产生临时文件。
            byte[] imageBytes = Base64.getDecoder().decode(b64Json.getBytes(StandardCharsets.UTF_8));
            return ossStorageService.uploadGeneratedImage(imageBytes);
        } catch (IllegalArgumentException ex) {
            log.warn("{} base64 图片解码失败，frameIndex={}, base64Length={}, reason={}",
                    scene, frameIndex, b64Json.length(), ex.getMessage());
            throw new BusinessException(scene + "返回的 base64 图片格式不正确", ex);
        }
    }

    private String buildPromptWithReferenceNames(String prompt, List<ReferenceImage> referenceImages) {
        String referenceGuide = referenceImages.stream()
                .map(referenceImage -> "- " + referenceImage.getName() + "：对应上传文件 " + sanitizeFilename(referenceImage.getName()) + ".png")
                .collect(Collectors.joining("\n"));
        // 参考图名称是给模型理解多参考图对应关系的提示，不要求生成到画面里。
        return prompt
                + "\n\n参考图对应关系：\n"
                + referenceGuide
                + "\n请根据以上名称区分不同参考图中的角色/主体，但不要在画面中生成这些名称文字、字幕、姓名牌或标题。";
    }

    private List<Map<String, Object>> summarizeReferenceImages(List<ReferenceImage> referenceImages) {
        List<Map<String, Object>> summary = new ArrayList<>();
        referenceImages.forEach(referenceImage -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", referenceImage.getName());
            item.put("image", sanitizeImageForLog(referenceImage.getImageUrl()));
            summary.add(item);
        });
        return summary;
    }

    private String sanitizeFilename(String filename) {
        String value = StrUtil.isNotBlank(filename) ? filename.trim() : "reference";
        return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
    }

    private String resolveSize(String imageSize) {
        return StrUtil.isNotBlank(imageSize) ? imageSize : properties.getImageProvider().getSize();
    }

    private String resolveQuality(String imageQuality) {
        return StrUtil.isNotBlank(imageQuality) ? imageQuality : properties.getImageProvider().getQuality();
    }

    private JdkClientHttpRequestFactory createImageRequestFactory() {
        int timeoutSeconds = properties.getImageProvider().getRequestTimeoutSeconds();
        HttpClient httpClient = HttpClient.newBuilder()
                // image-2 图片生成较慢，单次调用最多等待配置时间，默认 300 秒，超时后对应帧可重新生成。
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return requestFactory;
    }

    private String sanitizeImageForLog(String imageUrl) {
        if (StrUtil.isBlank(imageUrl)) {
            return "";
        }
        if (imageUrl.startsWith("data:image/")) {
            int commaIndex = imageUrl.indexOf(',');
            String prefix = commaIndex > 0 ? imageUrl.substring(0, commaIndex) : "data:image";
            return prefix + ",<base64 length=" + imageUrl.length() + ">";
        }
        return imageUrl.length() > 180 ? imageUrl.substring(0, 180) + "..." : imageUrl;
    }

    private Object sanitizeResponseForLog(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object itemValue = entry.getValue();
                // 图片 base64 字段很大且敏感，只记录长度。
                if ("b64_json".equals(key) && itemValue instanceof String text) {
                    sanitized.put(key, "<base64 length=" + text.length() + ">");
                } else if ("url".equals(key) && itemValue instanceof String text) {
                    sanitized.put(key, sanitizeImageForLog(text));
                } else {
                    sanitized.put(key, sanitizeResponseForLog(itemValue));
                }
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeResponseForLog).toList();
        }
        if (value instanceof String text && text.startsWith("data:image/")) {
            return sanitizeImageForLog(text);
        }
        return value;
    }

    private String sanitizeRawResponseForLog(String rawResponse) {
        String compact = rawResponse.replaceAll("\\s+", " ");
        return compact.length() > 10000 ? compact.substring(0, 10000) + "..." : compact;
    }

    private String extractProviderErrorMessage(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            return "厂商响应为空";
        }
        try {
            Map<?, ?> response = objectMapper.readValue(responseBody, Map.class);
            Object error = response.get("error");
            if (error instanceof Map<?, ?> errorMap) {
                String message = stringValue(errorMap.get("message"));
                String code = stringValue(errorMap.get("code"));
                return StrUtil.isNotBlank(code) ? code + "：" + message : message;
            }
            String message = stringValue(response.get("message"));
            if (StrUtil.isNotBlank(message)) {
                return message;
            }
        } catch (Exception ex) {
            log.warn("image-2 错误响应解析失败，reason={}", ex.getMessage());
        }
        return sanitizeRawResponseForLog(responseBody);
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
