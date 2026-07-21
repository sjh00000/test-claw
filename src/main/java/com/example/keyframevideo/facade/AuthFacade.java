package com.example.keyframevideo.facade;

import com.example.keyframevideo.bo.LoginBO;
import com.example.keyframevideo.auth.JwtService;
import com.example.keyframevideo.constants.AdminConstants;
import com.example.keyframevideo.domain.UserInfo;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.UserService;
import com.example.keyframevideo.vo.LoginVO;
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
            userInfo.setImageCallLimit(AdminConstants.DEFAULT_IMAGE_CALL_LIMIT);
            userInfo.setVideoCallLimit(AdminConstants.DEFAULT_VIDEO_CALL_LIMIT);
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
