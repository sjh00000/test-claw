package com.example.keyframevideo.service;

public interface OssStorageService {

    /**
     * 上传生成结果图片并返回可持久化的访问地址。
     */
    String uploadGeneratedImage(byte[] imageBytes);
}
