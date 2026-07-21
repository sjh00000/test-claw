package com.example.keyframevideo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("app_user")
public class UserInfo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String displayName;
    private String password;
    private Boolean admin;
    private Integer imageCallLimit;
    private Integer videoCallLimit;
    private Instant createdAt;
    private Instant updatedAt;
}
