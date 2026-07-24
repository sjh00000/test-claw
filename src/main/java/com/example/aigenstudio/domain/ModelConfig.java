package com.example.aigenstudio.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("model_config")
public class ModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String serviceType;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;
}
