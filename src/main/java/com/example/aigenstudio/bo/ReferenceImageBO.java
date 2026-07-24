package com.example.aigenstudio.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReferenceImageBO {

    // 参考图地址，可为后端生成的 URL、远程 URL 或前端上传后转成的 data:image base64。
    @NotBlank(message = "参考图地址不能为空")
    private String imageUrl;

    // 参考图名称，用于提示模型理解多参考图对应关系，例如“沈砚参考图”“林槐参考图”。
    private String name;
}
