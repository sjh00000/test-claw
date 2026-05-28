package com.example.keyframevideo.service;

import com.example.keyframevideo.domain.GenerationSession;

public interface GenerationSessionService {

    void saveOrUpdate(GenerationSession session);

    GenerationSession getById(String sessionId);
}
