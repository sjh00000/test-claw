package com.example.keyframevideo.facade;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.bo.LoginBO;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.UserService;
import com.example.keyframevideo.vo.LoginVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private static final String PASSWORD_SALT = "keyframe-video-studio";

    private final UserService userService;

    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginOrCreate(LoginBO loginBO) {
        String username = loginBO.getUsername().trim();
        String passwordHash = hashPassword(loginBO.getPassword());
        UserInfo existingUser = userService.getByUsername(username);
        if (existingUser != null) {
            if (!Objects.equals(passwordHash, existingUser.getPasswordHash())) {
                throw new BusinessException("用户名或密码错误");
            }
            return toVO(existingUser);
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setDisplayName(StrUtil.isNotBlank(loginBO.getDisplayName()) ? loginBO.getDisplayName().trim() : username);
        userInfo.setPasswordHash(passwordHash);
        try {
            // 首次登录自动创建用户，只持久化用户身份信息，不保存任何厂商 api-key。
            userService.save(userInfo);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已被占用，请重新登录", ex);
        }
        return toVO(userService.getByUsername(username));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((PASSWORD_SALT + ":" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception ex) {
            throw new BusinessException("密码处理失败", ex);
        }
    }

    private LoginVO toVO(UserInfo userInfo) {
        LoginVO loginVO = new LoginVO();
        loginVO.setUserId(userInfo.getId());
        loginVO.setUsername(userInfo.getUsername());
        loginVO.setDisplayName(userInfo.getDisplayName());
        return loginVO;
    }
}
