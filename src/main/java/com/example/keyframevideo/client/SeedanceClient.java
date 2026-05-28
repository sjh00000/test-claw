package com.example.keyframevideo.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.keyframevideo.config.GenerationProperties;
import com.example.keyframevideo.domain.GenerationSession;
import com.example.keyframevideo.domain.KeyframeResult;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class SeedanceClient {

    private final GenerationProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public SeedanceClient(
            GenerationProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    public String submit(GenerationSession session) {
        GenerationProperties.Seedance seedance = properties.getSeedance();
        if (!StringUtils.hasText(seedance.getBaseUrl()) || !StringUtils.hasText(seedance.getApiKey())) {
            // 未配置 Seedance 时使用 mock task，避免本地开发被外部凭证阻断。
            log.info("Seedance 未配置厂商参数，使用 mock task，sessionId={}", session.getId());
            return "mock-task-" + session.getId();
        }

        // 按 Seedance 2.0 文档组装请求：prompt 必须非空，真实提示词放在 metadata.content 文本项。
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", session.isFastMode() ? seedance.getFastModel() : seedance.getModel());
        payload.put("prompt", "关键帧生成视频");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("content", buildContent(session));
        metadata.put("duration", session.getDuration());
        metadata.put("resolution", session.getResolution());
        metadata.put("ratio", session.getRatio());
        metadata.put("generate_audio", session.isGenerateAudio());
        payload.put("metadata", metadata);
        log.info("Seedance 提交请求参数={}", JsonParser.toJson(sanitizeResponseForLog(payload)));

        Map<?, ?> response;
        try {
            String rawResponse = restClient()
                    .post()
                    .uri("/v1/video/generations")
                    .header("Authorization", "Bearer " + seedance.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            response = parseSeedanceResponse(rawResponse, "Seedance 任务提交", session.getId(), null);
        } catch (RestClientResponseException ex) {
            String providerMessage = extractProviderErrorMessage(ex.getResponseBodyAsString());
            log.warn("Seedance 任务提交调用失败，sessionId={}, httpStatus={}, providerMessage={}, responseBody={}",
                    session.getId(), ex.getStatusCode(), providerMessage, sanitizeRawResponseForLog(ex.getResponseBodyAsString()));
            throw new BusinessException(providerMessage, ex);
        } catch (Exception ex) {
            log.warn("Seedance 任务提交调用失败，sessionId={}, reason={}", session.getId(), ex.getMessage());
            throw new BusinessException("seedance 任务提交失败", ex);
        }

        String taskId = findString(response, "task_id", "taskId", "id");
        if (!StringUtils.hasText(taskId)) {
            log.warn("Seedance 提交响应缺少 task_id，sessionId={}", session.getId());
            throw new BusinessException("seedance 响应中没有 task_id");
        }
        log.info("Seedance 任务提交调用成功，sessionId={}, taskId={}", session.getId(), taskId);
        return taskId;
    }

    public SeedanceTaskStatus query(String taskId) {
        GenerationProperties.Seedance seedance = properties.getSeedance();
        SeedanceTaskStatus status = new SeedanceTaskStatus();
        status.setTaskId(taskId);

        if (!StringUtils.hasText(seedance.getBaseUrl()) || !StringUtils.hasText(seedance.getApiKey())) {
            // 本地 mock 直接返回成功，前端可完整看到最终态。
            log.info("Seedance 未配置厂商参数，返回 mock 成功状态，taskId={}", taskId);
            status.setStatus("SUCCEEDED");
            status.setVideoUrl("https://example.com/mock-seedance-video.mp4");
            return status;
        }

        Map<?, ?> response;
        try {
            String rawResponse = restClient()
                    .get()
                    .uri("/v1/video/generations/{taskId}", taskId)
                    .header("Authorization", "Bearer " + seedance.getApiKey())
                    .retrieve()
                    .body(String.class);
            response = parseSeedanceResponse(rawResponse, "Seedance 任务查询", null, taskId);
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
        log.info("Seedance 任务查询调用成功，taskId={}, providerStatus={}", taskId, status.getStatus());
        return status;
    }

    private List<Map<String, Object>> buildContent(GenerationSession session) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", session.getVideoPrompt()));

        List<KeyframeResult> keyframes = session.getKeyframes();
        for (int index = 0; index < keyframes.size(); index++) {
            KeyframeResult keyframe = keyframes.get(index);
            if (!StringUtils.hasText(keyframe.getGeneratedImageUrl())) {
                continue;
            }
            // Seedance 文档要求首帧/首尾帧/参考图三种图片场景互斥；这里使用多模态参考生视频。
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", keyframe.getGeneratedImageUrl()),
                    "role", "reference_image"
            ));
        }
        return content;
    }

    private String extractVideoUrl(Map<?, ?> task) {
        String directUrl = findString(task, "url", "video_url", "videoUrl");
        if (StringUtils.hasText(directUrl)) {
            return directUrl;
        }
        Object data = task == null ? null : task.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            String dataUrl = findString(dataMap, "url", "video_url", "videoUrl");
            if (StringUtils.hasText(dataUrl)) {
                return dataUrl;
            }
            Object content = dataMap.get("content");
            if (content instanceof Map<?, ?> contentMap) {
                return findString(contentMap, "video_url", "videoUrl", "url");
            }
        }
        return null;
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(properties.getSeedance().getBaseUrl()).build();
    }

    private Map<?, ?> parseSeedanceResponse(String rawResponse, String scene, String sessionId, String taskId) {
        if (!StringUtils.hasText(rawResponse)) {
            log.warn("{} 响应为空，sessionId={}, taskId={}", scene, sessionId, taskId);
            throw new BusinessException(scene + "响应为空");
        }
        try {
            Map<String, Object> response = objectMapper.readValue(rawResponse, new TypeReference<>() {
            });
            // 打印厂商响应的脱敏完整结构，方便排查状态字段和视频地址字段映射。
            log.info("{} 响应，sessionId={}, taskId={}, response={}",
                    scene, sessionId, taskId, JsonParser.toJson(sanitizeResponseForLog(response)));
            return response;
        } catch (Exception ex) {
            log.warn("{} 响应解析失败，sessionId={}, taskId={}, rawResponse={}, reason={}",
                    scene, sessionId, taskId, sanitizeRawResponseForLog(rawResponse), ex.getMessage());
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
        if (!StringUtils.hasText(imageUrl)) {
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

    private String extractProviderErrorMessage(String responseBody) {
        String fallback = "seedance 任务提交失败";
        if (!StringUtils.hasText(responseBody)) {
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
                    if (StringUtils.hasText(message)) {
                        return buildReadableProviderMessage(code, message);
                    }
                }
                Object message = errorMap.get("message");
                if (message instanceof String text && StringUtils.hasText(text)) {
                    current = text;
                    continue;
                }
                Object code = errorMap.get("code");
                if (code instanceof String text && StringUtils.hasText(text)) {
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
        return StringUtils.hasText(code) ? code + "：" + message : message;
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
