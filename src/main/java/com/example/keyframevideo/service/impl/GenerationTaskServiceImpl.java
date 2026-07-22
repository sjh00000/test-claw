package com.example.keyframevideo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.config.GenerationProperties;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GenerationTaskServiceImpl extends ServiceImpl<GenerationTaskMapper, GenerationTask> implements GenerationTaskService {

    // 前端会直接用任务 ID 轮询状态，因此这里把雪花 ID 压到 JS Number 安全整数范围内，避免精度丢失后查不到任务。
    private static final long TASK_ID_EPOCH_MS = 1767225600000L;
    private static final long WORKER_ID_BITS = 4L;
    private static final long SEQUENCE_BITS = 10L;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;
    private static final long MAX_JS_SAFE_INTEGER = 9_007_199_254_740_991L;

    private long lastTimestamp = -1L;
    private long sequence = 0L;
    private final GenerationProperties generationProperties;

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
        try {
            save(generationTask);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("当前已有生成任务正在执行，请等待任务完成后再创建新任务", ex);
        }
        return generationTask;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GenerationTask createSubmittedTaskIfNoActiveTask(
            UserInfo userInfo,
            OperationTypeEnum operationTypeEnum,
            String requestBody) {
        GenerationTask activeTask = getActiveTask(userInfo.getId());
        if (activeTask != null) {
            // 同一用户任意生成任务未终结前不允许创建新任务，避免重复点击或脚本并发占用厂商额度。
            throw new BusinessException("当前已有生成任务正在执行，请等待任务完成后再创建新任务");
        }
        return createSubmittedTask(userInfo, operationTypeEnum, requestBody);
    }

    private synchronized long nextTaskId() {
        long currentTimestamp = currentTimestamp();
        if (currentTimestamp < lastTimestamp) {
            throw new BusinessException("系统时钟回拨，任务创建失败");
        }
        if (currentTimestamp == lastTimestamp) {
            // 同一毫秒内用序列号区分任务，序列耗尽时等待下一毫秒保证 ID 不重复。
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = currentTimestamp;
        long workerId = resolveWorkerId();
        long taskId = ((currentTimestamp - TASK_ID_EPOCH_MS) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
        if (taskId > MAX_JS_SAFE_INTEGER) {
            throw new BusinessException("任务 ID 超出前端安全整数范围");
        }
        return taskId;
    }

    private long resolveWorkerId() {
        long workerId = generationProperties.getTask().getWorkerId();
        long maxWorkerId = (1L << WORKER_ID_BITS) - 1;
        if (workerId < 0 || workerId > maxWorkerId) {
            throw new BusinessException("任务 ID workerId 配置错误，范围应为 0-15");
        }
        return workerId;
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
    public GenerationTask getActiveTask(Long userId) {
        if (userId == null) {
            return null;
        }
        List<GenerationTask> activeTaskList = lambdaQuery()
                .eq(GenerationTask::getUserId, userId)
                .in(GenerationTask::getStatus, GenerationTaskStatusEnum.activeCodes())
                .orderByDesc(GenerationTask::getCreatedAt)
                .page(new Page<>(1, 1))
                .getRecords();
        return CollectionUtil.isEmpty(activeTaskList) ? null : activeTaskList.get(0);
    }

    @Override
    public GenerationTask getRequiredVisibleTask(Long taskId, UserInfo userInfo) {
        GenerationTask generationTask = getRequiredById(taskId);
        // 非管理员只能查看自己的任务，任务中心和单条状态查询都复用该权限边界。
        if (!isAdmin(userInfo) && !Objects.equals(generationTask.getUserId(), userInfo.getId())) {
            throw new BusinessException("无权查看该任务");
        }
        return generationTask;
    }

    @Override
    public List<GenerationTask> listVisibleTaskSummaries(UserInfo userInfo, String username, String taskType, String status) {
        // 任务列表只展示元信息，不查询 result_url/response_body/request_body，避免图片 base64 大字段拖慢接口和页面渲染。
        return buildVisibleTaskQuery(userInfo, username, taskType, status)
                .select(
                        GenerationTask::getId,
                        GenerationTask::getUserId,
                        GenerationTask::getUsername,
                        GenerationTask::getTaskType,
                        GenerationTask::getProviderTaskId,
                        GenerationTask::getStatus,
                        GenerationTask::getFailReason,
                        GenerationTask::getCreatedAt,
                        GenerationTask::getUpdatedAt)
                .page(new Page<>(1, 300))
                .getRecords();
    }

    private LambdaQueryChainWrapper<GenerationTask> buildVisibleTaskQuery(
            UserInfo userInfo,
            String username,
            String taskType,
            String status) {
        boolean admin = isAdmin(userInfo);
        return lambdaQuery()
                .eq(!admin, GenerationTask::getUserId, userInfo.getId())
                .like(admin && StrUtil.isNotBlank(username), GenerationTask::getUsername, username)
                .eq(StrUtil.isNotBlank(taskType), GenerationTask::getTaskType, taskType)
                .eq(StrUtil.isNotBlank(status), GenerationTask::getStatus, status)
                .orderByDesc(GenerationTask::getCreatedAt);
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
        // 任务终态统一在这里写入，保证图片、视频成功/失败都使用同一套任务中心字段。
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
