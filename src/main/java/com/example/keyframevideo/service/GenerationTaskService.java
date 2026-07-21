package com.example.keyframevideo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.keyframevideo.domain.GenerationTask;
import com.example.keyframevideo.domain.GenerationTaskStatusEnum;
import com.example.keyframevideo.domain.OperationTypeEnum;
import com.example.keyframevideo.domain.UserInfo;
import java.util.List;

public interface GenerationTaskService extends IService<GenerationTask> {

    GenerationTask createSubmittedTask(UserInfo userInfo, OperationTypeEnum operationTypeEnum, String requestBody);

    GenerationTask getRequiredById(Long taskId);

    GenerationTask getRequiredVisibleTask(Long taskId, UserInfo userInfo);

    List<GenerationTask> listVisibleTasks(UserInfo userInfo, String username, String taskType, String status);

    void markRunning(Long taskId, String providerTaskId);

    void markFinished(Long taskId, GenerationTaskStatusEnum statusEnum, String resultUrl, String failReason, String responseBody);
}
