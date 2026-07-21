package com.example.keyframevideo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.keyframevideo.domain.OperationLog;
import java.util.List;

public interface OperationLogService extends IService<OperationLog> {

    void record(OperationLog operationLog);

    long countSuccess(Long userId, String operationType);

    List<OperationLog> listForAdmin(Long userId, String username, String operationType, String status);
}
