package com.example.aigenstudio.auth;

import cn.hutool.core.util.StrUtil;
import com.example.aigenstudio.common.R;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StrUtil.isBlank(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            writeAuthFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录");
            return;
        }

        JwtService.JwtClaims claims = jwtService.parseAccessToken(authorization.substring(BEARER_PREFIX.length()));
        if (claims == null) {
            writeAuthFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "登录已过期，请重新登录");
            return;
        }

        // token 只保存用户 ID，权限和剩余额度等动态信息每次从数据库读取，避免旧 token 携带过期用户状态。
        UserInfo userInfo = userService.getById(claims.userId());
        if (userInfo == null) {
            writeAuthFailure(response, HttpServletResponse.SC_UNAUTHORIZED, "登录用户不存在");
            return;
        }
        try {
            // 后续 facade 统一从 ThreadLocal 取当前登录用户，避免前端在请求体里传操作者 ID。
            CurrentUserContext.set(userInfo);
            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !uri.startsWith("/api/")
                || "/api/auth/login".equals(uri);
    }

    private void writeAuthFailure(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(status, message)));
    }
}
