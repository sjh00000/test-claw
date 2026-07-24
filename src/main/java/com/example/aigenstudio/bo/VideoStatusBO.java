package com.example.aigenstudio.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VideoStatusBO {

    // 本地任务中心的任务 ID。
    @NotNull(message = "视频任务 ID 不能为空")
    private Long taskId;
}
