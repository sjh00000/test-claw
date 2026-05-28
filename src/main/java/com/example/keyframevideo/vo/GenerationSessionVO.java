package com.example.keyframevideo.vo;

import com.example.keyframevideo.domain.GenerationStatusEnum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerationSessionVO {

    // 会话 ID，前端后续操作都以它作为路径参数。
    private String id;
    // 视频整体描述，回显给前端确认。
    private String videoPrompt;
    // 全局主体/角色参考图 URL，所有关键帧生成共用。
    private List<String> referenceImageUrls = new ArrayList<>();
    // 全局主体/角色参考图对象，包含名称和 URL。
    private List<ReferenceImageItemVO> referenceImages = new ArrayList<>();
    // 视频时长，单位秒。
    private int duration;
    // 输出清晰度。
    private String resolution;
    // 输出比例。
    private String ratio;
    // 是否生成音频。
    private boolean generateAudio;
    // 是否使用快速模型。
    private boolean fastMode;
    // 会话总状态。
    private GenerationStatusEnum status;
    // 关键帧生成结果列表。
    private List<KeyframeVO> keyframes = new ArrayList<>();
    // Seedance 任务 ID。
    private String seedanceTaskId;
    // 成功后的视频 URL。
    private String videoUrl;
    // 可展示给用户的错误信息。
    private String errorMessage;
    // 创建时间。
    private Instant createdAt;
    // 最近更新时间。
    private Instant updatedAt;

    public void setKeyframes(List<KeyframeVO> keyframes) {
        this.keyframes = keyframes == null ? new ArrayList<>() : keyframes;
    }

    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? new ArrayList<>() : referenceImageUrls;
    }

    public void setReferenceImages(List<ReferenceImageItemVO> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }
}
