package com.example.keyframevideo.vo;

import com.example.keyframevideo.domain.GenerationStatusEnum;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class KeyframeVO {

    // 关键帧序号，从 1 开始。
    private int index;
    // 当前关键帧提示词。
    private String prompt;
    // 当前关键帧参考图 URL。
    private List<String> referenceImageUrls = new ArrayList<>();
    // 生成后的关键帧图片 URL。
    private String generatedImageUrl;
    // 当前关键帧状态。
    private GenerationStatusEnum status;
    // 当前关键帧失败原因。
    private String errorMessage;
    // 最近更新时间。
    private Instant updatedAt;

    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? new ArrayList<>() : referenceImageUrls;
    }
}
