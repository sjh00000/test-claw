package com.example.keyframevideo.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CreateVideoFromKeyframesBO {

    // 视频整体描述，最终进入 Seedance 的 text content。
    @NotBlank(message = "视频描述不能为空")
    private String videoPrompt;

    // 已生成或上传的关键帧图，Seedance 会按 reference_image 使用。
    @NotEmpty(message = "关键帧图不能为空")
    private List<String> keyframeImageUrls = new ArrayList<>();

    // Seedance 视频时长，支持 -1 智能时长或 4 到 15 秒。
    private int duration = 5;
    // Seedance 输出清晰度，当前支持 480p / 720p。
    private String resolution = "720p";
    // Seedance 输出比例，当前支持 adaptive、16:9、4:3、1:1、3:4、9:16、21:9。
    private String ratio = "adaptive";
    // 是否让 Seedance 同步生成音频。
    private boolean generateAudio = true;
    // 是否使用 Seedance fast model。
    private boolean fastMode;

    public void setKeyframeImageUrls(List<String> keyframeImageUrls) {
        this.keyframeImageUrls = keyframeImageUrls == null ? new ArrayList<>() : keyframeImageUrls;
    }
}
