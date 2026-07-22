package com.example.keyframevideo.facade;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.auth.CurrentUserContext;
import com.example.keyframevideo.bo.GenerationTaskQueryBO;
import com.example.keyframevideo.bo.GenerationTaskStatusBO;
import com.example.keyframevideo.bo.ProviderConfigBO;
import com.example.keyframevideo.bo.TextToImageBO;
import com.example.keyframevideo.bo.TextToVideoBO;
import com.example.keyframevideo.bo.VideoStatusBO;
import com.example.keyframevideo.client.SeedanceClient;
import com.example.keyframevideo.domain.GenerationTask;
import com.example.keyframevideo.domain.GenerationTaskStatusEnum;
import com.example.keyframevideo.domain.ModelConfig;
import com.example.keyframevideo.domain.OperationTypeEnum;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.domain.ServiceTypeEnum;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.GenerationTaskService;
import com.example.keyframevideo.service.ModelConfigService;
import com.example.keyframevideo.vo.GenerationTaskVO;
import com.example.keyframevideo.vo.ImageGenerationVO;
import com.example.keyframevideo.vo.VideoGenerationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationFacade {

    private final SeedanceClient seedanceClient;
    private final GenerationTaskService generationTaskService;
    private final ModelConfigService modelConfigService;
    private final GenerationAsyncFacade generationAsyncFacade;
    private final ObjectMapper objectMapper;

    public ImageGenerationVO generateImage(TextToImageBO textToImageBO) {
        UserInfo userInfo = loadCurrentUsableUser();
        validateImageOptions(textToImageBO.getImageSize(), textToImageBO.getImageQuality());
        assertRemainingCount(userInfo, OperationTypeEnum.TEXT_TO_IMAGE, resolveImageRemainingCount(userInfo));
        // 先创建本地任务再异步调用厂商，接口可立即返回 taskId 给前端轮询。
        GenerationTask generationTask = generationTaskService.createSubmittedTask(
                userInfo,
                OperationTypeEnum.TEXT_TO_IMAGE,
                toJson(sanitizeForStorage(textToImageBO)));
        generationAsyncFacade.generateImageAsync(generationTask.getId(), userInfo.getId(), textToImageBO);

        ImageGenerationVO imageGenerationVO = new ImageGenerationVO();
        imageGenerationVO.setTaskId(generationTask.getId());
        imageGenerationVO.setStatus(generationTask.getStatus());
        log.info("文生图任务已创建，userId={}, taskId={}", userInfo.getId(), generationTask.getId());
        return imageGenerationVO;
    }

    public VideoGenerationVO generateVideo(TextToVideoBO textToVideoBO) {
        UserInfo userInfo = loadCurrentUsableUser();
        validateVideoOptions(textToVideoBO);
        assertRemainingCount(userInfo, OperationTypeEnum.TEXT_TO_VIDEO, resolveVideoRemainingCount(userInfo));
        // 视频厂商本身也是异步任务，本地任务用于统一承载状态、额度扣减和最终下载地址。
        GenerationTask generationTask = generationTaskService.createSubmittedTask(
                userInfo,
                OperationTypeEnum.TEXT_TO_VIDEO,
                toJson(sanitizeForStorage(textToVideoBO)));
        generationAsyncFacade.submitVideoAsync(generationTask.getId(), userInfo.getId(), textToVideoBO);

        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(generationTask.getId());
        videoGenerationVO.setStatus(generationTask.getStatus());
        log.info("文生视频任务已创建，userId={}, taskId={}", userInfo.getId(), generationTask.getId());
        return videoGenerationVO;
    }

    public VideoGenerationVO queryVideoStatus(VideoStatusBO videoStatusBO) {
        GenerationTask generationTask = refreshAndLoadTask(videoStatusBO.getTaskId());
        if (!OperationTypeEnum.TEXT_TO_VIDEO.getCode().equals(generationTask.getTaskType())) {
            throw new BusinessException("该任务不是视频任务");
        }
        return toVideoGenerationVO(generationTask);
    }

    public GenerationTaskVO queryTaskStatus(GenerationTaskStatusBO taskStatusBO) {
        return toTaskVO(refreshAndLoadTask(taskStatusBO.getTaskId()));
    }

    public List<GenerationTaskVO> listTasks(GenerationTaskQueryBO queryBO) {
        UserInfo userInfo = loadCurrentUsableUser();
        return generationTaskService.listVisibleTaskSummaries(
                        userInfo,
                        queryBO.getUsername(),
                        queryBO.getTaskType(),
                        queryBO.getStatus())
                .stream()
                .map(this::toTaskVO)
                .toList();
    }

    private GenerationTask refreshAndLoadTask(Long taskId) {
        UserInfo userInfo = loadCurrentUsableUser();
        GenerationTask generationTask = generationTaskService.getRequiredVisibleTask(taskId, userInfo);
        // 视频结果只在用户查询本地任务时按需刷新，避免后台无界轮询厂商接口。
        refreshVideoTaskIfNecessary(generationTask);
        return generationTaskService.getRequiredVisibleTask(taskId, userInfo);
    }

    private void refreshVideoTaskIfNecessary(GenerationTask generationTask) {
        if (!OperationTypeEnum.TEXT_TO_VIDEO.getCode().equals(generationTask.getTaskType())
                || StrUtil.isBlank(generationTask.getProviderTaskId())
                || isTerminalStatus(generationTask.getStatus())) {
            return;
        }

        SeedanceTaskStatus taskStatus = seedanceClient.query(generationTask.getProviderTaskId(), buildProviderConfig(ServiceTypeEnum.VIDEO));
        GenerationTaskStatusEnum statusEnum = GenerationTaskStatusEnum.normalize(
                taskStatus.getStatus(),
                taskStatus.getVideoUrl(),
                taskStatus.getFailReason());
        if (GenerationTaskStatusEnum.RUNNING.equals(statusEnum)) {
            generationTaskService.markRunning(generationTask.getId(), generationTask.getProviderTaskId());
            return;
        }
        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(generationTask.getId());
        videoGenerationVO.setProviderTaskId(generationTask.getProviderTaskId());
        videoGenerationVO.setStatus(statusEnum.getCode());
        videoGenerationVO.setVideoUrl(taskStatus.getVideoUrl());
        videoGenerationVO.setFailReason(taskStatus.getFailReason());
        // 视频厂商状态只有在前端轮询本地任务时刷新，任务表保存最终状态和可下载地址。
        generationTaskService.markFinished(
                generationTask.getId(),
                statusEnum,
                taskStatus.getVideoUrl(),
                taskStatus.getFailReason(),
                toJson(videoGenerationVO));
    }

    private void validateImageOptions(String imageSize, String imageQuality) {
        if (!List.of("1024x1024", "1024x1536", "1536x1024").contains(imageSize)) {
            throw new BusinessException("图片尺寸仅支持 1024x1024、1024x1536、1536x1024");
        }
        if (!List.of("low", "medium", "high").contains(imageQuality)) {
            throw new BusinessException("图片质量仅支持 low、medium、high");
        }
    }

    private UserInfo loadCurrentUsableUser() {
        return CurrentUserContext.getRequiredUser();
    }

    private ProviderConfigBO buildProviderConfig(ServiceTypeEnum serviceTypeEnum) {
        ModelConfig modelConfig = modelConfigService.getEnabledConfig(serviceTypeEnum);
        if (modelConfig == null
                || StrUtil.isBlank(modelConfig.getBaseUrl())
                || StrUtil.isBlank(modelConfig.getApiKey())
                || StrUtil.isBlank(modelConfig.getModel())) {
            throw new BusinessException("管理员尚未配置" + serviceTypeEnum.getDesc());
        }
        ProviderConfigBO providerConfigBO = new ProviderConfigBO();
        providerConfigBO.setBaseUrl(modelConfig.getBaseUrl());
        providerConfigBO.setApiKey(modelConfig.getApiKey());
        providerConfigBO.setModel(modelConfig.getModel());
        return providerConfigBO;
    }

    private void assertRemainingCount(UserInfo userInfo, OperationTypeEnum operationTypeEnum, int remainingCount) {
        if (remainingCount <= 0) {
            throw new BusinessException(operationTypeEnum.getDesc() + "剩余次数不足");
        }
    }

    private int resolveImageRemainingCount(UserInfo userInfo) {
        return userInfo.getImageRemainingCount() == null ? 0 : userInfo.getImageRemainingCount();
    }

    private int resolveVideoRemainingCount(UserInfo userInfo) {
        return userInfo.getVideoRemainingCount() == null ? 0 : userInfo.getVideoRemainingCount();
    }

    private void validateVideoOptions(TextToVideoBO textToVideoBO) {
        if (!List.of("480p", "720p").contains(textToVideoBO.getResolution())) {
            throw new BusinessException("视频清晰度仅支持 480p 或 720p");
        }
        if (!List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(textToVideoBO.getRatio())) {
            throw new BusinessException("视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive");
        }
        if (textToVideoBO.getDuration() < 4 || textToVideoBO.getDuration() > 15) {
            throw new BusinessException("视频时长需为 4 到 15 秒之间的整数");
        }
    }

    private GenerationTaskVO toTaskVO(GenerationTask generationTask) {
        GenerationTaskVO generationTaskVO = new GenerationTaskVO();
        generationTaskVO.setTaskId(generationTask.getId());
        generationTaskVO.setUserId(generationTask.getUserId());
        generationTaskVO.setUsername(generationTask.getUsername());
        generationTaskVO.setTaskType(generationTask.getTaskType());
        generationTaskVO.setTaskName(OperationTypeEnum.getDescByCode(generationTask.getTaskType()));
        generationTaskVO.setProviderTaskId(generationTask.getProviderTaskId());
        generationTaskVO.setStatus(generationTask.getStatus());
        generationTaskVO.setStatusName(GenerationTaskStatusEnum.getDescByCode(generationTask.getStatus()));
        generationTaskVO.setResultUrl(generationTask.getResultUrl());
        generationTaskVO.setFailReason(generationTask.getFailReason());
        generationTaskVO.setCreatedAt(generationTask.getCreatedAt());
        generationTaskVO.setUpdatedAt(generationTask.getUpdatedAt());
        return generationTaskVO;
    }

    private VideoGenerationVO toVideoGenerationVO(GenerationTask generationTask) {
        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(generationTask.getId());
        videoGenerationVO.setProviderTaskId(generationTask.getProviderTaskId());
        videoGenerationVO.setStatus(generationTask.getStatus());
        videoGenerationVO.setVideoUrl(generationTask.getResultUrl());
        videoGenerationVO.setFailReason(generationTask.getFailReason());
        return videoGenerationVO;
    }

    private boolean isTerminalStatus(String status) {
        return GenerationTaskStatusEnum.SUCCEEDED.getCode().equals(status)
                || GenerationTaskStatusEnum.FAILED.getCode().equals(status)
                || GenerationTaskStatusEnum.CANCELED.getCode().equals(status);
    }

    private Object sanitizeForStorage(Object value) {
        // 请求入参落库前要脱敏/压缩大字段，尤其是参考图 data URL，避免日志和任务表膨胀。
        Object jsonValue = objectMapper.convertValue(value, Object.class);
        return sanitizeJsonValue(jsonValue);
    }

    private Object sanitizeJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> sanitizedMap.put(String.valueOf(key), sanitizeJsonValue(itemValue)));
            return sanitizedMap;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeJsonValue).toList();
        }
        if (value instanceof String text && text.startsWith("data:image/")) {
            return "data:image/*;base64,<length=" + text.length() + ">";
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }
}
