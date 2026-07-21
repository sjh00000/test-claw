package com.example.keyframevideo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.constants.AdminConstants;
import com.example.keyframevideo.domain.GenerationTask;
import com.example.keyframevideo.domain.GenerationTaskStatusEnum;
import com.example.keyframevideo.domain.OperationTypeEnum;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.mapper.GenerationTaskMapper;
import com.example.keyframevideo.service.GenerationTaskService;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask> implements GenerationTaskService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationTask createSubmittedTask(UserInfo userInfo, OperationTypeEnum operationTypeEnum, String requestBody) {
        GenerationTask generationTask = new GenerationTask();
        generationTask.setUserId(userInfo.getId());
        generationTask.setUsername(userInfo.getUsername());
        generationTask.setTaskType(operationTypeEnum.getCode());
        generationTask.setStatus(GenerationTaskStatusEnum.SUBMITTED.getCode());
        generationTask.setRequestBody(requestBody);
        // 任务先落库再异步调用厂商，前端拿本地任务 ID 轮询，避免长耗时请求阻塞页面。
        save(generationTask);
        return generationTask;
    }

    @Override
    public GenerationTask getRequiredById(Long taskId) {
        GenerationTask generationTask = getById(taskId);
        if (generationTask == null) {
            throw new BusinessException("任务不存在");
        }
        return generationTask;
    }

    @Override
    public GenerationTask getRequiredVisibleTask(Long taskId, UserInfo userInfo) {
        GenerationTask generationTask = getRequiredById(taskId);
        if (!isAdmin(userInfo) && !Objects.equals(generationTask.getUserId(), userInfo.getId())) {
            throw new BusinessException("无权查看该任务");
        }
        return generationTask;
    }

    @Override
    public List<GenerationTask> listVisibleTasks(UserInfo userInfo, String username, String taskType, String status) {
        boolean admin = isAdmin(userInfo);
        return lambdaQuery()
                .eq(!admin, GenerationTask::getUserId, userInfo.getId())
                .like(admin && StrUtil.isNotBlank(username), GenerationTask::getUsername, username)
                .eq(StrUtil.isNotBlank(taskType), GenerationTask::getTaskType, taskType)
                .eq(StrUtil.isNotBlank(status), GenerationTask::getStatus, status)
                .orderByDesc(GenerationTask::getCreatedAt)
                .page(new Page<>(1, 300))
                .getRecords();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRunning(Long taskId, String providerTaskId) {
        GenerationTask generationTask = getRequiredById(taskId);
        generationTask.setProviderTaskId(providerTaskId);
        generationTask.setStatus(GenerationTaskStatusEnum.RUNNING.getCode());
        updateById(generationTask);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFinished(Long taskId, GenerationTaskStatusEnum statusEnum, String resultUrl, String failReason, String responseBody) {
        GenerationTask generationTask = getRequiredById(taskId);
        generationTask.setStatus(statusEnum.getCode());
        generationTask.setResultUrl(resultUrl);
        generationTask.setFailReason(failReason);
        generationTask.setResponseBody(responseBody);
        updateById(generationTask);
    }

    private boolean isAdmin(UserInfo userInfo) {
        return userInfo != null && AdminConstants.INITIAL_ADMIN_USERNAME.equals(userInfo.getUsername());
    }
}
