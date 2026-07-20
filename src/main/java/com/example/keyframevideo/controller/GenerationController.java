package com.example.keyframevideo.controller;

import com.example.keyframevideo.bo.TextToImageBO;
import com.example.keyframevideo.bo.TextToVideoBO;
import com.example.keyframevideo.bo.VideoStatusBO;
import com.example.keyframevideo.common.R;
import com.example.keyframevideo.facade.GenerationFacade;
import com.example.keyframevideo.vo.ImageGenerationVO;
import com.example.keyframevideo.vo.VideoGenerationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationFacade generationFacade;

    @PostMapping("/images")
    public R<ImageGenerationVO> generateImage(@Valid @RequestBody TextToImageBO request) {
        // 文生图独立入口：参考图可选，不依赖任何会话或视频流程。
        return R.ok(generationFacade.generateImage(request));
    }

    @PostMapping("/videos")
    public R<VideoGenerationVO> generateVideo(@Valid @RequestBody TextToVideoBO request) {
        // 文生视频独立入口：参考图可选，直接提交厂商任务。
        return R.ok(generationFacade.generateVideo(request));
    }

    @PostMapping("/videos/status")
    public R<VideoGenerationVO> queryVideoStatus(@Valid @RequestBody VideoStatusBO request) {
        // 按厂商任务 ID 查询视频状态，不依赖本地会话。
        return R.ok(generationFacade.queryVideoStatus(request));
    }
}
