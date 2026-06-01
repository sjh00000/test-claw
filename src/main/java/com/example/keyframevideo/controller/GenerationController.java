package com.example.keyframevideo.controller;

import com.example.keyframevideo.bo.CreateSessionBO;
import com.example.keyframevideo.bo.CreateVideoFromKeyframesBO;
import com.example.keyframevideo.bo.GenerateKeyframeBO;
import com.example.keyframevideo.bo.GenerateReferenceImageBO;
import com.example.keyframevideo.common.R;
import com.example.keyframevideo.facade.GenerationWorkflowFacade;
import com.example.keyframevideo.vo.GenerationSessionVO;
import com.example.keyframevideo.vo.ReferenceImageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generation-sessions")
public class GenerationController {

    private final GenerationWorkflowFacade generationWorkflowFacade;

    public GenerationController(GenerationWorkflowFacade generationWorkflowFacade) {
        this.generationWorkflowFacade = generationWorkflowFacade;
    }

    @PostMapping("/reference-images")
    public R<ReferenceImageVO> generateReferenceImage(@Valid @RequestBody GenerateReferenceImageBO request) {
        // 用 image-2 先生成主体/角色参考图，供后续所有关键帧复用。
        return R.ok(generationWorkflowFacade.generateReferenceImage(request));
    }

    @PostMapping
    public R<GenerationSessionVO> createSession(@Valid @RequestBody CreateSessionBO request) {
        // 创建会话只做参数校验和状态初始化，不触发外部厂商调用。
        return R.ok(generationWorkflowFacade.createSession(request));
    }

    @PostMapping("/{sessionId}/keyframes/{frameIndex}")
    public R<GenerationSessionVO> generateKeyframe(
            @PathVariable String sessionId,
            @PathVariable int frameIndex,
            @Valid @RequestBody GenerateKeyframeBO request) {
        // 单独生成某一帧，使用前端最新描述覆盖会话旧提示词，适合修改描述后重试当前帧。
        return R.ok(generationWorkflowFacade.generateKeyframe(sessionId, frameIndex, request));
    }

    @PostMapping("/video")
    public R<GenerationSessionVO> submitVideoFromKeyframes(@Valid @RequestBody CreateVideoFromKeyframesBO request) {
        // 用户上传或选择关键帧图后，可直接提交 Seedance 生成视频。
        return R.ok(generationWorkflowFacade.submitVideoFromKeyframes(request));
    }

    @PostMapping("/{sessionId}/video/status")
    public R<GenerationSessionVO> refreshVideo(@PathVariable String sessionId) {
        // 查询 Seedance 任务状态，并同步会话状态。
        return R.ok(generationWorkflowFacade.refreshVideo(sessionId));
    }

    @PostMapping("/{sessionId}/video/cancel")
    public R<GenerationSessionVO> cancelVideo(@PathVariable String sessionId) {
        // 用户主动取消 Seedance 视频任务；仅允许提交中或生成中状态取消。
        return R.ok(generationWorkflowFacade.cancelVideo(sessionId));
    }
}
