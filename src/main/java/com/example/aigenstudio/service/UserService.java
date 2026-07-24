package com.example.aigenstudio.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.aigenstudio.domain.OperationTypeEnum;
import com.example.aigenstudio.domain.UserInfo;
import java.util.List;

public interface UserService extends IService<UserInfo> {

    UserInfo getByUsername(String username);

    UserInfo getRequiredById(Long userId);

    List<UserInfo> listByKeyword(String keyword);

    boolean isAdmin(Long userId);

    boolean decreaseRemainingCount(Long userId, OperationTypeEnum operationTypeEnum);
}
