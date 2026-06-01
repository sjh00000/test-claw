package com.example.keyframevideo.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CreateSessionBO {

    // 视频整体描述，最终进入 Seedance 的 text content。
    @NotBlank(message = "视频描述不能为空")
    private String videoPrompt;

    // 全局主体/角色参考图 URL，所有关键帧生成都会共用这组参考图。
    private List<String> referenceImageUrls = new ArrayList<>();

    // 全局主体/角色参考图对象，支持给每张参考图命名，帮助模型区分多角色参考。
    @Valid
    private List<ReferenceImageBO> referenceImages = new ArrayList<>();

    // 用户声明的关键帧数量，需要与 keyframes.size() 一致。
    @Min(value = 1, message = "至少需要 1 个关键帧")
    @Max(value = 50, message = "关键帧最多支持 50 张")
    private int keyframeCount;

    // 每个关键帧的提示词和参考图输入，后端会按顺序逐张生成。
    @Valid
    @NotEmpty(message = "关键帧列表不能为空")
    private List<KeyframeBO> keyframes = new ArrayList<>();

    // image-2 关键帧图输出尺寸，例如 1024x1024。
    private String imageSize = "1024x1024";

    // image-2 关键帧图输出质量，例如 low / medium / high。
    private String imageQuality = "medium";

    // Seedance 视频时长，单位秒，当前按厂商 r2v 限制为 4 到 15。
    private int duration = 5;
    // Seedance 输出清晰度，当前支持 480p / 720p。
    private String resolution = "720p";
    // Seedance 输出比例，当前支持 adaptive、16:9、4:3、1:1、3:4、9:16、21:9。
    private String ratio = "16:9";
    // 是否让 Seedance 同步生成音频。
    private boolean generateAudio = true;
    // 是否使用 Seedance fast model。
    private boolean fastMode;

    public void setKeyframes(List<KeyframeBO> keyframes) {
        this.keyframes = keyframes == null ? new ArrayList<>() : keyframes;
    }

    public void setReferenceImageUrls(List<String> referenceImageUrls) {
        this.referenceImageUrls = referenceImageUrls == null ? new ArrayList<>() : referenceImageUrls;
    }

    public void setReferenceImages(List<ReferenceImageBO> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }
}
