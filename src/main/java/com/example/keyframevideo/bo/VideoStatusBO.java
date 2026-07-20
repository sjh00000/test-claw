package com.example.keyframevideo.bo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoStatusBO {

    // Seedance 提交后返回的任务 ID。
    @NotBlank(message = "视频任务 ID 不能为空")
    private String taskId;

    // 查询任务仍需要厂商配置，密钥只随本次请求使用。
    @Valid
    private ProviderConfigBO seedanceConfig = new ProviderConfigBO();

    public void setSeedanceConfig(ProviderConfigBO seedanceConfig) {
        this.seedanceConfig = seedanceConfig == null ? new ProviderConfigBO() : seedanceConfig;
    }
}
