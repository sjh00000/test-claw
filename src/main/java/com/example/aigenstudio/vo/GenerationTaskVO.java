package com.example.aigenstudio.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class GenerationTaskVO {

    private Long taskId;
    private Long userId;
    private String username;
    private String taskType;
    private String taskName;
    private String providerTaskId;
    private String status;
    private String statusName;
    private String resultUrl;
    private String failReason;
    private Instant createdAt;
    private Instant updatedAt;
}
