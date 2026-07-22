package com.example.keyframevideo.client;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.keyframevideo.bo.ProviderConfigBO;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class SeedanceClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public SeedanceClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public String submit(
            String prompt,
            List<ReferenceImage> referenceImages,
            int duration,
            String resolution,
            String ratio,
            boolean generateAudio,
            ProviderConfigBO providerConfigBO) {
        ProviderConfigBO seedance = resolveSeedance(providerConfigBO);
        if (!isSeedanceConfigured(seedance)) {
            // Seedance base-url、api-key、model 任一缺失时使用 mock task，避免带空模型或凭证请求外部服务。
            log.info("Seedance 未配置厂商参数，使用 mock task");
            return "mock-task-" + System.currentTimeMillis();
        }

        // 按 Seedance 2.0 文档组装请求：prompt 必须非空，真实提示词放在 metadata.content 文本项。
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", seedance.getModel());
        payload.put("prompt", prompt);

        Map<String, Object> metadata = new HashMap<>();
        List<Map<String, Object>> content = buildContent(prompt, referenceImages);
        metadata.put("content", content);
        metadata.put("duration", duration);
        metadata.put("resolution", resolution);
        metadata.put("ratio", ratio);
        metadata.put("generate_audio", generateAudio);
        payload.put("metadata", metadata);
        log.info("Seedance 提交请求参数，model={}, duration={}, resolution={}, ratio={}, generateAudio={}, referenceImageCount={}, actualContent={}",
                payload.get("model"), duration, resolution, ratio, generateAudio,
                referenceImages == null ? 0 : referenceImages.size(), toJsonForLog(summarizeContentForLog(content)));

        Map<?, ?> response;
        try {
            String rawResponse = restClient(seedance)
                    .post()
                    .uri("/v1/video/generations")
                    .header("Authorization", "Bearer " + seedance.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            response = parseSeedanceResponse(rawResponse, "Seedance 任务提交", null);
        } catch (RestClientResponseException ex) {
            String providerMessage = extractProviderErrorMessage(ex.getResponseBodyAsString());
            log.warn("Seedance 任务提交调用失败，httpStatus={}, providerMessage={}, responseBody={}",
                    ex.getStatusCode(), providerMessage, sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException(providerMessage, ex);
        } catch (Exception ex) {
            log.warn("Seedance 任务提交调用失败，reason={}", ex.getMessage());
            throw new BusinessException("seedance 任务提交失败", ex);
        }

        String taskId = findString(response, "task_id", "taskId", "id");
        if (StrUtil.isBlank(taskId)) {
            log.warn("Seedance 提交响应缺少 task_id");
            throw new BusinessException("seedance 响应中没有 task_id");
        }
        log.info("Seedance 任务提交调用成功，taskId={}", taskId);
        return taskId;
    }

    public SeedanceTaskStatus query(String taskId, ProviderConfigBO providerConfigBO) {
        ProviderConfigBO seedance = resolveSeedance(providerConfigBO);
        SeedanceTaskStatus status = new SeedanceTaskStatus();
        status.setTaskId(taskId);

        if (!isSeedanceConfigured(seedance)) {
            // 本地 mock 直接返回成功，前端可完整看到最终态。
            log.info("Seedance 未配置厂商参数，返回 mock 成功状态，taskId={}", taskId);
            status.setStatus("SUCCEEDED");
            status.setVideoUrl("https://example.com/mock-seedance-video.mp4");
            return status;
        }

        Map<?, ?> response;
        try {
            String rawResponse = restClient(seedance)
                    .get()
                    .uri("/v1/video/generations/{taskId}", taskId)
                    .header("Authorization", "Bearer " + seedance.getApiKey())
                    .retrieve()
                    .body(String.class);
            response = parseSeedanceResponse(rawResponse, "Seedance 任务查询", taskId);
        } catch (RestClientResponseException ex) {
            String providerMessage = extractProviderErrorMessage(ex.getResponseBodyAsString());
            log.warn("Seedance 任务查询调用失败，taskId={}, httpStatus={}, providerMessage={}, responseBody={}",
                    taskId, ex.getStatusCode(), providerMessage, sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException(providerMessage, ex);
        } catch (Exception ex) {
            log.warn("Seedance 任务查询调用失败，taskId={}, reason={}", taskId, ex.getMessage());
            throw new BusinessException("seedance 任务查询失败", ex);
        }

        Map<?, ?> task = response;
        if (response != null && response.get("data") instanceof Map<?, ?> data) {
            task = data;
        }

        status.setStatus(findString(task, "status"));
        status.setProgress(findString(task, "progress"));
        status.setVideoUrl(extractVideoUrl(task));
        status.setFailReason(findString(task, "fail_reason", "failReason", "error", "message"));
        log.info("Seedance 任务查询调用成功，taskId={}, providerStatus={}, videoUrl={}",
                taskId, status.getStatus(), sanitizeImageForLog(status.getVideoUrl()));
        return status;
    }

    public void cancel(String taskId, ProviderConfigBO providerConfigBO) {
        ProviderConfigBO seedance = resolveSeedance(providerConfigBO);
        if (StrUtil.isBlank(taskId)) {
            throw new BusinessException("Seedance 任务 ID 不能为空");
        }
        if (!isSeedanceConfigured(seedance)) {
            // 本地 mock 模式只记录取消动作，不调用外部厂商。
            log.info("Seedance 未配置厂商参数，跳过 mock 任务取消，taskId={}", taskId);
            return;
        }
        try {
            String rawResponse = restClient(seedance)
                    .delete()
                    .uri("/v1/videos/{taskId}", taskId)
                    .header("Authorization", "Bearer " + seedance.getApiKey())
                    .retrieve()
                    .body(String.class);
            log.info("Seedance 任务取消成功，taskId={}, response={}", taskId, sanitizeRawResponseForLog(rawResponse));
        } catch (RestClientResponseException ex) {
            String providerMessage = extractProviderErrorMessage(ex.getResponseBodyAsString());
            log.warn("Seedance 任务取消失败，taskId={}, httpStatus={}, providerMessage={}, responseBody={}",
                    taskId, ex.getStatusCode(), providerMessage, sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException(providerMessage, ex);
        } catch (Exception ex) {
            log.warn("Seedance 任务取消失败，taskId={}, reason={}", taskId, ex.getMessage());
            throw new BusinessException("seedance 任务取消失败", ex);
        }
    }

    private List<Map<String, Object>> buildContent(String prompt, List<ReferenceImage> referenceImages) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildVideoInstructionText(prompt, referenceImages)));

        List<ReferenceImage> safeReferenceImages = referenceImages == null ? List.of() : referenceImages;
        for (int index = 0; index < safeReferenceImages.size(); index++) {
            ReferenceImage referenceImage = safeReferenceImages.get(index);
            if (StrUtil.isBlank(referenceImage.getImageUrl())) {
                continue;
            }
            // 每张主体参考图前追加文本说明，让 Seedance 知道该图对应哪个角色/主体及其视觉特征。
            content.add(Map.of("type", "text", "text", buildReferenceImageText(referenceImage, index)));
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", referenceImage.getImageUrl()),
                    "role", "reference_image"
            ));
        }
        return content;
    }

    private String buildVideoInstructionText(String prompt, List<ReferenceImage> referenceImages) {
        List<ReferenceImage> safeReferenceImages = referenceImages == null ? List.of() : referenceImages;
        StringBuilder builder = new StringBuilder();
        builder.append("视频整体描述：\n")
                .append(prompt)
                .append("\n\n生成规则：\n")
                .append("1. 请按视频整体描述生成完整镜头，不要生成水印、Logo 或无关字幕。\n")
                .append("2. 如果视频描述需要旁白或对白，请将其作为声音/叙事节奏处理；除非明确要求，不要生成大段屏幕文字。\n");
        if (CollectionUtil.isNotEmpty(safeReferenceImages)) {
            builder.append("3. 参考图用于保持主体、角色或风格一致；参考图名称只用于理解图片对应关系，不要显示在视频画面中。\n");
            builder.append("\n主体参考图对应关系：\n");
            for (int index = 0; index < safeReferenceImages.size(); index++) {
                ReferenceImage referenceImage = safeReferenceImages.get(index);
                builder.append(index + 1)
                        .append(". ")
                        .append(resolveReferenceImageName(referenceImage, index));
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String buildReferenceImageText(ReferenceImage referenceImage, int index) {
        StringBuilder builder = new StringBuilder();
        builder.append("下面这张图片是主体参考图 ")
                .append(index + 1)
                .append("，内部标签为“")
                .append(resolveReferenceImageName(referenceImage, index))
                .append("”。");
        builder.append("请把它作为对应角色/主体的身份参考图使用，但不要把内部标签长期显示在视频画面中。");
        return builder.toString();
    }

    private String resolveReferenceImageName(ReferenceImage referenceImage, int index) {
        if (StrUtil.isNotBlank(referenceImage.getName())) {
            return referenceImage.getName();
        }
        return "参考图" + (index + 1);
    }

    private String extractVideoUrl(Map<?, ?> task) {
        return findVideoUrlDeep(task);
    }

    private String findVideoUrlDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            String directUrl = findString(map,
                    "video_url", "videoUrl", "url", "download_url", "downloadUrl",
                    "result_url", "resultUrl", "output_url", "outputUrl");
            if (StrUtil.isNotBlank(directUrl)) {
                return directUrl;
            }
            for (Object nestedValue : map.values()) {
                String nestedUrl = findVideoUrlDeep(nestedValue);
                if (StrUtil.isNotBlank(nestedUrl)) {
                    return nestedUrl;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String nestedUrl = findVideoUrlDeep(item);
                if (StrUtil.isNotBlank(nestedUrl)) {
                    return nestedUrl;
                }
            }
        }
        return null;
    }

    private RestClient restClient(ProviderConfigBO seedance) {
        return restClientBuilder.baseUrl(seedance.getBaseUrl()).build();
    }

    private boolean isSeedanceConfigured(ProviderConfigBO seedance) {
        return StrUtil.isNotBlank(seedance.getBaseUrl())
                && StrUtil.isNotBlank(seedance.getApiKey())
                && StrUtil.isNotBlank(seedance.getModel());
    }

    private ProviderConfigBO resolveSeedance(ProviderConfigBO providerConfigBO) {
        ProviderConfigBO seedance = new ProviderConfigBO();
        // Seedance 核心参数只允许来自管理员后台模型配置，避免部署配置覆盖数据库里的模型策略。
        seedance.setBaseUrl(trimToEmpty(providerConfigBO == null ? null : providerConfigBO.getBaseUrl()));
        seedance.setApiKey(trimToEmpty(providerConfigBO == null ? null : providerConfigBO.getApiKey()));
        seedance.setModel(trimToEmpty(providerConfigBO == null ? null : providerConfigBO.getModel()));
        return seedance;
    }

    private String trimToEmpty(String value) {
        return StrUtil.isNotBlank(value) ? value.trim() : "";
    }

    private Map<?, ?> parseSeedanceResponse(String rawResponse, String scene, String taskId) {
        if (StrUtil.isBlank(rawResponse)) {
            log.warn("{} 响应为空，taskId={}", scene, taskId);
            throw new BusinessException(scene + "响应为空");
        }
        try {
            Map<String, Object> response = objectMapper.readValue(rawResponse, new TypeReference<>() {
            });
            // 打印厂商响应的脱敏完整结构，方便排查状态字段和视频地址字段映射。
            log.info("{} 响应，taskId={}, response={}",
                    scene, taskId, toJsonForLog(sanitizeResponseForLog(response)));
            return response;
        } catch (Exception ex) {
            log.warn("{} 响应解析失败，taskId={}, rawResponse={}, reason={}",
                    scene, taskId, sanitizeRawResponseForLog(rawResponse), ex.getMessage());
            throw new BusinessException(scene + "响应解析失败", ex);
        }
    }

    private List<Map<String, Object>> summarizeContentForLog(List<?> content) {
        List<Map<String, Object>> summary = new ArrayList<>();
        for (Object item : content) {
            if (item instanceof Map<?, ?> map) {
                Object type = map.get("type");
                Map<String, Object> entry = new HashMap<>();
                entry.put("type", type);
                if ("text".equals(type)) {
                    entry.put("text", map.get("text"));
                } else if ("image_url".equals(type)) {
                    entry.put("role", map.get("role"));
                    entry.put("image", sanitizeImageForLog(extractNestedUrl(map, "image_url")));
                } else {
                    entry.put("role", map.get("role"));
                }
                summary.add(entry);
            }
        }
        return summary;
    }

    private String extractNestedUrl(Map<?, ?> map, String key) {
        Object nested = map.get(key);
        if (nested instanceof Map<?, ?> nestedMap && nestedMap.get("url") instanceof String url) {
            return url;
        }
        return null;
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
                if ("b64_json".equals(key) && itemValue instanceof String text) {
                    sanitized.put(key, "<base64 length=" + text.length() + ">");
                } else if (("url".equals(key) || "video_url".equals(key) || "videoUrl".equals(key))
                        && itemValue instanceof String text) {
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
        return compact.length() > 3000 ? compact.substring(0, 3000) + "..." : compact;
    }

    private String toJsonForLog(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String extractProviderErrorMessage(String responseBody) {
        String fallback = "seedance 任务提交失败";
        if (StrUtil.isBlank(responseBody)) {
            return fallback;
        }
        String current = responseBody;
        for (int i = 0; i < 4; i++) {
            try {
                Map<String, Object> errorMap = objectMapper.readValue(current, new TypeReference<>() {
                });
                Object error = errorMap.get("error");
                if (error instanceof Map<?, ?> nestedError) {
                    String code = findString(nestedError, "code");
                    String message = findString(nestedError, "message");
                    if (StrUtil.isNotBlank(message)) {
                        return buildReadableProviderMessage(code, message);
                    }
                }
                Object message = errorMap.get("message");
                if (message instanceof String text && StrUtil.isNotBlank(text)) {
                    current = text;
                    continue;
                }
                Object code = errorMap.get("code");
                if (code instanceof String text && StrUtil.isNotBlank(text)) {
                    return buildReadableProviderMessage(text, fallback);
                }
                break;
            } catch (Exception ignored) {
                break;
            }
        }
        return fallback;
    }

    private String buildReadableProviderMessage(String code, String message) {
        if ("InputImageSensitiveContentDetected.PrivacyInformation".equals(code)) {
            return "Seedance 拒绝输入图片：图片可能包含真实人物或隐私信息。请改用更虚构化、非真人照片感的角色参考图后重试";
        }
        return StrUtil.isNotBlank(code) ? code + "：" + message : message;
    }

    private String findString(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof String text) {
                return text;
            }
        }
        return null;
    }
}
