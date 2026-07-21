USE keyframe_video_studio;

DROP TABLE IF EXISTS generation_task;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL,
    admin TINYINT(1) NOT NULL DEFAULT 0,
    image_remaining_count INT NOT NULL DEFAULT 20,
    video_remaining_count INT NOT NULL DEFAULT 5,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    username VARCHAR(64) NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_name VARCHAR(64) NOT NULL,
    request_body TEXT NULL,
    response_body TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_operation_log_user_id (user_id),
    INDEX idx_operation_log_type (operation_type),
    INDEX idx_operation_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE generation_task (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    provider_task_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    result_url LONGTEXT NULL,
    fail_reason VARCHAR(1024) NULL,
    request_body TEXT NULL,
    response_body LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_generation_task_user_id (user_id),
    INDEX idx_generation_task_type_status (task_type, status),
    INDEX idx_generation_task_provider_task_id (provider_task_id),
    INDEX idx_generation_task_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS model_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    service_type VARCHAR(32) NOT NULL UNIQUE,
    base_url VARCHAR(512) NOT NULL,
    api_key VARCHAR(512) NOT NULL,
    model VARCHAR(128) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model_config_service_type (service_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
