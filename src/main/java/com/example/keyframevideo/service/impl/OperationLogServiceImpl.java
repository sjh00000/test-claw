package com.example.keyframevideo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.domain.OperationLog;
import com.example.keyframevideo.mapper.OperationLogMapper;
import com.example.keyframevideo.service.OperationLogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLog operationLog) {
        save(operationLog);
    }

    @Override
    public List<OperationLog> listForAdmin(Long userId, String username, String operationType) {
        return lambdaQuery()
                .eq(userId != null, OperationLog::getUserId, userId)
                .like(StrUtil.isNotBlank(username), OperationLog::getUsername, username)
                .eq(StrUtil.isNotBlank(operationType), OperationLog::getOperationType, operationType)
                .orderByDesc(OperationLog::getCreatedAt)
                .page(new Page<>(1, 300))
                .getRecords();
    }
}
