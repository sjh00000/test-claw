package com.example.aigenstudio.bo;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserUpdateBO extends AdminOperatorBO {

    @NotNull(message = "用户不能为空")
    private Long userId;

    @Min(value = 0, message = "图片剩余次数不能小于 0")
    private Integer imageRemainingCount;

    @Min(value = 0, message = "视频剩余次数不能小于 0")
    private Integer videoRemainingCount;
}
