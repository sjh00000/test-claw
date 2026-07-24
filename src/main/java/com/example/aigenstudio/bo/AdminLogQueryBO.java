package com.example.aigenstudio.bo;

import lombok.Data;

@Data
public class AdminLogQueryBO extends AdminOperatorBO {

    private Long userId;
    private String username;
    private String operationType;
}
