package com.example.keyframevideo.facade;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.bo.ProviderConfigBO;
import com.example.keyframevideo.bo.ReferenceImageBO;
import com.example.keyframevideo.bo.TextToImageBO;
import com.example.keyframevideo.bo.TextToVideoBO;
import com.example.keyframevideo.client.ImageProviderClient;
import com.example.keyframevideo.client.SeedanceClient;
import com.example.keyframevideo.domain.GenerationTaskStatusEnum;
import com.example.keyframevideo.domain.ModelConfig;
import com.example.keyframevideo.domain.OperationTypeEnum;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.domain.ServiceTypeEnum;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.GenerationTaskService;
import com.example.keyframevideo.service.ModelConfigService;
import com.example.keyframevideo.service.UserService;
import com.example.keyframevideo.vo.ImageGenerationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationAsyncFacade {

    private final ImageProviderClient imageProviderClient;
    private final SeedanceClient seedanceClient;
    private final GenerationTaskService generationTaskService;
    private final ModelConfigService modelConfigService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Async
    public void generateImageAsync(Long taskId, Long userId, TextToImageBO textToImageBO) {
        UserInfo userInfo = userService.getRequiredById(userId);
        try {
            generationTaskService.markRunning(taskId, null);
            ProviderConfigBO providerConfigBO = buildProviderConfig(ServiceTypeEnum.IMAGE);
            List<ReferenceImage> referenceImages = buildReferenceImages(textToImageBO.getReferenceImages());
            String imageUrl = imageProviderClient.generate(
                    textToImageBO.getPrompt().trim(),
                    referenceImages,
                    textToImageBO.getImageSize(),
                    textToImageBO.getImageQuality(),
                    providerConfigBO);
            decreaseRemainingCount(userInfo, OperationTypeEnum.TEXT_TO_IMAGE);
            ImageGenerationVO imageGenerationVO = new ImageGenerationVO();
            imageGenerationVO.setTaskId(taskId);
            imageGenerationVO.setStatus(GenerationTaskStatusEnum.SUCCEEDED.getCode());
            imageGenerationVO.setImageUrl(imageUrl);
            generationTaskService.markFinished(taskId, GenerationTaskStatusEnum.SUCCEEDED, imageUrl, null, toJson(imageGenerationVO));
            log.info("文生图异步任务完成，userId={}, taskId={}, referenceImageCount={}", userId, taskId, referenceImages.size());
        } catch (RuntimeException ex) {
            generationTaskService.markFinished(taskId, GenerationTaskStatusEnum.FAILED,
                    null, sanitizeFailure(ex.getMessage()), buildFailureResponse(ex));
            log.warn("文生图异步任务失败，userId={}, taskId={}, reason={}", userId, taskId, ex.getMessage());
        }
    }

    @Async
    public void submitVideoAsync(Long taskId, Long userId, TextToVideoBO textToVideoBO) {
        UserInfo userInfo = userService.getRequiredById(userId);
        try {
            ProviderConfigBO providerConfigBO = buildProviderConfig(ServiceTypeEnum.VIDEO);
            List<ReferenceImage> referenceImages = buildReferenceImages(textToVideoBO.getReferenceImages());
            String providerTaskId = seedanceClient.submit(
                    textToVideoBO.getPrompt().trim(),
                    referenceImages,
                    textToVideoBO.getDuration(),
                    textToVideoBO.getResolution(),
                    textToVideoBO.getRatio(),
                    textToVideoBO.isGenerateAudio(),
                    providerConfigBO);
            decreaseRemainingCount(userInfo, OperationTypeEnum.TEXT_TO_VIDEO);
            generationTaskService.markRunning(taskId, providerTaskId);
            log.info("文生视频异步任务已提交厂商，userId={}, taskId={}, providerTaskId={}, referenceImageCount={}",
                    userId, taskId, providerTaskId, referenceImages.size());
        } catch (RuntimeException ex) {
            generationTaskService.markFinished(taskId, GenerationTaskStatusEnum.FAILED,
                    null, sanitizeFailure(ex.getMessage()), buildFailureResponse(ex));
            log.warn("文生视频异步任务提交失败，userId={}, taskId={}, reason={}", userId, taskId, ex.getMessage());
        }
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

    private void decreaseRemainingCount(UserInfo userInfo, OperationTypeEnum operationTypeEnum) {
        // 厂商调用成功后再扣减剩余次数，失败任务不会消耗用户额度。
        boolean decreased = userService.decreaseRemainingCount(userInfo.getId(), operationTypeEnum);
        if (!decreased) {
            log.warn("生成成功但剩余次数扣减失败，userId={}, operationType={}", userInfo.getId(), operationTypeEnum.getCode());
            throw new BusinessException(operationTypeEnum.getDesc() + "剩余次数不足");
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String sanitizeFailure(String message) {
        if (StrUtil.isBlank(message)) {
            return "生成失败";
        }
        return message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    private String buildFailureResponse(RuntimeException ex) {
        return toJson(Map.of(
                "message", sanitizeFailure(ex.getMessage()),
                "cause", ex.getCause() == null ? "" : sanitizeFailure(ex.getCause().getMessage())
        ));
    }
}
