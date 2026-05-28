package com.example.keyframevideo.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KeyframeResult {

    // 关键帧序号，从 1 开始展示给用户。
    private int index;
    // 用户对当前关键帧的语言描述，用于调用 gpt-image2。
    private String prompt;
    // 当前关键帧的参考图 URL 列表，会透传给 gpt-image2 厂商适配器。
    private List<String> referenceImageUrls = new ArrayList<>();
    // 当前关键帧的参考图对象列表，名称用于提示模型区分多角色参考图。
    private List<ReferenceImage> referenceImages = new ArrayList<>();
    // image-2 输出尺寸，来自用户前端选择。
    private String imageSize;
    // image-2 输出质量，来自用户前端选择。
    private String imageQuality;
    // gpt-image2 生成后的关键帧图片 URL。
    private String generatedImageUrl;
    // 当前关键帧状态，独立于会话状态，便于定位失败帧。
    private GenerationStatusEnum status = GenerationStatusEnum.DRAFT;
    // 当前关键帧失败原因。
    private String errorMessage;
    // 当前关键帧更新时间。
    private Instant updatedAt = Instant.now();

    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? new ArrayList<>() : referenceImageUrls;
    }

    public void setReferenceImages(List<ReferenceImage> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }

    public void setStatus(GenerationStatusEnum status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
