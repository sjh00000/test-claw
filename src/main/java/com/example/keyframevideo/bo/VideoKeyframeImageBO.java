package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoKeyframeImageBO {

    // 关键帧图片地址，可为后端生成 URL、远程 URL 或前端上传转成的 data:image base64。
    @NotBlank(message = "关键帧图片地址不能为空")
    private String imageUrl;

    // 关键帧图片名称，用于在 Seedance 多图输入中标记顺序，例如“关键帧 1”。
    private String name;
}
