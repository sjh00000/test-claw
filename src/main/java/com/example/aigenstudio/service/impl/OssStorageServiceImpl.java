package com.example.aigenstudio.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.aigenstudio.config.GenerationProperties;
import com.example.aigenstudio.exception.BusinessException;
import com.example.aigenstudio.service.OssStorageService;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OssStorageServiceImpl implements OssStorageService {

    private static final DateTimeFormatter OBJECT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final GenerationProperties properties;

    @Override
    public String uploadGeneratedImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new BusinessException("图片内容为空，无法上传 OSS");
        }
        GenerationProperties.Oss oss = properties.getOss();
        validateOssConfig(oss);
        // objectKey 按业务类型和日期分层，便于后续在 OSS 控制台排查单日生成结果。
        String objectKey = buildObjectKey(oss.getObjectPrefix(), "images", "png");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("image/png");
        metadata.setContentLength(imageBytes.length);

        OSS client = new OSSClientBuilder().build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            // 厂商返回 base64 时立即转存 OSS，任务表只保存可访问 URL，避免列表接口传输超大 base64。
            client.putObject(new PutObjectRequest(oss.getBucketName(), objectKey, inputStream, metadata));
            String objectUrl = buildObjectUrl(oss.getBucketDomain(), objectKey);
            log.info("生成图片上传 OSS 成功，bucket={}, objectKey={}, byteSize={}", oss.getBucketName(), objectKey, imageBytes.length);
            return objectUrl;
        } catch (Exception ex) {
            log.warn("生成图片上传 OSS 失败，bucket={}, objectKey={}, byteSize={}, reason={}",
                    oss.getBucketName(), objectKey, imageBytes.length, ex.getMessage());
            throw new BusinessException("生成图片上传 OSS 失败：" + ex.getMessage(), ex);
        } finally {
            client.shutdown();
        }
    }

    private void validateOssConfig(GenerationProperties.Oss oss) {
        // OSS 密钥只允许从环境或部署配置读取，缺项时立即阻断，避免生成结果回退到数据库 base64。
        if (oss == null
                || StrUtil.isBlank(oss.getEndpoint())
                || StrUtil.isBlank(oss.getAccessKeyId())
                || StrUtil.isBlank(oss.getAccessKeySecret())
                || StrUtil.isBlank(oss.getBucketName())
                || StrUtil.isBlank(oss.getBucketDomain())) {
            throw new BusinessException("管理员尚未配置 OSS 存储参数");
        }
    }

    private String buildObjectKey(String objectPrefix, String bizType, String extension) {
        String prefix = StrUtil.isNotBlank(objectPrefix) ? trimSlash(objectPrefix.trim()) : "aigen-studio";
        String datePath = LocalDate.now().format(OBJECT_DATE_FORMATTER);
        return prefix + "/" + bizType + "/" + datePath + "/" + UUID.randomUUID() + "." + extension;
    }

    private String buildObjectUrl(String bucketDomain, String objectKey) {
        // 数据库存完整访问 URL，前端预览和下载不需要理解 bucket、endpoint 与 objectKey 的拼接规则。
        String domain = bucketDomain.trim();
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        return trimSlash(domain) + "/" + objectKey;
    }

    private String trimSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
