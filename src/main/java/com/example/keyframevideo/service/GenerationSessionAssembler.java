package com.example.keyframevideo.service;

import com.example.keyframevideo.domain.GenerationSession;
import com.example.keyframevideo.domain.KeyframeResult;
import com.example.keyframevideo.vo.GenerationSessionVO;
import com.example.keyframevideo.vo.KeyframeVO;
import com.example.keyframevideo.vo.ReferenceImageItemVO;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GenerationSessionAssembler {

    public GenerationSessionVO toVO(GenerationSession session) {
        GenerationSessionVO vo = new GenerationSessionVO();
        vo.setId(session.getId());
        vo.setVideoPrompt(session.getVideoPrompt());
        vo.setReferenceImageUrls(List.copyOf(session.getReferenceImageUrls()));
        vo.setReferenceImages(session.getReferenceImages().stream().map(this::toReferenceImageVO).toList());
        vo.setDuration(session.getDuration());
        vo.setResolution(session.getResolution());
        vo.setRatio(session.getRatio());
        vo.setGenerateAudio(session.isGenerateAudio());
        vo.setFastMode(session.isFastMode());
        vo.setStatus(session.getStatus());
        vo.setSeedanceTaskId(session.getSeedanceTaskId());
        vo.setVideoUrl(session.getVideoUrl());
        vo.setErrorMessage(session.getErrorMessage());
        vo.setCreatedAt(session.getCreatedAt());
        vo.setUpdatedAt(session.getUpdatedAt());
        vo.setKeyframes(session.getKeyframes().stream().map(this::toVO).toList());
        return vo;
    }

    private KeyframeVO toVO(KeyframeResult keyframe) {
        KeyframeVO vo = new KeyframeVO();
        vo.setIndex(keyframe.getIndex());
        vo.setPrompt(keyframe.getPrompt());
        vo.setReferenceImageUrls(List.copyOf(keyframe.getReferenceImageUrls()));
        vo.setGeneratedImageUrl(keyframe.getGeneratedImageUrl());
        vo.setStatus(keyframe.getStatus());
        vo.setErrorMessage(keyframe.getErrorMessage());
        vo.setUpdatedAt(keyframe.getUpdatedAt());
        return vo;
    }

    private ReferenceImageItemVO toReferenceImageVO(com.example.keyframevideo.domain.ReferenceImage referenceImage) {
        ReferenceImageItemVO vo = new ReferenceImageItemVO();
        vo.setName(referenceImage.getName());
        vo.setImageUrl(referenceImage.getImageUrl());
        return vo;
    }
}
