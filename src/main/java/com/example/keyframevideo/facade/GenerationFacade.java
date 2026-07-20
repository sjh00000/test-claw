package com.example.keyframevideo.facade;

import cn.hutool.core.util.StrUtil;
import com.example.keyframevideo.bo.ReferenceImageBO;
import com.example.keyframevideo.bo.TextToImageBO;
import com.example.keyframevideo.bo.TextToVideoBO;
import com.example.keyframevideo.bo.VideoStatusBO;
import com.example.keyframevideo.client.ImageProviderClient;
import com.example.keyframevideo.client.SeedanceClient;
import com.example.keyframevideo.domain.ReferenceImage;
import com.example.keyframevideo.domain.SeedanceTaskStatus;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.vo.ImageGenerationVO;
import com.example.keyframevideo.vo.VideoGenerationVO;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationFacade {

    private final ImageProviderClient imageProviderClient;
    private final SeedanceClient seedanceClient;

    public ImageGenerationVO generateImage(TextToImageBO textToImageBO) {
        validateImageOptions(textToImageBO.getImageSize(), textToImageBO.getImageQuality());
        List<ReferenceImage> referenceImages = buildReferenceImages(textToImageBO.getReferenceImages());
        // 文生图与文生视频完全解耦：本接口只返回图片结果，不创建会话也不触发视频流程。
        String imageUrl = imageProviderClient.generate(
                textToImageBO.getPrompt().trim(),
                referenceImages,
                textToImageBO.getImageSize(),
                textToImageBO.getImageQuality(),
                textToImageBO.getImageProviderConfig());
        ImageGenerationVO imageGenerationVO = new ImageGenerationVO();
        imageGenerationVO.setImageUrl(imageUrl);
        log.info("文生图完成，userId={}, referenceImageCount={}", textToImageBO.getUserId(), referenceImages.size());
        return imageGenerationVO;
    }

    public VideoGenerationVO generateVideo(TextToVideoBO textToVideoBO) {
        validateVideoOptions(textToVideoBO);
        List<ReferenceImage> referenceImages = buildReferenceImages(textToVideoBO.getReferenceImages());
        // 文生视频直接提交 Seedance；参考图为空时按纯文本生成，有参考图时作为多模态参考输入。
        String taskId = seedanceClient.submit(
                textToVideoBO.getPrompt().trim(),
                referenceImages,
                textToVideoBO.getDuration(),
                textToVideoBO.getResolution(),
                textToVideoBO.getRatio(),
                textToVideoBO.isGenerateAudio(),
                textToVideoBO.getSeedanceConfig());
        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(taskId);
        videoGenerationVO.setStatus("SUBMITTED");
        log.info("文生视频任务已提交，userId={}, taskId={}, referenceImageCount={}",
                textToVideoBO.getUserId(), taskId, referenceImages.size());
        return videoGenerationVO;
    }

    public VideoGenerationVO queryVideoStatus(VideoStatusBO videoStatusBO) {
        SeedanceTaskStatus taskStatus = seedanceClient.query(videoStatusBO.getTaskId(), videoStatusBO.getSeedanceConfig());
        VideoGenerationVO videoGenerationVO = new VideoGenerationVO();
        videoGenerationVO.setTaskId(taskStatus.getTaskId());
        videoGenerationVO.setStatus(taskStatus.getStatus());
        videoGenerationVO.setVideoUrl(taskStatus.getVideoUrl());
        videoGenerationVO.setFailReason(taskStatus.getFailReason());
        return videoGenerationVO;
    }

    private void validateImageOptions(String imageSize, String imageQuality) {
        if (!List.of("1024x1024", "1024x1536", "1536x1024").contains(imageSize)) {
            throw new BusinessException("图片尺寸仅支持 1024x1024、1024x1536、1536x1024");
        }
        if (!List.of("low", "medium", "high").contains(imageQuality)) {
            throw new BusinessException("图片质量仅支持 low、medium、high");
        }
    }

    private void validateVideoOptions(TextToVideoBO textToVideoBO) {
        if (!List.of("480p", "720p").contains(textToVideoBO.getResolution())) {
            throw new BusinessException("视频清晰度仅支持 480p 或 720p");
        }
        if (!List.of("16:9", "4:3", "1:1", "3:4", "9:16", "21:9", "adaptive").contains(textToVideoBO.getRatio())) {
            throw new BusinessException("视频比例仅支持 16:9、4:3、1:1、3:4、9:16、21:9 或 adaptive");
        }
        if (textToVideoBO.getDuration() < 4 || textToVideoBO.getDuration() > 15) {
            throw new BusinessException("视频时长需为 4 到 15 秒之间的整数");
        }
    }

    private List<ReferenceImage> buildReferenceImages(List<ReferenceImageBO> referenceImageBOList) {
        List<ReferenceImage> referenceImages = new ArrayList<>();
        for (int index = 0; index < referenceImageBOList.size(); index++) {
            ReferenceImageBO referenceImageBO = referenceImageBOList.get(index);
            if (StrUtil.isBlank(referenceImageBO.getImageUrl())) {
                throw new BusinessException("参考图地址不能为空");
            }
            ReferenceImage referenceImage = new ReferenceImage();
            referenceImage.setImageUrl(referenceImageBO.getImageUrl());
            referenceImage.setName(StrUtil.isNotBlank(referenceImageBO.getName())
                    ? referenceImageBO.getName().trim()
                    : "参考图" + (index + 1));
            referenceImages.add(referenceImage);
        }
        return referenceImages;
    }
}
