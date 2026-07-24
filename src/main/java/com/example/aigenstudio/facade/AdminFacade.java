package com.example.aigenstudio.facade;

import cn.hutool.core.util.StrUtil;
import com.example.aigenstudio.auth.CurrentUserContext;
import com.example.aigenstudio.bo.AdminLogQueryBO;
import com.example.aigenstudio.bo.AdminOperatorBO;
import com.example.aigenstudio.bo.AdminUserQueryBO;
import com.example.aigenstudio.bo.AdminUserUpdateBO;
import com.example.aigenstudio.bo.ModelConfigSaveBO;
import com.example.aigenstudio.constants.AdminConstants;
import com.example.aigenstudio.domain.ModelConfig;
import com.example.aigenstudio.domain.OperationLog;
import com.example.aigenstudio.domain.ServiceTypeEnum;
import com.example.aigenstudio.domain.UserInfo;
import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.service.ModelConfigService;
import com.example.aigenstudio.service.OperationLogService;
import com.example.aigenstudio.service.UserService;
import com.example.aigenstudio.vo.AdminUserVO;
import com.example.aigenstudio.vo.ModelConfigVO;
import com.example.aigenstudio.vo.OperationLogVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminFacade {

    private final UserService userService;
    private final OperationLogService operationLogService;
    private final ModelConfigService modelConfigService;

    public List<AdminUserVO> listUsers(AdminUserQueryBO queryBO) {
        assertAdmin();
        return userService.listByKeyword(queryBO.getKeyword())
                .stream()
                .map(this::toUserVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO updateUser(AdminUserUpdateBO updateBO) {
        assertAdmin();
        UserInfo userInfo = userService.getRequiredById(updateBO.getUserId());
        userInfo.setAdmin(AdminConstants.INITIAL_ADMIN_USERNAME.equals(userInfo.getUsername()));
        userInfo.setImageRemainingCount(resolveCount(updateBO.getImageRemainingCount()));
        userInfo.setVideoRemainingCount(resolveCount(updateBO.getVideoRemainingCount()));
        // 用户权限和额度影响后续所有生成入口，统一在后台保存为单表配置。
        userService.updateById(userInfo);
        return toUserVO(userInfo);
    }

    public List<OperationLogVO> listLogs(AdminLogQueryBO queryBO) {
        assertAdmin();
        return operationLogService.listForAdmin(
                        queryBO.getUserId(),
                        queryBO.getUsername(),
                        queryBO.getOperationType())
                .stream()
                .map(this::toLogVO)
                .toList();
    }

    public List<ModelConfigVO> listModelConfigs(AdminOperatorBO operatorBO) {
        assertAdmin();
        return modelConfigService.listByServiceTypeOrder()
                .stream()
                .map(this::toModelConfigVO)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVO saveModelConfig(ModelConfigSaveBO saveBO) {
        assertAdmin();
        ServiceTypeEnum serviceTypeEnum = ServiceTypeEnum.requireByCode(saveBO.getServiceType());
        ModelConfig modelConfig = modelConfigService.getByServiceType(serviceTypeEnum);
        if (modelConfig == null) {
            if (StrUtil.isBlank(saveBO.getApiKey())) {
                throw new BusinessException("首次配置必须填写密钥");
            }
            modelConfig = new ModelConfig();
            modelConfig.setServiceType(serviceTypeEnum.getCode());
        }
        modelConfig.setBaseUrl(saveBO.getBaseUrl().trim());
        if (StrUtil.isNotBlank(saveBO.getApiKey())) {
            modelConfig.setApiKey(saveBO.getApiKey().trim());
        }
        modelConfig.setModel(saveBO.getModel().trim());
        modelConfig.setEnabled(!Boolean.FALSE.equals(saveBO.getEnabled()));
        // 模型配置是全局生效项，保存后普通用户生成请求立即使用最新配置。
        modelConfigService.saveOrUpdate(modelConfig);
        return toModelConfigVO(modelConfig);
    }

    private UserInfo assertAdmin() {
        UserInfo operator = CurrentUserContext.getRequiredUser();
        if (!AdminConstants.INITIAL_ADMIN_USERNAME.equals(operator.getUsername())) {
            throw new BusinessException("仅管理员可访问");
        }
        return operator;
    }

    private int resolveCount(Integer count) {
        return count == null ? 0 : Math.max(0, count);
    }

    private AdminUserVO toUserVO(UserInfo userInfo) {
        AdminUserVO adminUserVO = new AdminUserVO();
        adminUserVO.setUserId(userInfo.getId());
        adminUserVO.setUsername(userInfo.getUsername());
        adminUserVO.setAdmin(AdminConstants.INITIAL_ADMIN_USERNAME.equals(userInfo.getUsername()));
        adminUserVO.setImageRemainingCount(userInfo.getImageRemainingCount());
        adminUserVO.setVideoRemainingCount(userInfo.getVideoRemainingCount());
        adminUserVO.setCreatedAt(userInfo.getCreatedAt());
        return adminUserVO;
    }

    private OperationLogVO toLogVO(OperationLog operationLog) {
        OperationLogVO operationLogVO = new OperationLogVO();
        operationLogVO.setId(operationLog.getId());
        operationLogVO.setUserId(operationLog.getUserId());
        operationLogVO.setUsername(operationLog.getUsername());
        operationLogVO.setOperationType(operationLog.getOperationType());
        operationLogVO.setOperationName(operationLog.getOperationName());
        operationLogVO.setRequestBody(operationLog.getRequestBody());
        operationLogVO.setResponseBody(operationLog.getResponseBody());
        operationLogVO.setCreatedAt(operationLog.getCreatedAt());
        return operationLogVO;
    }

    private ModelConfigVO toModelConfigVO(ModelConfig modelConfig) {
        ServiceTypeEnum serviceTypeEnum = ServiceTypeEnum.requireByCode(modelConfig.getServiceType());
        ModelConfigVO modelConfigVO = new ModelConfigVO();
        modelConfigVO.setId(modelConfig.getId());
        modelConfigVO.setServiceType(modelConfig.getServiceType());
        modelConfigVO.setServiceName(serviceTypeEnum.getDesc());
        modelConfigVO.setBaseUrl(modelConfig.getBaseUrl());
        modelConfigVO.setApiKey(modelConfig.getApiKey());
        modelConfigVO.setApiKeyMask(maskApiKey(modelConfig.getApiKey()));
        modelConfigVO.setModel(modelConfig.getModel());
        modelConfigVO.setEnabled(Boolean.TRUE.equals(modelConfig.getEnabled()));
        modelConfigVO.setUpdatedAt(modelConfig.getUpdatedAt());
        return modelConfigVO;
    }

    private String maskApiKey(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return "";
        }
        String value = apiKey.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}
