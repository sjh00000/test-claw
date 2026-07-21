package com.example.keyframevideo.bo;

import lombok.Data;

@Data
public class AdminLogQueryBO extends AdminOperatorBO {

    private Long userId;
    private String username;
    private String operationType;
}
