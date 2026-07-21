package com.example.keyframevideo.bo;

import lombok.Data;

@Data
public class ProviderConfigBO {

    // 厂商网关域名，由管理员在后台配置后从数据库读取。
    private String baseUrl;

    // 厂商鉴权密钥，由管理员维护，普通生成请求不接收前端传入密钥。
    private String apiKey;

    // 厂商模型名称，由管理员维护。
    private String model;
}
