package com.example.keyframevideo.facade;

import com.example.keyframevideo.bo.CreateSessionBO;
import com.example.keyframevideo.bo.CreateVideoFromKeyframesBO;
import com.example.keyframevideo.bo.GenerateReferenceImageBO;
import com.example.keyframevideo.bo.KeyframeBO;
import com.example.keyframevideo.client.ImageProviderClient;
import com.example.keyframevideo.client.SeedanceClient;
import com.example.keyframevideo.domain.GenerationSession;
import com.example.keyframevideo.domain.GenerationStatusEnum;
import com.example.keyframevideo.domain.KeyframeResult;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.GenerationSessionService;
import com.example.keyframevideo.service.GenerationSessionAssembler;
import com.example.keyframevideo.vo.GenerationSessionVO;
import com.example.keyframevideo.vo.ReferenceImageVO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class GenerationWorkflowFacade {

    private final ImageProviderClient imageProviderClient;
    private final SeedanceClient seedanceClient;
    private final GenerationSessionAssembler assembler;
    private final GenerationSessionService generationSessionService;

    public GenerationWorkflowFacade(
            ImageProviderClient imageProviderClient,
            SeedanceClient seedanceClient,
            GenerationSessionAssembler assembler,
            GenerationSessionService generationSessionService) {
        this.imageProviderClient = imageProviderClient;
        this.seedanceClient = seedanceClient;
        this.assembler = assembler;
        this.generationSessionService = generationSessionService;
    }

    public GenerationSessionVO createSession(CreateSessionBO createSessionBO) {
        validateRequest(createSessionBO);

        // 创建会话只落内存和校验输入，不立即调用外部厂商，便于前端明确展示 DRAFT 状态。
        GenerationSession session = new GenerationSession();
        session.setId(UUID.randomUUID().toString());
        session.setVideoPrompt(createSessionBO.getVideoPrompt());
        List<ReferenceImage> referenceImages = buildReferenceImages(createSessionBO);
        session.setReferenceImages(referenceImages);
        session.setReferenceImageUrls(referenceImages.stream().map(ReferenceImage::getImageUrl).toList());
        session.setDuration(createSessionBO.getDuration());
        session.setResolution(createSessionBO.getResolution());
        session.setRatio(createSessionBO.getRatio());
        session.setGenerateAudio(createSessionBO.isGenerateAudio());
        session.setFastMode(createSessionBO.isFastMode());
        session.setKeyframes(buildKeyframes(
                createSessionBO.getKeyframes(),
                referenceImages,
                createSessionBO.getImageSize(),
                createSessionBO.getImageQuality()));

        generationSessionService.saveOrUpdate(session);
        log.info("创建生成会话成功，sessionId={}, keyframeCount={}, referenceImageCount={}, imageSize={}, imageQuality={}, duration={}, resolution={}, ratio={}",
                session.getId(), session.getKeyframes().size(), session.getReferenceImageUrls().size(),
                createSessionBO.getImageSize(), createSessionBO.getImageQuality(),
                session.getDuration(), session.getResolution(), session.getRatio());
        return assembler.toVO(session);
    }

    public ReferenceImageVO generateReferenceImage(GenerateReferenceImageBO generateReferenceImageBO) {
        validateImageOptions(generateReferenceImageBO.getImageSize(), generateReferenceImageBO.getImageQuality());
        // 主体参考图先由 image-2 独立生成，用户确认后再作为会话级参考图参与关键帧生成。
        String imageUrl = imageProviderClient.generateReferenceImage(
                generateReferenceImageBO.getPrompt(),
                generateReferenceImageBO.getImageSize(),
                generateReferenceImageBO.getImageQuality());
        ReferenceImageVO referenceImageVO = new ReferenceImageVO();
        referenceImageVO.setImageUrl(imageUrl);
        log.info("参考图生成完成");
        return referenceImageVO;
    }

    public GenerationSessionVO generateKeyframes(String sessionId) {
        GenerationSession session = findSession(sessionId);
        if (session.getStatus() != GenerationStatusEnum.DRAFT && session.getStatus() != GenerationStatusEnum.FAILED) {
            throw new BusinessException("当前会话状态不允许重新生成关键帧");
        }

        session.setErrorMessage(null);
        session.setStatus(GenerationStatusEnum.GENERATING_KEYFRAMES);
        generationSessionService.saveOrUpdate(session);
        log.info("开始生成关键帧，sessionId={}, keyframeCount={}", sessionId, session.getKeyframes().size());
        // gpt-image2 一次只能生成一张图，因此这里必须严格按关键帧顺序串行调用。
        for (KeyframeResult keyframe : session.getKeyframes()) {
            try {
                keyframe.setStatus(GenerationStatusEnum.GENERATING_KEYFRAMES);
                keyframe.setErrorMessage(null);
                keyframe.setGeneratedImageUrl(imageProviderClient.generate(keyframe));
                keyframe.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
                generationSessionService.saveOrUpdate(session);
                log.info("关键帧生成成功，sessionId={}, frameIndex={}", sessionId, keyframe.getIndex());
            } catch (Exception ex) {
                keyframe.setErrorMessage(ex.getMessage());
                keyframe.setStatus(GenerationStatusEnum.FAILED);
                session.setErrorMessage("第 " + keyframe.getIndex() + " 个关键帧生成失败：" + ex.getMessage());
                session.setStatus(GenerationStatusEnum.FAILED);
                generationSessionService.saveOrUpdate(session);
                log.warn("关键帧生成失败，sessionId={}, frameIndex={}, reason={}", sessionId, keyframe.getIndex(), ex.getMessage());
                return assembler.toVO(session);
            }
        }

        session.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
        generationSessionService.saveOrUpdate(session);
        log.info("全部关键帧生成完成，sessionId={}", sessionId);
        return assembler.toVO(session);
    }

    public GenerationSessionVO submitVideo(String sessionId) {
        GenerationSession session = findSession(sessionId);
        if (session.getStatus() != GenerationStatusEnum.KEYFRAMES_READY) {
            throw new BusinessException("请先完成全部关键帧生成");
        }

        session.setStatus(GenerationStatusEnum.SUBMITTING_VIDEO);
        generationSessionService.saveOrUpdate(session);
        try {
            // Seedance 接收多模态参考生视频任务，图片 role 统一由 SeedanceClient 设置为 reference_image。
            String taskId = seedanceClient.submit(session);
            session.setSeedanceTaskId(taskId);
            session.setStatus(GenerationStatusEnum.VIDEO_RUNNING);
            generationSessionService.saveOrUpdate(session);
            log.info("Seedance 视频任务提交成功，sessionId={}, taskId={}", sessionId, taskId);
            return assembler.toVO(session);
        } catch (Exception ex) {
            session.setErrorMessage(ex.getMessage());
            session.setStatus(GenerationStatusEnum.FAILED);
            generationSessionService.saveOrUpdate(session);
            log.warn("Seedance 视频任务提交失败，sessionId={}, reason={}", sessionId, ex.getMessage());
            return assembler.toVO(session);
        }
    }

    public GenerationSessionVO submitVideoFromKeyframes(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        validateVideoRequest(createVideoFromKeyframesBO);

        // 用户可以跳过 image-2 关键帧生成，直接上传关键帧图；这里构造一个已就绪会话提交 Seedance。
        GenerationSession session = new GenerationSession();
        session.setId(UUID.randomUUID().toString());
        session.setVideoPrompt(createVideoFromKeyframesBO.getVideoPrompt());
        session.setDuration(createVideoFromKeyframesBO.getDuration());
        session.setResolution(createVideoFromKeyframesBO.getResolution());
        session.setRatio(createVideoFromKeyframesBO.getRatio());
        session.setGenerateAudio(createVideoFromKeyframesBO.isGenerateAudio());
        session.setFastMode(createVideoFromKeyframesBO.isFastMode());
        session.setKeyframes(buildReadyKeyframes(createVideoFromKeyframesBO.getKeyframeImageUrls()));
        session.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
        generationSessionService.saveOrUpdate(session);
        return submitVideo(session.getId());
    }

    public GenerationSessionVO refreshVideo(String sessionId) {
        GenerationSession session = findSession(sessionId);
        if (!StringUtils.hasText(session.getSeedanceTaskId())) {
            return assembler.toVO(session);
        }

        SeedanceTaskStatus status = seedanceClient.query(session.getSeedanceTaskId());
        String normalized = status.getStatus() == null ? "" : status.getStatus().toUpperCase(Locale.ROOT);
        log.info("刷新 Seedance 视频任务状态，sessionId={}, taskId={}, providerStatus={}",
                sessionId, session.getSeedanceTaskId(), normalized);
        // 兼容不同厂商返回 SUCCESS/SUCCEEDED、FAILURE/FAILED 的状态命名差异。
        if ("SUCCESS".equals(normalized) || "SUCCEEDED".equals(normalized)) {
            session.setVideoUrl(status.getVideoUrl());
            session.setStatus(GenerationStatusEnum.SUCCEEDED);
            generationSessionService.saveOrUpdate(session);
            log.info("Seedance 视频生成成功，sessionId={}, taskId={}", sessionId, session.getSeedanceTaskId());
        } else if ("FAILURE".equals(normalized) || "FAILED".equals(normalized)) {
            String failReason = StringUtils.hasText(status.getFailReason()) ? status.getFailReason() : "seedance 视频生成失败";
            session.setErrorMessage(failReason);
            session.setStatus(GenerationStatusEnum.FAILED);
            generationSessionService.saveOrUpdate(session);
            log.warn("Seedance 视频生成失败，sessionId={}, taskId={}, reason={}", sessionId, session.getSeedanceTaskId(), failReason);
        } else if ("NOT_START".equals(normalized) || "IN_PROGRESS".equals(normalized) || "RUNNING".equals(normalized) || normalized.isBlank()) {
            session.setStatus(GenerationStatusEnum.VIDEO_RUNNING);
            generationSessionService.saveOrUpdate(session);
        } else {
            log.warn("Seedance 返回未知任务状态，sessionId={}, taskId={}, providerStatus={}",
                    sessionId, session.getSeedanceTaskId(), normalized);
            session.setStatus(GenerationStatusEnum.VIDEO_RUNNING);
            generationSessionService.saveOrUpdate(session);
        }
        return assembler.toVO(session);
    }

    public GenerationSessionVO getSession(String sessionId) {
        return assembler.toVO(findSession(sessionId));
    }

    private void validateRequest(CreateSessionBO createSessionBO) {
        // 双重校验数量，避免前端 keyframeCount 与 keyframes 数组不同步导致漏生成。
        if (createSessionBO.getKeyframeCount() != createSessionBO.getKeyframes().size()) {
            throw new BusinessException("关键帧数量与关键帧列表不一致");
        }
        if (createSessionBO.getKeyframeCount() > 50) {
            throw new BusinessException("关键帧最多支持 50 张");
        }
        if (!List.of("480p", "720p").contains(createSessionBO.getResolution())) {
            throw new BusinessException("视频清晰度仅支持 480p 或 720p");
        }
        validateImageOptions(createSessionBO.getImageSize(), createSessionBO.getImageQuality());
        if (!List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(createSessionBO.getRatio())) {
            throw new BusinessException("视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive");
        }
        if (createSessionBO.getDuration() != -1 && (createSessionBO.getDuration() < 4 || createSessionBO.getDuration() > 15)) {
            throw new BusinessException("视频时长需为 -1 或 4 到 15 秒之间的整数");
        }
        // image2 edits 接口依赖主体参考图文件；参考图是会话级主体/角色约束，所有关键帧共用。
        if (createSessionBO.getReferenceImageUrls().isEmpty() && createSessionBO.getReferenceImages().isEmpty()) {
            throw new BusinessException("至少需要 1 张主体参考图");
        }
    }

    private void validateVideoRequest(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        if (createVideoFromKeyframesBO.getKeyframeImageUrls().size() > 50) {
            throw new BusinessException("关键帧图最多支持 50 张");
        }
        if (!List.of("480p", "720p").contains(createVideoFromKeyframesBO.getResolution())) {
            throw new BusinessException("视频清晰度仅支持 480p 或 720p");
        }
        if (!List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(createVideoFromKeyframesBO.getRatio())) {
            throw new BusinessException("视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive");
        }
        if (createVideoFromKeyframesBO.getDuration() != -1
                && (createVideoFromKeyframesBO.getDuration() < 4 || createVideoFromKeyframesBO.getDuration() > 15)) {
            throw new BusinessException("视频时长需为 -1 或 4 到 15 秒之间的整数");
        }
    }

    private void validateImageOptions(String imageSize, String imageQuality) {
        if (!List.of("1024x1024", "1024x1536", "1536x1024").contains(imageSize)) {
            throw new BusinessException("image-2 图片尺寸仅支持 1024x1024、1024x1536、1536x1024");
        }
        if (!List.of("low", "medium", "high").contains(imageQuality)) {
            throw new BusinessException("image-2 图片质量仅支持 low、medium、high");
        }
    }

    private List<ReferenceImage> buildReferenceImages(CreateSessionBO createSessionBO) {
        if (!createSessionBO.getReferenceImages().isEmpty()) {
            List<ReferenceImage> referenceImages = new ArrayList<>();
            for (int index = 0; index < createSessionBO.getReferenceImages().size(); index++) {
                var referenceImageBO = createSessionBO.getReferenceImages().get(index);
                ReferenceImage referenceImage = new ReferenceImage();
                referenceImage.setImageUrl(referenceImageBO.getImageUrl());
                referenceImage.setName(StringUtils.hasText(referenceImageBO.getName())
                        ? referenceImageBO.getName()
                        : "参考图" + (index + 1));
                referenceImages.add(referenceImage);
            }
            return referenceImages;
        }
        List<ReferenceImage> referenceImages = new ArrayList<>();
        for (int index = 0; index < createSessionBO.getReferenceImageUrls().size(); index++) {
            ReferenceImage referenceImage = new ReferenceImage();
            referenceImage.setImageUrl(createSessionBO.getReferenceImageUrls().get(index));
            referenceImage.setName("参考图" + (index + 1));
            referenceImages.add(referenceImage);
        }
        return referenceImages;
    }

    private List<KeyframeResult> buildKeyframes(
            List<KeyframeBO> keyframeBOList,
            List<ReferenceImage> referenceImages,
            String imageSize,
            String imageQuality) {
        List<KeyframeResult> keyframes = new ArrayList<>();
        for (int index = 0; index < keyframeBOList.size(); index++) {
            KeyframeBO keyframeBO = keyframeBOList.get(index);
            // 领域对象保存业务处理状态；每个关键帧共享会话级主体参考图，只变化提示词。
            KeyframeResult keyframe = new KeyframeResult();
            keyframe.setIndex(index + 1);
            keyframe.setPrompt(keyframeBO.getPrompt());
            keyframe.setReferenceImages(referenceImages);
            keyframe.setReferenceImageUrls(referenceImages.stream().map(ReferenceImage::getImageUrl).toList());
            keyframe.setImageSize(imageSize);
            keyframe.setImageQuality(imageQuality);
            keyframe.setStatus(GenerationStatusEnum.DRAFT);
            keyframe.setUpdatedAt(Instant.now());
            keyframes.add(keyframe);
        }
        return keyframes;
    }

    private List<KeyframeResult> buildReadyKeyframes(List<String> keyframeImageUrls) {
        List<KeyframeResult> keyframes = new ArrayList<>();
        for (int index = 0; index < keyframeImageUrls.size(); index++) {
            KeyframeResult keyframe = new KeyframeResult();
            keyframe.setIndex(index + 1);
            keyframe.setPrompt("用户选择关键帧 " + (index + 1));
            keyframe.setGeneratedImageUrl(keyframeImageUrls.get(index));
            keyframe.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
            keyframe.setUpdatedAt(Instant.now());
            keyframes.add(keyframe);
        }
        return keyframes;
    }

    private GenerationSession findSession(String sessionId) {
        return generationSessionService.getById(sessionId);
    }
}
