package com.example.keyframevideo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.domain.ModelConfig;
import com.example.keyframevideo.domain.ServiceTypeEnum;
import com.example.keyframevideo.mapper.ModelConfigMapper;
import com.example.keyframevideo.service.ModelConfigService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigService {

    @Override
    public ModelConfig getByServiceType(ServiceTypeEnum serviceTypeEnum) {
        return lambdaQuery()
                .eq(ModelConfig::getServiceType, serviceTypeEnum.getCode())
                .one();
    }

    @Override
    public ModelConfig getEnabledConfig(ServiceTypeEnum serviceTypeEnum) {
        return lambdaQuery()
                .eq(ModelConfig::getServiceType, serviceTypeEnum.getCode())
                .eq(ModelConfig::getEnabled, true)
                .one();
    }

    @Override
    public List<ModelConfig> listByServiceTypeOrder() {
        return lambdaQuery()
                .orderByAsc(ModelConfig::getServiceType)
                .list();
    }
}
