package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VideoStatusBO {

    // Seedance 提交后返回的任务 ID。
    @NotBlank(message = "视频任务 ID 不能为空")
    private String taskId;
}
