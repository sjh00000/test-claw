package com.example.keyframevideo.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class OperationLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String operationType;
    private String operationName;
    private String targetType;
    private String targetId;
    private String status;
    private String requestSummary;
    private String responseSummary;
    private String errorMessage;
    private Long durationMs;
    private Instant createdAt;
}
