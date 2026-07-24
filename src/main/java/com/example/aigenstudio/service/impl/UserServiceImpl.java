package com.example.aigenstudio.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.aigenstudio.constants.AdminConstants;
import com.example.aigenstudio.domain.OperationTypeEnum;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.mapper.UserInfoMapper;
import com.example.aigenstudio.service.UserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserService {

    @Override
    public UserInfo getByUsername(String username) {
        return lambdaQuery()
                .eq(UserInfo::getUsername, username)
                .one();
    }

    @Override
    public UserInfo getRequiredById(Long userId) {
        UserInfo userInfo = getById(userId);
        if (userInfo == null) {
            throw new BusinessException("用户不存在");
        }
        return userInfo;
    }

    @Override
    public List<UserInfo> listByKeyword(String keyword) {
        return lambdaQuery()
                .like(StrUtil.isNotBlank(keyword), UserInfo::getUsername, keyword)
                .orderByDesc(UserInfo::getCreatedAt)
                .page(new Page<>(1, 200))
                .getRecords();
    }

    @Override
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        UserInfo userInfo = getById(userId);
        return userInfo != null && AdminConstants.INITIAL_ADMIN_USERNAME.equals(userInfo.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized boolean decreaseRemainingCount(Long userId, OperationTypeEnum operationTypeEnum) {
        UserInfo userInfo = getRequiredById(userId);
        if (OperationTypeEnum.TEXT_TO_IMAGE.equals(operationTypeEnum)) {
            int remainingCount = userInfo.getImageRemainingCount() == null ? 0 : userInfo.getImageRemainingCount();
            if (remainingCount <= 0) {
                return false;
            }
            userInfo.setImageRemainingCount(remainingCount - 1);
            return updateById(userInfo);
        }
        if (OperationTypeEnum.TEXT_TO_VIDEO.equals(operationTypeEnum)) {
            int remainingCount = userInfo.getVideoRemainingCount() == null ? 0 : userInfo.getVideoRemainingCount();
            if (remainingCount <= 0) {
                return false;
            }
            userInfo.setVideoRemainingCount(remainingCount - 1);
            return updateById(userInfo);
        }
        throw new BusinessException("不支持的生成类型");
    }
}
