package com.example.keyframevideo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
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
