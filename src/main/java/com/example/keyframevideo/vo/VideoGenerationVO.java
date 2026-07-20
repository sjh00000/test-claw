package com.example.keyframevideo.vo;

import lombok.Data;

@Data
public class VideoGenerationVO {

    private String taskId;
    private String status;
    private String videoUrl;
    private String failReason;
}
