package com.example.keyframevideo.vo;

import lombok.Data;

@Data
public class VideoGenerationVO {

    private Long taskId;
    private String providerTaskId;
    private String status;
    private String videoUrl;
    private String failReason;
}
