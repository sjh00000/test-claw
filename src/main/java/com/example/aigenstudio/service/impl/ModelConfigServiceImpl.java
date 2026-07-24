package com.example.aigenstudio.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aigenstudio.domain.ModelConfig;
import com.example.aigenstudio.domain.ServiceTypeEnum;
import com.example.aigenstudio.mapper.ModelConfigMapper;
import com.example.aigenstudio.service.ModelConfigService;
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
