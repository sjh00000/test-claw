package com.example.keyframevideo.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class AdminUserVO {

    private Long userId;
    private String username;
    private Boolean admin;
    private Integer imageCallLimit;
    private Integer videoCallLimit;
    private Instant createdAt;
}
