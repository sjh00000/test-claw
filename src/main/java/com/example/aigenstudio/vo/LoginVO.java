package com.example.aigenstudio.vo;

import lombok.Data;

@Data
public class LoginVO {

    private Long userId;
    private String username;
    private Boolean admin;
    private String accessToken;
}
