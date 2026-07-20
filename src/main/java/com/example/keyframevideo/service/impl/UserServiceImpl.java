package com.example.keyframevideo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.mapper.UserInfoMapper;
import com.example.keyframevideo.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserService {

    @Override
    public UserInfo getByUsername(String username) {
        return lambdaQuery()
                .eq(UserInfo::getUsername, username)
                .one();
    }
}
