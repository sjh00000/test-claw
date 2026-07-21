package com.example.keyframevideo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.constants.AdminConstants;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.mapper.UserInfoMapper;
import com.example.keyframevideo.service.UserService;
import java.util.List;
import org.springframework.stereotype.Service;

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
}
