package com.example.aigenstudio.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TextToVideoBO {

    // 视频提示词；参考图为空时按纯文本生成视频，有参考图时按多模态参考生成视频。
    @NotBlank(message = "视频提示词不能为空")
    private String prompt;

    // 可选参考图；支持用户不传参考图直接文生视频。
    @Valid
    private List<ReferenceImageBO> referenceImages = new ArrayList<>();

    private int duration = 5;
    private String resolution = "720p";
    private String ratio = "adaptive";
    private boolean generateAudio = true;

    public void setReferenceImages(List<ReferenceImageBO> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }

}
