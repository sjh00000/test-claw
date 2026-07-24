package com.example.aigenstudio.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ModelConfigSaveBO extends AdminOperatorBO {

    @NotBlank(message = "服务类型不能为空")
    private String serviceType;

    @NotBlank(message = "服务地址不能为空")
    private String baseUrl;

    private String apiKey;

    @NotBlank(message = "模型名称不能为空")
    private String model;

    private Boolean enabled = true;
}
