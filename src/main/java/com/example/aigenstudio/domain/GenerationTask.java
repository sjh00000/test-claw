package com.example.aigenstudio.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("generation_task")
public class GenerationTask {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long userId;
    private String username;
    private String taskType;
    private String providerTaskId;
    private String status;
    private String resultUrl;
    private String failReason;
    private String requestBody;
    private String responseBody;
    private Instant createdAt;
    private Instant updatedAt;
}
