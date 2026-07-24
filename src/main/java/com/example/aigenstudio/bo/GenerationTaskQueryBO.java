package com.example.aigenstudio.bo;

import lombok.Data;

@Data
public class GenerationTaskQueryBO {

    private String username;
    private String taskType;
    private String status;
}
