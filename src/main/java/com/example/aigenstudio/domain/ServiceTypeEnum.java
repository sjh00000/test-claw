package com.example.aigenstudio.domain;

import com.example.aigenstudio.exception.BusinessException;
import java.util.Arrays;
import lombok.Getter;

@Getter
public enum ServiceTypeEnum {

    IMAGE("IMAGE", "图片服务"),
    VIDEO("VIDEO", "视频服务");

    private final String code;
    private final String desc;

    ServiceTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ServiceTypeEnum requireByCode(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException("服务类型不正确"));
    }
}
