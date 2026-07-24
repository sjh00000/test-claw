package com.example.aigenstudio.auth;

import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.exception.BusinessException;

public final class CurrentUserContext {

    private static final ThreadLocal<UserInfo> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(UserInfo userInfo) {
        CURRENT_USER.set(userInfo);
    }

    public static UserInfo getRequiredUser() {
        UserInfo userInfo = CURRENT_USER.get();
        if (userInfo == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userInfo;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
