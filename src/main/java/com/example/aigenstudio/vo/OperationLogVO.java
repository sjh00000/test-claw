package com.example.aigenstudio.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class OperationLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String operationType;
    private String operationName;
    private String requestBody;
    private String responseBody;
    private Instant createdAt;
}
