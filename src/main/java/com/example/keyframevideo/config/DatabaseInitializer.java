package com.example.keyframevideo.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(Path.of("data"));
        // 本地 SQLite 不依赖外部服务，启动时创建会话表，session_json 保存完整领域快照。
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS generation_session (
                    id TEXT PRIMARY KEY,
                    status TEXT NOT NULL,
                    session_json TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
        log.info("SQLite 会话表初始化完成");
    }
}
