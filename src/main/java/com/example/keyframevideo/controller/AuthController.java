package com.example.keyframevideo.controller;

import com.example.keyframevideo.bo.LoginBO;
import com.example.keyframevideo.common.R;
import com.example.keyframevideo.facade.AuthFacade;
import com.example.keyframevideo.vo.LoginVO;
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
