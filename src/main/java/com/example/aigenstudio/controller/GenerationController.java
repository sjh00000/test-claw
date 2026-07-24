package com.example.aigenstudio.controller;

import com.example.aigenstudio.bo.GenerationTaskQueryBO;
import com.example.aigenstudio.bo.GenerationTaskStatusBO;
import com.example.aigenstudio.bo.TextToImageBO;
import com.example.aigenstudio.bo.TextToVideoBO;
import com.example.aigenstudio.bo.VideoStatusBO;
import com.example.aigenstudio.common.R;
import com.example.aigenstudio.facade.GenerationFacade;
import com.example.aigenstudio.log.UserOperationLog;
import com.example.aigenstudio.domain.OperationTypeEnum;
import com.example.aigenstudio.vo.GenerationTaskVO;
import com.example.aigenstudio.vo.ImageGenerationVO;
import com.example.aigenstudio.vo.VideoGenerationVO;
import jakarta.validation.Valid;
import java.util.List;
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
    @UserOperationLog(operationType = OperationTypeEnum.TEXT_TO_IMAGE)
    public R<ImageGenerationVO> generateImage(@Valid @RequestBody TextToImageBO request) {
        // 文生图独立入口：先创建本地任务，再由异步流程完成厂商调用并更新任务状态。
        return R.ok(generationFacade.generateImage(request));
    }

    @PostMapping("/videos")
    @UserOperationLog(operationType = OperationTypeEnum.TEXT_TO_VIDEO)
    public R<VideoGenerationVO> generateVideo(@Valid @RequestBody TextToVideoBO request) {
        // 文生视频独立入口：先返回本地任务 ID，厂商任务提交和状态刷新由任务中心承载。
        return R.ok(generationFacade.generateVideo(request));
    }

    @PostMapping("/videos/status")
    public R<VideoGenerationVO> queryVideoStatus(@Valid @RequestBody VideoStatusBO request) {
        // 前端按本地任务 ID 轮询，后端按需刷新视频厂商状态并返回任务结果。
        return R.ok(generationFacade.queryVideoStatus(request));
    }

    @PostMapping("/tasks/status")
    public R<GenerationTaskVO> queryTaskStatus(@Valid @RequestBody GenerationTaskStatusBO request) {
        // 图片和视频统一按本地任务 ID 查询任务中心状态。
        return R.ok(generationFacade.queryTaskStatus(request));
    }

    @PostMapping("/tasks/active")
    public R<GenerationTaskVO> getCurrentActiveTask() {
        // 生成页恢复时使用当前登录用户解析活跃任务，前端无需也不能传 userId。
        return R.ok(generationFacade.getCurrentActiveTask());
    }

    @PostMapping("/tasks")
    public R<List<GenerationTaskVO>> listTasks(@Valid @RequestBody GenerationTaskQueryBO request) {
        return R.ok(generationFacade.listTasks(request));
    }
}
