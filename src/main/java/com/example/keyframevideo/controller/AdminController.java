package com.example.keyframevideo.controller;

import com.example.keyframevideo.bo.AdminLogQueryBO;
import com.example.keyframevideo.bo.AdminOperatorBO;
import com.example.keyframevideo.bo.AdminUserQueryBO;
import com.example.keyframevideo.bo.AdminUserUpdateBO;
import com.example.keyframevideo.bo.ModelConfigSaveBO;
import com.example.keyframevideo.common.R;
import com.example.keyframevideo.facade.AdminFacade;
import com.example.keyframevideo.vo.AdminUserVO;
import com.example.keyframevideo.vo.ModelConfigVO;
import com.example.keyframevideo.vo.OperationLogVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminFacade adminFacade;

    @PostMapping("/users")
    public R<List<AdminUserVO>> listUsers(@Valid @RequestBody AdminUserQueryBO request) {
        return R.ok(adminFacade.listUsers(request));
    }

    @PostMapping("/users/update")
    public R<AdminUserVO> updateUser(@Valid @RequestBody AdminUserUpdateBO request) {
        return R.ok(adminFacade.updateUser(request));
    }

    @PostMapping("/logs")
    public R<List<OperationLogVO>> listLogs(@Valid @RequestBody AdminLogQueryBO request) {
        return R.ok(adminFacade.listLogs(request));
    }

    @PostMapping("/model-configs")
    public R<List<ModelConfigVO>> listModelConfigs(@Valid @RequestBody AdminOperatorBO request) {
        return R.ok(adminFacade.listModelConfigs(request));
    }

    @PostMapping("/model-configs/save")
    public R<ModelConfigVO> saveModelConfig(@Valid @RequestBody ModelConfigSaveBO request) {
        return R.ok(adminFacade.saveModelConfig(request));
    }
}
