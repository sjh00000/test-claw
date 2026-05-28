package com.example.keyframevideo.domain;

import lombok.Data;

@Data
public class SeedanceTaskStatus {

    // Seedance 任务 ID。
    private String taskId;
    // Seedance 原始任务状态，例如 SUCCESS、FAILED、RUNNING。
    private String status;
    // Seedance 查询返回的进度，例如 30%、100%。
    private String progress;
    // 视频生成成功后的播放或下载地址。
    private String videoUrl;
    // 视频任务失败原因。
    private String failReason;

}
