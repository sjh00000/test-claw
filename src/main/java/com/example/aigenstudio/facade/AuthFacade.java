package com.example.aigenstudio.facade;

import com.example.aigenstudio.bo.LoginBO;
import com.example.aigenstudio.auth.JwtService;
import com.example.aigenstudio.constants.AdminConstants;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.service.UserService;
import com.example.aigenstudio.vo.LoginVO;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final UserService userService;
    private final JwtService jwtService;

    @Transactional(rollbackFor = Exception.class)
    public LoginVO loginOrCreate(LoginBO loginBO) {
        String username = loginBO.getUsername().trim();
        String password = loginBO.getPassword();
        try {
            UserInfo existingUser = userService.getByUsername(username);
            if (existingUser != null) {
                if (!Objects.equals(password, existingUser.getPassword())) {
                    throw new BusinessException("用户名或密码错误");
                }
                return toVO(existingUser);
            }

            UserInfo userInfo = new UserInfo();
            userInfo.setUsername(username);
            userInfo.setDisplayName(username);
            userInfo.setPassword(password);
            userInfo.setAdmin(AdminConstants.INITIAL_ADMIN_USERNAME.equals(username));
            userInfo.setImageRemainingCount(AdminConstants.DEFAULT_IMAGE_REMAINING_COUNT);
            userInfo.setVideoRemainingCount(AdminConstants.DEFAULT_VIDEO_REMAINING_COUNT);
            // 首次登录自动创建用户，按当前产品要求明文保存密码，不保存任何厂商 api-key。
            userService.save(userInfo);
            return toVO(userInfo);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已被占用，请重新登录", ex);
        }
    }

    private LoginVO toVO(UserInfo userInfo) {
        LoginVO loginVO = new LoginVO();
        loginVO.setUserId(userInfo.getId());
        loginVO.setUsername(userInfo.getUsername());
        loginVO.setAdmin(AdminConstants.INITIAL_ADMIN_USERNAME.equals(userInfo.getUsername()));
        loginVO.setAccessToken(jwtService.createAccessToken(userInfo.getId(), userInfo.getUsername()));
        return loginVO;
    }
}
