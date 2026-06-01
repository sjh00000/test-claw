package com.example.keyframevideo.bo;

import lombok.Data;

@Data
public class KeyframeBO {

    // 单张关键帧的画面描述，每次 gpt-image2 调用只生成这一张图。
    private String prompt;

}
