package com.example.keyframevideo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.keyframevideo.domain.ModelConfig;
import com.example.keyframevideo.domain.ServiceTypeEnum;
import java.util.List;

public interface ModelConfigService extends IService<ModelConfig> {

    ModelConfig getByServiceType(ServiceTypeEnum serviceTypeEnum);

    ModelConfig getEnabledConfig(ServiceTypeEnum serviceTypeEnum);

    List<ModelConfig> listByServiceTypeOrder();
}
