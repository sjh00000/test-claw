package com.example.keyframevideo.bo;

import lombok.Data;

@Data
public class ProviderConfigBO {

    // 厂商网关域名，由前端用户填写，不落库保存。
    private String baseUrl;

    // 厂商鉴权密钥，由前端用户填写，只用于本次请求。
    private String apiKey;

    // 厂商模型名称，由前端用户填写。
    private String model;
}
