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

    // 前端会直接用任务 ID 轮询状态，因此这里把雪花 ID 压到 JS Number 安全整数范围内，避免精度丢失后查不到任务。
    private static final long TASK_ID_EPOCH_MS = 1767225600000L;
    private static final long WORKER_ID = 1L;
    private static final long WORKER_ID_BITS = 4L;
    private static final long SEQUENCE_BITS = 10L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final long MAX_JS_SAFE_INTEGER = 9_007_199_254_740_991L;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationTask createSubmittedTask(UserInfo userInfo, OperationTypeEnum operationTypeEnum, String requestBody) {
        GenerationTask generationTask = new GenerationTask();
        generationTask.setId(nextTaskId());
        generationTask.setUserId(userInfo.getId());
        generationTask.setUsername(userInfo.getUsername());
        generationTask.setTaskType(operationTypeEnum.getCode());
        generationTask.setStatus(GenerationTaskStatusEnum.SUBMITTED.getCode());
        generationTask.setRequestBody(requestBody);
        // 任务先落库再异步调用厂商；任务 ID 使用 53 位以内的紧凑雪花算法，避免浏览器 Number 精度丢失。
        save(generationTask);
        return generationTask;
    }

    private synchronized long nextTaskId() {
        long currentTimestamp = currentTimestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new BusinessException("系统时钟回拨，任务创建失败");
        }
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = currentTimestamp;
        long taskId = ((currentTimestamp - TASK_ID_EPOCH_MS) << TIMESTAMP_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
        if (taskId > MAX_JS_SAFE_INTEGER) {
            throw new BusinessException("任务 ID 超出前端安全整数范围");
        }
        return taskId;
    }

    private long waitNextMillis(long previousTimestamp) {
        long currentTimestamp = currentTimestamp();
        while (currentTimestamp <= previousTimestamp) {
            currentTimestamp = currentTimestamp();
        }
        return currentTimestamp;
    }

    private long currentTimestamp() {
        return System.currentTimeMillis();
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
