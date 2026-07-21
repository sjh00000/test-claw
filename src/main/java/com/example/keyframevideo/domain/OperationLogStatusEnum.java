package com.example.keyframevideo.domain;

import lombok.Getter;

@Getter
public enum OperationLogStatusEnum {

    SUCCESS("SUCCESS", "成功"),
    FAILURE("FAILURE", "失败");

    private final String code;
    private final String desc;

    OperationLogStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
