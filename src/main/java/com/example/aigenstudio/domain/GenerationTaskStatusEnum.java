package com.example.aigenstudio.domain;

import java.util.List;
import lombok.Getter;

@Getter
public enum GenerationTaskStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
    RUNNING("RUNNING", "生成中"),
    SUCCEEDED("SUCCEEDED", "已完成"),
    FAILED("FAILED", "生成失败"),
    CANCELED("CANCELED", "已取消");

    private final String code;
    private final String desc;

    GenerationTaskStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static GenerationTaskStatusEnum normalize(String providerStatus, String resultUrl, String failReason) {
        if (failReason != null && !failReason.isBlank()) {
            return FAILED;
        }
        if (resultUrl != null && !resultUrl.isBlank()) {
            return SUCCEEDED;
        }
        if (providerStatus == null || providerStatus.isBlank()) {
            return RUNNING;
        }
        String status = providerStatus.trim().toUpperCase();
        if ("SUCCESS".equals(status) || "SUCCEEDED".equals(status) || "COMPLETED".equals(status)) {
            return SUCCEEDED;
        }
        if ("FAIL".equals(status) || "FAILED".equals(status) || "ERROR".equals(status)) {
            return FAILED;
        }
        if ("CANCEL".equals(status) || "CANCELED".equals(status) || "CANCELLED".equals(status)) {
            return CANCELED;
        }
        return RUNNING;
    }

    public static String getDescByCode(String code) {
        for (GenerationTaskStatusEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item.getDesc();
            }
        }
        return code;
    }

    public static List<String> activeCodes() {
        return List.of(SUBMITTED.getCode(), RUNNING.getCode());
    }
}
