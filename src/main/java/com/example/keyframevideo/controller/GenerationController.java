package com.example.keyframevideo.controller;

import com.example.keyframevideo.bo.CreateSessionBO;
import com.example.keyframevideo.bo.CreateVideoFromKeyframesBO;
import com.example.keyframevideo.bo.GenerateReferenceImageBO;
import com.example.keyframevideo.common.R;
import com.example.keyframevideo.facade.GenerationWorkflowFacade;
import com.example.keyframevideo.vo.GenerationSessionVO;
import com.example.keyframevideo.vo.ReferenceImageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping("/{sessionId}/keyframes")
    public R<GenerationSessionVO> generateKeyframes(@PathVariable String sessionId) {
        // 逐张调用 gpt-image2 生成关键帧图。
        return R.ok(generationWorkflowFacade.generateKeyframes(sessionId));
    }

    @PostMapping("/{sessionId}/video")
    public R<GenerationSessionVO> submitVideo(@PathVariable String sessionId) {
        // 把已生成的关键帧提交给 Seedance 生成视频。
        return R.ok(generationWorkflowFacade.submitVideo(sessionId));
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

    @GetMapping("/{sessionId}")
    public R<GenerationSessionVO> getSession(@PathVariable String sessionId) {
        return R.ok(generationWorkflowFacade.getSession(sessionId));
    }
}
