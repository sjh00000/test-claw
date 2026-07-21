package com.example.keyframevideo.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TextToImageBO {

    // 文生图提示词；有参考图时作为编辑提示词，无参考图时作为生成提示词。
    @NotBlank(message = "图片提示词不能为空")
    private String prompt;

    // 可选参考图；为空时走纯文生图，有值时走参考图编辑。
    @Valid
    private List<ReferenceImageBO> referenceImages = new ArrayList<>();

    // 图片输出尺寸。
    private String imageSize = "1024x1024";

    // 图片输出质量。
    private String imageQuality = "medium";

    public void setReferenceImages(List<ReferenceImageBO> referenceImages) {
        this.referenceImages = referenceImages == null ? new ArrayList<>() : referenceImages;
    }

}
