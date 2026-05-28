package com.example.keyframevideo.domain;

public enum GenerationStatusEnum {
    // 会话已创建，但还没有开始调用厂商。
    DRAFT,
    // 正在逐张调用 gpt-image2 生成关键帧图。
    GENERATING_KEYFRAMES,
    // 所有关键帧图已生成，允许提交 Seedance。
    KEYFRAMES_READY,
    // 正在向 Seedance 提交视频任务。
    SUBMITTING_VIDEO,
    // Seedance 已接收任务，等待轮询结果。
    VIDEO_RUNNING,
    // 视频生成成功。
    SUCCEEDED,
    // 任一关键步骤失败。
    FAILED
}
