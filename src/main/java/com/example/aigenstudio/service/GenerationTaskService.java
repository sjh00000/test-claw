package com.example.aigenstudio.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aigenstudio.domain.GenerationTask;
import com.example.aigenstudio.domain.GenerationTaskStatusEnum;
import com.example.aigenstudio.domain.OperationTypeEnum;
import com.example.aigenstudio.domain.UserInfo;
import java.util.List;

public interface GenerationTaskService extends IService<GenerationTask> {

    GenerationTask createSubmittedTask(UserInfo userInfo, OperationTypeEnum operationTypeEnum, String requestBody);

    GenerationTask createSubmittedTaskIfNoActiveTask(UserInfo userInfo, OperationTypeEnum operationTypeEnum, String requestBody);

    GenerationTask getRequiredById(Long taskId);

    GenerationTask getActiveTask(Long userId);

    GenerationTask getRequiredVisibleTask(Long taskId, UserInfo userInfo);

    List<GenerationTask> listVisibleTaskSummaries(UserInfo userInfo, String username, String taskType, String status);

    void markRunning(Long taskId, String providerTaskId);

    void markFinished(Long taskId, GenerationTaskStatusEnum statusEnum, String resultUrl, String failReason, String responseBody);
}
