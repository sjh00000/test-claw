package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerationTaskStatusBO {

    @NotNull(message = "任务 ID 不能为空")
    private Long taskId;
}
