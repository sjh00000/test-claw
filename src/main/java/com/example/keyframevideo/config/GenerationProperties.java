package com.example.keyframevideo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "generation")
public class GenerationProperties {

    private final ImageProvider imageProvider = new ImageProvider();
    private final Seedance seedance = new Seedance();

    @Data
    public static class ImageProvider {
        // gpt-image2 厂商网关域名，由部署环境配置。
        private String baseUrl = "";
        // gpt-image2 厂商鉴权密钥，由部署环境配置，禁止写入代码或日志。
        private String apiKey = "";
        // 图片生成模型名称，由部署环境配置。
        private String model = "";
        // 图片编辑接口路径，对应 image2 示例中的 /v1/images/edits。
        private String editEndpoint = "/v1/images/edits";
        // 图片生成接口路径，用于先生成主体/角色参考图。
        private String generationEndpoint = "/v1/images/generations";
        // image2 输出尺寸。
        private String size = "1024x1024";
        // image2 输出质量。
        private String quality = "medium";
        // image-2 单次调用超时时间，单位秒，超过后允许前端重新生成对应帧。
        private int requestTimeoutSeconds = 300;
    }

    @Data
    public static class Seedance {
        // Seedance 厂商网关域名，由部署环境配置。
        private String baseUrl = "";
        // Seedance Bearer Token，由部署环境配置。
        private String apiKey = "";
        // 标准质量视频模型，由部署环境配置。
        private String model = "";
        // 建议轮询间隔，前端当前用 5 秒主动刷新，后续可由接口下发此值。
        private int pollIntervalSeconds = 15;

    }
}
