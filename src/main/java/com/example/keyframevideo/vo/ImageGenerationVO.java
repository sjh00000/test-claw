package com.example.keyframevideo.vo;

import lombok.Data;

@Data
public class ImageGenerationVO {

    private Long taskId;
    private String status;
    private String imageUrl;
    private String failReason;
}
