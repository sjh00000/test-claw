package com.example.keyframevideo.service.impl;

import com.example.keyframevideo.domain.GenerationSession;
import com.example.keyframevideo.exception.BusinessException;
import com.example.keyframevideo.service.GenerationSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationSessionServiceImpl implements GenerationSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(GenerationSession session) {
        try {
            String sessionJson = objectMapper.writeValueAsString(session);
            // SQLite UPSERT 保存完整会话快照，状态变化、关键帧结果、视频任务号都统一落库。
            jdbcTemplate.update("""
                            INSERT INTO generation_session (id, status, session_json, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT(id) DO UPDATE SET
                                status = excluded.status,
                                session_json = excluded.session_json,
                                updated_at = excluded.updated_at
                            """,
                    session.getId(),
                    session.getStatus().name(),
                    sessionJson,
                    session.getCreatedAt().toString(),
                    session.getUpdatedAt().toString());
            log.info("会话快照保存成功，sessionId={}, status={}", session.getId(), session.getStatus());
        } catch (Exception ex) {
            log.warn("会话快照保存失败，sessionId={}, reason={}", session.getId(), ex.getMessage());
            throw new BusinessException("会话保存失败", ex);
        }
    }

    @Override
    public GenerationSession getById(String sessionId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT session_json FROM generation_session WHERE id = ?",
                    (ResultSet rs, int rowNum) -> readSession(rs.getString("session_json"), sessionId),
                    sessionId);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException(404, "会话不存在");
        }
    }

    private GenerationSession readSession(String sessionJson, String sessionId) {
        try {
            return objectMapper.readValue(sessionJson, GenerationSession.class);
        } catch (Exception ex) {
            log.warn("会话快照解析失败，sessionId={}, reason={}", sessionId, ex.getMessage());
            throw new BusinessException("会话解析失败", ex);
        }
    }
}
