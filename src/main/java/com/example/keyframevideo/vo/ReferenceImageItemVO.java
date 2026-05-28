package com.example.keyframevideo.vo;

import lombok.Data;

@Data
public class ReferenceImageItemVO {

    // 参考图名称，例如“沈砚参考图”，用于多参考图时帮助区分主体。
    private String name;

    // 参考图地址。
    private String imageUrl;
}
