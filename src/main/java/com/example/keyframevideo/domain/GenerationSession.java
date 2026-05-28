package com.example.keyframevideo.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerationSession {

    // 会话 ID，贯穿关键帧生成、视频提交、状态查询全流程。
    private String id;
    // 用户输入的视频整体描述，会作为 Seedance content 中的 text。
    private String videoPrompt;
    // 全局主体/角色参考图 URL，所有关键帧生成共用。
    private List<String> referenceImageUrls = new ArrayList<>();
    // 全局主体/角色参考图，包含可读名称，帮助多参考图场景区分角色。
    private List<ReferenceImage> referenceImages = new ArrayList<>();
    // Seedance 视频时长，单位秒。
    private int duration;
    // Seedance 输出清晰度，例如 480p、720p。
    private String resolution;
    // Seedance 输出画幅比例，例如 16:9、9:16、1:1。
    private String ratio;
    // 是否要求 Seedance 生成音频。
    private boolean generateAudio;
    // 是否使用 Seedance fast model。
    private boolean fastMode;
    // 当前会话总状态，用于前端进度展示和后端状态机校验。
    private GenerationStatusEnum status = GenerationStatusEnum.DRAFT;
    // 用户定义的关键帧列表，每个元素最终会生成一张图。
    private List<KeyframeResult> keyframes = new ArrayList<>();
    // Seedance 提交任务后返回的 task_id。
    private String seedanceTaskId;
    // Seedance 成功后返回的视频 URL。
    private String videoUrl;
    // 当前会话级错误信息，避免把厂商 token 或原始异常堆栈暴露给前端。
    private String errorMessage;
    // 会话创建时间。
    private Instant createdAt = Instant.now();
    // 会话更新时间，状态变化时自动刷新。
    private Instant updatedAt = Instant.now();

    public void setStatus(GenerationStatusEnum status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setKeyframes(List<KeyframeResult> keyframes) {
        this.keyframes = keyframes == null ? new ArrayList<>() : keyframes;
    }

    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? new ArrayList<>() : referenceImageUrls;
    }

    public void setReferenceImages(List<ReferenceImage> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }
}
