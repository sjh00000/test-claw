package com.example.aigenstudio.domain;

import lombok.Data;

@Data
public class ReferenceImage {

    // 参考图名称，用于提示词和日志中区分角色/主体，例如“沈砚参考图”。
    private String name;

    // 参考图地址，可为 URL 或 data:image base64。
    private String imageUrl;
}
