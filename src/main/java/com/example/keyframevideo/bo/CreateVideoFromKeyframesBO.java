package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CreateVideoFromKeyframesBO {

    // 视频整体描述，最终进入 Seedance 的 text content。
    @NotBlank(message = "视频描述不能为空")
    private String videoPrompt;

    // 参与视频生成的主体/角色参考图，Seedance 会结合名称和描述理解“谁长什么样”。
    @Valid
    private List<ReferenceImageBO> referenceImages = new ArrayList<>();

    // 已生成或上传的关键帧图对象，包含图片地址和顺序名称。
    @Valid
    private List<VideoKeyframeImageBO> keyframeImages = new ArrayList<>();

    // 兼容旧前端：仅传图片 URL 时，后端会自动补成“用户选择关键帧 N”。
    private List<String> keyframeImageUrls = new ArrayList<>();

    // Seedance 视频时长，单位秒，当前按厂商 r2v 限制为 4 到 15。
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

    public void setReferenceImages(List<ReferenceImageBO> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }

    public void setKeyframeImages(List<VideoKeyframeImageBO> keyframeImages) {
        this.keyframeImages = keyframeImages == null ? new ArrayList<>() : keyframeImages;
    }
}
