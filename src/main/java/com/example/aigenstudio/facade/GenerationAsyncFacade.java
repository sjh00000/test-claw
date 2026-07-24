package com.example.aigenstudio.facade;

import cn.hutool.core.util.StrUtil;
import com.example.aigenstudio.bo.ProviderConfigBO;
import com.example.aigenstudio.bo.ReferenceImageBO;
import com.example.aigenstudio.bo.TextToImageBO;
import com.example.aigenstudio.bo.TextToVideoBO;
import com.example.aigenstudio.client.ImageProviderClient;
import com.example.aigenstudio.client.SeedanceClient;
import com.example.aigenstudio.domain.GenerationTaskStatusEnum;
import com.example.aigenstudio.domain.ModelConfig;
import com.example.aigenstudio.domain.OperationTypeEnum;
import com.example.aigenstudio.domain.ReferenceImage;
import com.example.aigenstudio.domain.ServiceTypeEnum;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.service.GenerationTaskService;
import com.example.aigenstudio.service.ModelConfigService;
import com.example.aigenstudio.service.UserService;
import com.example.aigenstudio.vo.ImageGenerationVO;
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
            // 任务进入 RUNNING 后再调用厂商，前端能及时看到任务已被后台接管。
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
            // 失败时同时保存用户可读原因和结构化响应，便于任务中心展示与后端排障。
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
            // 视频提交失败不会进入厂商轮询，直接把本地任务终结为 FAILED。
            generationTaskService.markFinished(taskId, GenerationTaskStatusEnum.FAILED,
                    null, sanitizeFailure(ex.getMessage()), buildFailureResponse(ex));
            log.warn("文生视频异步任务提交失败，userId={}, taskId={}, reason={}", userId, taskId, ex.getMessage());
        }
    }

    private ProviderConfigBO buildProviderConfig(ServiceTypeEnum serviceTypeEnum) {
        // 图片/视频厂商参数由管理员配置，普通用户生成请求不能覆盖 baseUrl、apikey、model。
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
        // response_body 存结构化失败信息，避免后续只能从应用日志里反查厂商错误。
        return toJson(Map.of(
                "message", sanitizeFailure(ex.getMessage()),
                "cause", ex.getCause() == null ? "" : sanitizeFailure(ex.getCause().getMessage())
        ));
    }
}
