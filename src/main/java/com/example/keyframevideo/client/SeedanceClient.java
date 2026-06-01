package com.example.keyframevideo.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.keyframevideo.config.GenerationProperties;
import com.example.keyframevideo.domain.GenerationSession;
import com.example.keyframevideo.domain.KeyframeResult;
import com.example.keyframevideo.domain.ReferenceImage;
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
        List<Map<String, Object>> content = buildContent(session);
        metadata.put("content", content);
        metadata.put("duration", session.getDuration());
        metadata.put("resolution", session.getResolution());
        metadata.put("ratio", session.getRatio());
        metadata.put("generate_audio", session.isGenerateAudio());
        payload.put("metadata", metadata);
        log.info("Seedance 提交请求参数，sessionId={}, model={}, duration={}, resolution={}, ratio={}, generateAudio={}, actualContent={}",
                session.getId(), payload.get("model"), session.getDuration(), session.getResolution(), session.getRatio(),
                session.isGenerateAudio(), JsonParser.toJson(summarizeContentForLog(content)));

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
        log.info("Seedance 任务查询调用成功，taskId={}, providerStatus={}, videoUrl={}",
                taskId, status.getStatus(), sanitizeImageForLog(status.getVideoUrl()));
        return status;
    }

    public void cancel(String taskId) {
        GenerationProperties.Seedance seedance = properties.getSeedance();
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException("Seedance 任务 ID 不能为空");
        }
        if (!StringUtils.hasText(seedance.getBaseUrl()) || !StringUtils.hasText(seedance.getApiKey())) {
            // 本地 mock 模式只记录取消动作，不调用外部厂商。
            log.info("Seedance 未配置厂商参数，跳过 mock 任务取消，taskId={}", taskId);
            return;
        }
        try {
            String rawResponse = restClient()
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

    private List<Map<String, Object>> buildContent(GenerationSession session) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildVideoInstructionText(session)));

        List<ReferenceImage> referenceImages = session.getReferenceImages();
        for (int index = 0; index < referenceImages.size(); index++) {
            ReferenceImage referenceImage = referenceImages.get(index);
            if (!StringUtils.hasText(referenceImage.getImageUrl())) {
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

        List<KeyframeResult> keyframes = session.getKeyframes();
        for (int index = 0; index < keyframes.size(); index++) {
            KeyframeResult keyframe = keyframes.get(index);
            if (!StringUtils.hasText(keyframe.getGeneratedImageUrl())) {
                continue;
            }
            // 每张关键帧图前追加帧说明，避免视频模型只看到图片而无法对应剧情顺序和镜头意图。
            content.add(Map.of("type", "text", "text", buildKeyframeImageText(keyframe, index)));
            // Seedance 文档要求首帧/首尾帧/参考图三种图片场景互斥；这里统一使用多模态参考生视频。
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", keyframe.getGeneratedImageUrl()),
                    "role", "reference_image"
            ));
        }
        return content;
    }

    private String buildVideoInstructionText(GenerationSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("视频整体描述：\n")
                .append(session.getVideoPrompt())
                .append("\n\n参考图与关键帧使用规则：\n")
                .append("1. 后续图片分为主体参考图和关键帧参考图；主体参考图用于保持人物/主体外观一致，关键帧参考图用于确定镜头顺序、画面构图和剧情节点。\n")
                .append("2. 参考图名称只用于理解图片对应关系，不要把角色名、参考图名、关键帧名作为持续字幕、水印、Logo 或画面文字呈现。\n")
                .append("3. 如果视频描述需要旁白或对白，请将其作为声音/叙事节奏处理；除非明确要求，不要生成大段屏幕文字。\n");
        if (!session.getReferenceImages().isEmpty()) {
            builder.append("\n主体参考图对应关系：\n");
            for (int index = 0; index < session.getReferenceImages().size(); index++) {
                ReferenceImage referenceImage = session.getReferenceImages().get(index);
                builder.append(index + 1)
                        .append(". ")
                        .append(resolveReferenceImageName(referenceImage, index));
                builder.append("\n");
            }
        }
        if (!session.getKeyframes().isEmpty()) {
            builder.append("\n关键帧顺序说明：\n");
            for (int index = 0; index < session.getKeyframes().size(); index++) {
                KeyframeResult keyframe = session.getKeyframes().get(index);
                builder.append("关键帧 ")
                        .append(keyframe.getIndex() > 0 ? keyframe.getIndex() : index + 1)
                        .append("：")
                        .append(StringUtils.hasText(keyframe.getPrompt()) ? keyframe.getPrompt() : "按对应图片画面承接")
                        .append("\n");
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

    private String buildKeyframeImageText(KeyframeResult keyframe, int index) {
        int frameIndex = keyframe.getIndex() > 0 ? keyframe.getIndex() : index + 1;
        StringBuilder builder = new StringBuilder();
        builder.append("下面这张图片是关键帧 ")
                .append(frameIndex)
                .append(" 的参考图。");
        builder.append("请按视频整体描述中“关键帧 ")
                .append(frameIndex)
                .append("”对应的画面要求承接前后镜头。");
        return builder.toString();
    }

    private String resolveReferenceImageName(ReferenceImage referenceImage, int index) {
        if (StringUtils.hasText(referenceImage.getName())) {
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
            if (StringUtils.hasText(directUrl)) {
                return directUrl;
            }
            for (Object nestedValue : map.values()) {
                String nestedUrl = findVideoUrlDeep(nestedValue);
                if (StringUtils.hasText(nestedUrl)) {
                    return nestedUrl;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String nestedUrl = findVideoUrlDeep(item);
                if (StringUtils.hasText(nestedUrl)) {
                    return nestedUrl;
                }
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
