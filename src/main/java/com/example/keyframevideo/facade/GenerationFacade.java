package com.example.keyframevideo.facade;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.auth.CurrentUserContext;
import com.example.keyframevideo.bo.ProviderConfigBO;
import com.example.keyframevideo.bo.ReferenceImageBO;
import com.example.keyframevideo.bo.TextToImageBO;
import com.example.keyframevideo.bo.TextToVideoBO;
import com.example.keyframevideo.bo.VideoStatusBO;
import com.example.keyframevideo.client.ImageProviderClient;
import com.example.keyframevideo.client.SeedanceClient;
import com.example.keyframevideo.domain.ModelConfig;
import com.example.keyframevideo.domain.OperationLog;
import com.example.keyframevideo.domain.OperationLogStatusEnum;
import com.example.keyframevideo.domain.OperationTypeEnum;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.domain.ServiceTypeEnum;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.ModelConfigService;
import com.example.keyframevideo.service.OperationLogService;
import com.example.keyframevideo.vo.ImageGenerationVO;
import com.example.keyframevideo.vo.VideoGenerationVO;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationFacade {

    private final ImageProviderClient imageProviderClient;
    private final SeedanceClient seedanceClient;
    private final OperationLogService operationLogService;
    private final ModelConfigService modelConfigService;

    public ImageGenerationVO generateImage(TextToImageBO textToImageBO) {
        Instant startedAt = Instant.now();
        int referenceImageCount = textToImageBO.getReferenceImages().size();
        UserInfo userInfo = loadCurrentUsableUser();
        try {
            validateImageOptions(textToImageBO.getImageSize(), textToImageBO.getImageQuality());
            assertCallLimit(userInfo, OperationTypeEnum.TEXT_TO_IMAGE, resolveImageLimit(userInfo));
            ProviderConfigBO providerConfigBO = buildProviderConfig(ServiceTypeEnum.IMAGE);
            List<ReferenceImage> referenceImages = buildReferenceImages(textToImageBO.getReferenceImages());
            // 文生图与文生视频完全解耦：本接口只返回图片结果，不创建会话也不触发视频流程。
            String imageUrl = imageProviderClient.generate(
                    textToImageBO.getPrompt().trim(),
                    referenceImages,
                    textToImageBO.getImageSize(),
                    textToImageBO.getImageQuality(),
                    providerConfigBO);
            ImageGenerationVO imageGenerationVO = new ImageGenerationVO();
            imageGenerationVO.setImageUrl(imageUrl);
            recordGenerationOperation(userInfo, OperationTypeEnum.TEXT_TO_IMAGE,
                    OperationLogStatusEnum.SUCCESS, "IMAGE", null,
                    buildImageRequestSummary(textToImageBO, referenceImages.size()),
                    "imageUrl=" + sanitizeForSummary(imageUrl), null, startedAt);
            log.info("文生图完成，userId={}, referenceImageCount={}", userInfo.getId(), referenceImages.size());
            return imageGenerationVO;
        } catch (RuntimeException ex) {
            recordGenerationOperation(userInfo, OperationTypeEnum.TEXT_TO_IMAGE,
                    OperationLogStatusEnum.FAILURE, "IMAGE", null,
                    buildImageRequestSummary(textToImageBO, referenceImageCount),
                    null, ex.getMessage(), startedAt);
            throw ex;
        }
    }

    public VideoGenerationVO generateVideo(TextToVideoBO textToVideoBO) {
        Instant startedAt = Instant.now();
        int referenceImageCount = textToVideoBO.getReferenceImages().size();
        UserInfo userInfo = loadCurrentUsableUser();
        try {
            validateVideoOptions(textToVideoBO);
            assertCallLimit(userInfo, OperationTypeEnum.TEXT_TO_VIDEO, resolveVideoLimit(userInfo));
            ProviderConfigBO providerConfigBO = buildProviderConfig(ServiceTypeEnum.VIDEO);
            List<ReferenceImage> referenceImages = buildReferenceImages(textToVideoBO.getReferenceImages());
            // 文生视频直接提交 Seedance；参考图为空时按纯文本生成，有参考图时作为多模态参考输入。
            String taskId = seedanceClient.submit(
                    textToVideoBO.getPrompt().trim(),
                    referenceImages,
                    textToVideoBO.getDuration(),
                    textToVideoBO.getResolution(),
                    textToVideoBO.getRatio(),
                    textToVideoBO.isGenerateAudio(),
                    providerConfigBO);
            VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
            videoGenerationVO.setTaskId(taskId);
            videoGenerationVO.setStatus("SUBMITTED");
            recordGenerationOperation(userInfo, OperationTypeEnum.TEXT_TO_VIDEO,
                    OperationLogStatusEnum.SUCCESS, "VIDEO_TASK", taskId,
                    buildVideoRequestSummary(textToVideoBO, referenceImages.size()),
                    "taskId=" + taskId + ", status=SUBMITTED", null, startedAt);
            log.info("文生视频任务已提交，userId={}, taskId={}, referenceImageCount={}",
                    userInfo.getId(), taskId, referenceImages.size());
            return videoGenerationVO;
        } catch (RuntimeException ex) {
            recordGenerationOperation(userInfo, OperationTypeEnum.TEXT_TO_VIDEO,
                    OperationLogStatusEnum.FAILURE, "VIDEO_TASK", null,
                    buildVideoRequestSummary(textToVideoBO, referenceImageCount),
                    null, ex.getMessage(), startedAt);
            throw ex;
        }
    }

    public VideoGenerationVO queryVideoStatus(VideoStatusBO videoStatusBO) {
        loadCurrentUsableUser();
        SeedanceTaskStatus taskStatus = seedanceClient.query(videoStatusBO.getTaskId(), buildProviderConfig(ServiceTypeEnum.VIDEO));
        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(taskStatus.getTaskId());
        videoGenerationVO.setStatus(taskStatus.getStatus());
        videoGenerationVO.setVideoUrl(taskStatus.getVideoUrl());
        videoGenerationVO.setFailReason(taskStatus.getFailReason());
        return videoGenerationVO;
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

    private void assertCallLimit(UserInfo userInfo, OperationTypeEnum operationTypeEnum, int callLimit) {
        long usedCount = operationLogService.countSuccess(userInfo.getId(), operationTypeEnum.getCode());
        if (usedCount >= callLimit) {
            throw new BusinessException(operationTypeEnum.getDesc() + "总调用次数已达上限");
        }
    }

    private int resolveImageLimit(UserInfo userInfo) {
        return userInfo.getImageCallLimit() == null ? 0 : userInfo.getImageCallLimit();
    }

    private int resolveVideoLimit(UserInfo userInfo) {
        return userInfo.getVideoCallLimit() == null ? 0 : userInfo.getVideoCallLimit();
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

    private List<ReferenceImage> buildReferenceImages(List<ReferenceImageBO> referenceImageBOList) {
        List<ReferenceImage> referenceImages = new ArrayList<>();
        for (int index = 0; index < referenceImageBOList.size(); index++) {
            ReferenceImageBO referenceImageBO = referenceImageBOList.get(index);
            if (StrUtil.isBlank(referenceImageBO.getImageUrl())) {
                throw new BusinessException("参考图地址不能为空");
            }
            ReferenceImage referenceImage = new ReferenceImage();
            referenceImage.setImageUrl(referenceImageBO.getImageUrl());
            referenceImage.setName(StrUtil.isNotBlank(referenceImageBO.getName())
                    ? referenceImageBO.getName().trim()
                    : "参考图" + (index + 1));
            referenceImages.add(referenceImage);
        }
        return referenceImages;
    }

    private String buildImageRequestSummary(TextToImageBO textToImageBO, int referenceImageCount) {
        return "promptLength=" + textToImageBO.getPrompt().trim().length()
                + ", imageSize=" + textToImageBO.getImageSize()
                + ", imageQuality=" + textToImageBO.getImageQuality()
                + ", referenceImageCount=" + referenceImageCount;
    }

    private String buildVideoRequestSummary(TextToVideoBO textToVideoBO, int referenceImageCount) {
        return "promptLength=" + textToVideoBO.getPrompt().trim().length()
                + ", duration=" + textToVideoBO.getDuration()
                + ", resolution=" + textToVideoBO.getResolution()
                + ", ratio=" + textToVideoBO.getRatio()
                + ", generateAudio=" + textToVideoBO.isGenerateAudio()
                + ", referenceImageCount=" + referenceImageCount;
    }

    private void recordGenerationOperation(
            UserInfo userInfo,
            OperationTypeEnum operationType,
            OperationLogStatusEnum status,
            String targetType,
            String targetId,
            String requestSummary,
            String responseSummary,
            String errorMessage,
            Instant startedAt) {
        OperationLog operationLog = new OperationLog();
        operationLog.setUserId(userInfo.getId());
        operationLog.setUsername(userInfo.getUsername());
        operationLog.setOperationType(operationType.getCode());
        operationLog.setOperationName(operationType.getDesc());
        operationLog.setTargetType(targetType);
        operationLog.setTargetId(targetId);
        operationLog.setStatus(status.getCode());
        operationLog.setRequestSummary(requestSummary);
        operationLog.setResponseSummary(responseSummary);
        operationLog.setErrorMessage(sanitizeForSummary(errorMessage));
        operationLog.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
        // 生成类操作涉及外部厂商调用，成功和失败都独立落审计表，便于按用户和任务排查。
        operationLogService.record(operationLog);
    }

    private String sanitizeForSummary(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        if (value.startsWith("data:image/")) {
            return "data:image/*;base64,<length=" + value.length() + ">";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
