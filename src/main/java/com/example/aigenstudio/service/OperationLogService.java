package com.example.aigenstudio.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aigenstudio.domain.OperationLog;
import java.util.List;

public interface OperationLogService extends IService<OperationLog> {

    void record(OperationLog operationLog);

    List<OperationLog> listForAdmin(Long userId, String username, String operationType);
}
