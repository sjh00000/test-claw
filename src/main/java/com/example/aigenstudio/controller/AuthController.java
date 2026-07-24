package com.example.aigenstudio.controller;

import com.example.aigenstudio.bo.LoginBO;
import com.example.aigenstudio.common.R;
import com.example.aigenstudio.facade.AuthFacade;
import com.example.aigenstudio.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacade authFacade;

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginBO request) {
        return R.ok(authFacade.loginOrCreate(request));
    }
}
