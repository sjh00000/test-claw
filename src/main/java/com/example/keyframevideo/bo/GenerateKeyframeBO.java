package com.example.keyframevideo.bo;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class GenerateKeyframeBO {

    // 当前关键帧最新画面描述；单帧重生成时必须用前端最新输入覆盖会话旧值。
    @NotBlank(message = "关键帧描述不能为空")
    private String prompt;

    // 本次生成当前帧实际选中的参考图；避免复用创建会话时的旧参考图快照。
    private List<ReferenceImageBO> referenceImages = new ArrayList<>();

    // 本次生成当前帧实际选择的 image-2 输出尺寸；避免复用创建会话时的旧尺寸。
    private String imageSize = "1024x1024";

    // 本次生成当前帧实际选择的 image-2 输出质量；避免复用创建会话时的旧质量。
    private String imageQuality = "medium";

    // 前端每次点击生成时生成的请求标识，用于防止取消后旧请求晚返回覆盖新结果。
    private String requestId;
}
