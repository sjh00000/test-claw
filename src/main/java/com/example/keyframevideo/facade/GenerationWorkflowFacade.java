package com.example.keyframevideo.facade;

import com.example.keyframevideo.bo.CreateSessionBO;
import com.example.keyframevideo.bo.CreateVideoFromKeyframesBO;
import com.example.keyframevideo.bo.GenerateKeyframeBO;
import com.example.keyframevideo.bo.GenerateReferenceImageBO;
import com.example.keyframevideo.bo.KeyframeBO;
import com.example.keyframevideo.bo.ReferenceImageBO;
import com.example.keyframevideo.bo.VideoKeyframeImageBO;
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
import java.util.Objects;
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

    public GenerationSessionVO generateKeyframe(String sessionId, int frameIndex, GenerateKeyframeBO generateKeyframeBO) {
        validateImageOptions(generateKeyframeBO.getImageSize(), generateKeyframeBO.getImageQuality());
        GenerationSession session = findSessionOrCreate(sessionId);
        if (session.getStatus() == GenerationStatusEnum.SUBMITTING_VIDEO || session.getStatus() == GenerationStatusEnum.VIDEO_RUNNING) {
            throw new BusinessException("视频生成中，不允许重新生成关键帧");
        }
        if (!StringUtils.hasText(generateKeyframeBO.getPrompt())) {
            throw new BusinessException("第 " + frameIndex + " 个关键帧描述不能为空");
        }

        session.setErrorMessage(null);
        KeyframeResult keyframe = resolveRequestKeyframe(session, frameIndex);
        // 单帧重生成以本次请求体为准，避免用户修改前端描述或 image-2 参数后仍使用会话创建时的旧值。
        keyframe.setPrompt(generateKeyframeBO.getPrompt().trim());
        keyframe.setImageSize(generateKeyframeBO.getImageSize());
        keyframe.setImageQuality(generateKeyframeBO.getImageQuality());
        keyframe.setRequestId(resolveKeyframeRequestId(generateKeyframeBO));
        List<ReferenceImage> currentReferenceImages = resolveCurrentReferenceImages(generateKeyframeBO);
        // 单帧生成必须使用本次请求携带的参考图，不能因为历史会话快照影响当前调用。
        keyframe.setReferenceImages(currentReferenceImages);
        keyframe.setReferenceImageUrls(currentReferenceImages.stream().map(ReferenceImage::getImageUrl).toList());
        session.setReferenceImages(currentReferenceImages);
        session.setReferenceImageUrls(currentReferenceImages.stream().map(ReferenceImage::getImageUrl).toList());
        session.setStatus(GenerationStatusEnum.GENERATING_KEYFRAMES);
        generationSessionService.saveOrUpdate(session);
        // 单帧生成用于前端逐帧点击和失败重试，只影响当前帧状态。
        KeyframeGenerationResult generationResult = generateSingleKeyframe(session, keyframe, keyframe.getRequestId());
        if (generationResult == KeyframeGenerationResult.STALE) {
            return assembler.toVO(findSession(sessionId));
        }
        if (session.getKeyframes().stream().allMatch(item -> item.getStatus() == GenerationStatusEnum.KEYFRAMES_READY)) {
            session.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
        } else if (session.getStatus() != GenerationStatusEnum.FAILED) {
            session.setStatus(GenerationStatusEnum.DRAFT);
        }
        generationSessionService.saveOrUpdate(session);
        return assembler.toVO(session);
    }

    private GenerationSessionVO submitVideo(String sessionId) {
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
        session.setReferenceImages(buildVideoReferenceImages(createVideoFromKeyframesBO));
        session.setReferenceImageUrls(session.getReferenceImages().stream().map(ReferenceImage::getImageUrl).toList());
        session.setKeyframes(buildReadyKeyframes(createVideoFromKeyframesBO));
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
            if (!StringUtils.hasText(status.getVideoUrl())) {
                log.warn("Seedance 视频任务已成功但未解析到视频地址，sessionId={}, taskId={}",
                        sessionId, session.getSeedanceTaskId());
            }
            session.setVideoUrl(status.getVideoUrl());
            session.setStatus(GenerationStatusEnum.SUCCEEDED);
            generationSessionService.saveOrUpdate(session);
            log.info("Seedance 视频生成成功，sessionId={}, taskId={}, videoUrl={}",
                    sessionId, session.getSeedanceTaskId(), status.getVideoUrl());
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

    public GenerationSessionVO cancelVideo(String sessionId) {
        GenerationSession session = findSession(sessionId);
        if (session.getStatus() != GenerationStatusEnum.SUBMITTING_VIDEO && session.getStatus() != GenerationStatusEnum.VIDEO_RUNNING) {
            throw new BusinessException("当前视频任务状态不允许取消");
        }
        if (StringUtils.hasText(session.getSeedanceTaskId())) {
            // 已拿到 Seedance task_id 时，调用厂商取消接口，停止上游生成并触发退还预扣费。
            seedanceClient.cancel(session.getSeedanceTaskId());
        } else {
            log.info("视频任务尚未拿到 Seedance taskId，仅取消本地等待状态，sessionId={}", sessionId);
        }
        session.setStatus(GenerationStatusEnum.CANCELLED);
        session.setErrorMessage("用户已取消视频生成");
        generationSessionService.saveOrUpdate(session);
        log.info("视频生成任务已取消，sessionId={}, taskId={}", sessionId, session.getSeedanceTaskId());
        return assembler.toVO(session);
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
        if (createSessionBO.getDuration() < 4 || createSessionBO.getDuration() > 15) {
            throw new BusinessException("视频时长需为 4 到 15 秒之间的整数");
        }
        // image2 edits 接口依赖主体参考图文件；参考图是会话级主体/角色约束，所有关键帧共用。
        if (createSessionBO.getReferenceImageUrls().isEmpty() && createSessionBO.getReferenceImages().isEmpty()) {
            throw new BusinessException("至少需要 1 张主体参考图");
        }
    }

    private void validateVideoRequest(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        int keyframeImageCount = resolveVideoKeyframeImageCount(createVideoFromKeyframesBO);
        if (keyframeImageCount == 0) {
            throw new BusinessException("关键帧图不能为空");
        }
        if (keyframeImageCount > 50) {
            throw new BusinessException("关键帧图最多支持 50 张");
        }
        if (!createVideoFromKeyframesBO.getKeyframeImages().isEmpty()
                && createVideoFromKeyframesBO.getKeyframeImages().stream().anyMatch(item -> !StringUtils.hasText(item.getImageUrl()))) {
            throw new BusinessException("关键帧图片地址不能为空");
        }
        if (createVideoFromKeyframesBO.getKeyframeImages().isEmpty()
                && createVideoFromKeyframesBO.getKeyframeImageUrls().stream().anyMatch(item -> !StringUtils.hasText(item))) {
            throw new BusinessException("关键帧图片地址不能为空");
        }
        if (!List.of("480p", "720p").contains(createVideoFromKeyframesBO.getResolution())) {
            throw new BusinessException("视频清晰度仅支持 480p 或 720p");
        }
        if (!List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(createVideoFromKeyframesBO.getRatio())) {
            throw new BusinessException("视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive");
        }
        if (createVideoFromKeyframesBO.getDuration() < 4 || createVideoFromKeyframesBO.getDuration() > 15) {
            throw new BusinessException("视频时长需为 4 到 15 秒之间的整数");
        }
    }

    private int resolveVideoKeyframeImageCount(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        // 新版前端传 keyframeImages，旧版兼容 keyframeImageUrls；数量校验必须按实际使用的集合计算。
        if (!createVideoFromKeyframesBO.getKeyframeImages().isEmpty()) {
            return createVideoFromKeyframesBO.getKeyframeImages().size();
        }
        return createVideoFromKeyframesBO.getKeyframeImageUrls().size();
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
            return buildReferenceImages(createSessionBO.getReferenceImages());
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

    private List<ReferenceImage> resolveCurrentReferenceImages(GenerateKeyframeBO generateKeyframeBO) {
        if (generateKeyframeBO.getReferenceImages().isEmpty()) {
            throw new BusinessException("至少需要 1 张主体参考图");
        }
        if (generateKeyframeBO.getReferenceImages().stream().anyMatch(item -> !StringUtils.hasText(item.getImageUrl()))) {
            throw new BusinessException("参考图地址不能为空");
        }
        return buildReferenceImages(generateKeyframeBO.getReferenceImages());
    }

    private KeyframeResult resolveRequestKeyframe(GenerationSession session, int frameIndex) {
        KeyframeResult keyframe = session.getKeyframes().stream()
                .filter(item -> item.getIndex() == frameIndex)
                .findFirst()
                .orElse(null);
        if (keyframe != null) {
            return keyframe;
        }
        // 单帧生成以本次请求为准；历史会话缺少该帧时直接补齐，避免旧 keyframeCount 影响用户当前操作。
        keyframe = new KeyframeResult();
        keyframe.setIndex(frameIndex);
        keyframe.setStatus(GenerationStatusEnum.DRAFT);
        keyframe.setUpdatedAt(Instant.now());
        session.getKeyframes().add(keyframe);
        session.getKeyframes().sort((left, right) -> Integer.compare(left.getIndex(), right.getIndex()));
        return keyframe;
    }

    private String resolveKeyframeRequestId(GenerateKeyframeBO generateKeyframeBO) {
        if (StringUtils.hasText(generateKeyframeBO.getRequestId())) {
            return generateKeyframeBO.getRequestId();
        }
        return UUID.randomUUID().toString();
    }

    private List<ReferenceImage> buildReferenceImages(List<ReferenceImageBO> referenceImageBOList) {
        List<ReferenceImage> referenceImages = new ArrayList<>();
        for (int index = 0; index < referenceImageBOList.size(); index++) {
            var referenceImageBO = referenceImageBOList.get(index);
            ReferenceImage referenceImage = new ReferenceImage();
            referenceImage.setImageUrl(referenceImageBO.getImageUrl());
            referenceImage.setName(StringUtils.hasText(referenceImageBO.getName())
                    ? referenceImageBO.getName()
                    : "参考图" + (index + 1));
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

    private List<ReferenceImage> buildVideoReferenceImages(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        List<ReferenceImage> referenceImages = new ArrayList<>();
        for (int index = 0; index < createVideoFromKeyframesBO.getReferenceImages().size(); index++) {
            var referenceImageBO = createVideoFromKeyframesBO.getReferenceImages().get(index);
            ReferenceImage referenceImage = new ReferenceImage();
            referenceImage.setImageUrl(referenceImageBO.getImageUrl());
            referenceImage.setName(StringUtils.hasText(referenceImageBO.getName())
                    ? referenceImageBO.getName()
                    : "参考图" + (index + 1));
            referenceImages.add(referenceImage);
        }
        return referenceImages;
    }

    private List<KeyframeResult> buildReadyKeyframes(CreateVideoFromKeyframesBO createVideoFromKeyframesBO) {
        if (!createVideoFromKeyframesBO.getKeyframeImages().isEmpty()) {
            return buildReadyKeyframesFromObjects(createVideoFromKeyframesBO.getKeyframeImages());
        }
        return buildReadyKeyframes(createVideoFromKeyframesBO.getKeyframeImageUrls());
    }

    private List<KeyframeResult> buildReadyKeyframesFromObjects(List<VideoKeyframeImageBO> keyframeImages) {
        List<KeyframeResult> keyframes = new ArrayList<>();
        for (int index = 0; index < keyframeImages.size(); index++) {
            VideoKeyframeImageBO keyframeImage = keyframeImages.get(index);
            KeyframeResult keyframe = new KeyframeResult();
            keyframe.setIndex(index + 1);
            keyframe.setPrompt(resolveVideoKeyframeName(keyframeImage, index));
            keyframe.setGeneratedImageUrl(keyframeImage.getImageUrl());
            keyframe.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
            keyframe.setUpdatedAt(Instant.now());
            keyframes.add(keyframe);
        }
        return keyframes;
    }

    private String resolveVideoKeyframeName(VideoKeyframeImageBO keyframeImage, int index) {
        // 视频描述按“关键帧 1/2/3”引用画面；新版前端会显式传入该标签，旧入参则按提交顺序兜底命名。
        if (StringUtils.hasText(keyframeImage.getName())) {
            return keyframeImage.getName();
        }
        return "关键帧 " + (index + 1);
    }

    private List<KeyframeResult> buildReadyKeyframes(List<String> keyframeImageUrls) {
        List<KeyframeResult> keyframes = new ArrayList<>();
        for (int index = 0; index < keyframeImageUrls.size(); index++) {
            KeyframeResult keyframe = new KeyframeResult();
            keyframe.setIndex(index + 1);
            keyframe.setPrompt("关键帧 " + (index + 1));
            keyframe.setGeneratedImageUrl(keyframeImageUrls.get(index));
            keyframe.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
            keyframe.setUpdatedAt(Instant.now());
            keyframes.add(keyframe);
        }
        return keyframes;
    }

    private KeyframeGenerationResult generateSingleKeyframe(GenerationSession session, KeyframeResult keyframe, String requestId) {
        try {
            keyframe.setStatus(GenerationStatusEnum.GENERATING_KEYFRAMES);
            keyframe.setErrorMessage(null);
            String generatedImageUrl = imageProviderClient.generate(keyframe);
            if (!isLatestKeyframeRequest(session.getId(), keyframe.getIndex(), requestId)) {
                // 前端取消或重新生成后，旧 image-2 请求可能晚返回；旧结果不能覆盖最新帧图。
                log.info("忽略过期关键帧生成结果，sessionId={}, frameIndex={}, requestId={}",
                        session.getId(), keyframe.getIndex(), requestId);
                return KeyframeGenerationResult.STALE;
            }
            keyframe.setGeneratedImageUrl(generatedImageUrl);
            keyframe.setStatus(GenerationStatusEnum.KEYFRAMES_READY);
            generationSessionService.saveOrUpdate(session);
            log.info("关键帧生成成功，sessionId={}, frameIndex={}", session.getId(), keyframe.getIndex());
            return KeyframeGenerationResult.SUCCESS;
        } catch (Exception ex) {
            if (!isLatestKeyframeRequest(session.getId(), keyframe.getIndex(), requestId)) {
                // 旧请求失败也不能把最新请求的状态覆盖成失败。
                log.info("忽略过期关键帧生成失败，sessionId={}, frameIndex={}, requestId={}, reason={}",
                        session.getId(), keyframe.getIndex(), requestId, ex.getMessage());
                return KeyframeGenerationResult.STALE;
            }
            keyframe.setErrorMessage(ex.getMessage());
            keyframe.setStatus(GenerationStatusEnum.FAILED);
            session.setErrorMessage("第 " + keyframe.getIndex() + " 个关键帧生成失败：" + ex.getMessage());
            session.setStatus(GenerationStatusEnum.FAILED);
            generationSessionService.saveOrUpdate(session);
            log.warn("关键帧生成失败，sessionId={}, frameIndex={}, reason={}", session.getId(), keyframe.getIndex(), ex.getMessage());
            return KeyframeGenerationResult.FAILED;
        }
    }

    private boolean isLatestKeyframeRequest(String sessionId, int frameIndex, String requestId) {
        GenerationSession latestSession = findSession(sessionId);
        return latestSession.getKeyframes().stream()
                .filter(item -> item.getIndex() == frameIndex)
                .findFirst()
                .map(item -> Objects.equals(item.getRequestId(), requestId))
                .orElse(false);
    }

    private GenerationSession findSession(String sessionId) {
        return generationSessionService.getById(sessionId);
    }

    private GenerationSession findSessionOrCreate(String sessionId) {
        try {
            return generationSessionService.getById(sessionId);
        } catch (BusinessException ex) {
            if (ex.getCode() != 404) {
                throw ex;
            }
            // 单帧生成由前端提交完整参数；会话不存在时按传入 ID 新建轻量会话，避免历史缓存成为硬依赖。
            GenerationSession session = new GenerationSession();
            session.setId(sessionId);
            session.setVideoPrompt("单帧生成");
            session.setDuration(5);
            session.setResolution("720p");
            session.setRatio("adaptive");
            session.setGenerateAudio(true);
            session.setStatus(GenerationStatusEnum.DRAFT);
            return session;
        }
    }

    private enum KeyframeGenerationResult {
        SUCCESS,
        FAILED,
        STALE
    }
}
