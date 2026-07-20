package com.example.keyframevideo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.keyframevideo.domain.UserInfo;

public interface UserService extends IService<UserInfo> {

    UserInfo getByUsername(String username);
}
