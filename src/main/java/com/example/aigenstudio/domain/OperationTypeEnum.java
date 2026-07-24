package com.example.aigenstudio.domain;

import lombok.Getter;

@Getter
public enum OperationTypeEnum {

    TEXT_TO_IMAGE("TEXT_TO_IMAGE", "文生图"),
    TEXT_TO_VIDEO("TEXT_TO_VIDEO", "文生视频");

    private final String code;
    private final String desc;

    OperationTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(String code) {
        for (OperationTypeEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item.getDesc();
            }
        }
        return code;
    }
}
