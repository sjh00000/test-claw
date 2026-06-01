package com.example.keyframevideo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "generation")
public class GenerationProperties {

    private final ImageProvider imageProvider = new ImageProvider();
    private final Seedance seedance = new Seedance();
    private final Assets assets = new Assets();

    @Data
    public static class ImageProvider {
        // gpt-image2 厂商网关域名，例如 https://api.euzhi.com。
        private String baseUrl = "https://api.euzhi.com";
        // gpt-image2 厂商鉴权密钥，只允许通过环境变量注入，禁止写入代码或日志。
        private String apiKey = "";
        // 图片生成模型名称，默认使用用户指定的 gpt-image-2。
        private String model = "gpt-image-2";
        // 图片编辑接口路径，对应 image2 示例中的 /v1/images/edits。
        private String editEndpoint = "/v1/images/edits";
        // 图片生成接口路径，用于先生成主体/角色参考图。
        private String generationEndpoint = "/v1/images/generations";
        // image2 输出尺寸。
        private String size = "1024x1024";
        // image2 输出质量。
        private String quality = "medium";
        // b64_json 输出模式：url=落盘后返回图片 URL；base64=直接返回 data:image/png;base64。
        private String b64JsonOutputMode = "url";
        // image-2 单次调用超时时间，单位秒，超过后允许前端重新生成对应帧。
        private int requestTimeoutSeconds = 300;
    }

    @Data
    public static class Seedance {
        // Seedance 厂商网关域名，对应文档中的 BASE_URL。
        private String baseUrl = "";
        // Seedance Bearer Token，只允许通过环境变量注入。
        private String apiKey = "";
        // 标准质量视频模型。
        private String model = "doubao-seedance-2-0-260128";
        // 快速生成视频模型，由前端 fastMode 控制是否使用。
        private String fastModel = "doubao-seedance-2-0-fast-260128";
        // 建议轮询间隔，前端当前用 5 秒主动刷新，后续可由接口下发此值。
        private int pollIntervalSeconds = 15;

    }

    @Data
    public static class Assets {
        // 模型返回 b64_json 时的本地图片保存目录，默认放在项目运行目录下。
        private String imageDir = "generated-assets/images";
        // 本地图片对外访问前缀，前端和 Seedance 会使用该 URL 访问图片。
        private String imageUrlPrefix = "/assets/images";
        // 对外访问基础地址；为空时按当前后端请求自动拼接，例如 http://localhost:8080。
        private String publicBaseUrl = "";
    }
}
