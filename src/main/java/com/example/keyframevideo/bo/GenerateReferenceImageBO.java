package com.example.keyframevideo.bo;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class GenerateReferenceImageBO {

    // 主体/角色参考图描述，用 image-2 先生成，后续所有关键帧共用。
    @NotBlank(message = "参考图描述不能为空")
    private String prompt;

    // image-2 输出尺寸，例如 1024x1024。
    private String imageSize = "1024x1024";

    // image-2 输出质量，例如 low / medium / high。
    private String imageQuality = "medium";
}
