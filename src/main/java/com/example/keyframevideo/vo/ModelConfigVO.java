package com.example.keyframevideo.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class ModelConfigVO {

    private Long id;
    private String serviceType;
    private String serviceName;
    private String baseUrl;
    private String apiKey;
    private String apiKeyMask;
    private String model;
    private Boolean enabled;
    private Instant updatedAt;
}
