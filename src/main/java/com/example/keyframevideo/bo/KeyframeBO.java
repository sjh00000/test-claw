package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KeyframeBO {

    // 单张关键帧的画面描述，每次 gpt-image2 调用只生成这一张图。
    @NotBlank(message = "关键帧描述不能为空")
    private String prompt;

}
