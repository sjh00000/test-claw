package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginBO {

    // 登录账号；首次登录时会自动创建用户。
    @NotBlank(message = "用户名不能为空")
    private String username;

    // 登录密码；后端只保存摘要，不保存明文。
    @NotBlank(message = "密码不能为空")
    private String password;

    // 用户展示名；首次创建时为空则使用 username。
    private String displayName;
}
