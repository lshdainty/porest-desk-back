-- =============================================
-- POREST Desk - Phase 2C: Todo Enhancements
-- =============================================

-- 1. Todo Project (프로젝트/목록)
CREATE TABLE todo_project (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    project_name        VARCHAR(100)    NOT NULL,
    description         TEXT            NULL,
    color               VARCHAR(20)     NULL,
    icon                VARCHAR(50)     NULL,
    sort_order          INT             NOT NULL DEFAULT 0,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_todo_project_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Todo Tag (태그)
CREATE TABLE todo_tag (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    tag_name            VARCHAR(50)     NOT NULL,
    color               VARCHAR(20)     NULL,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_todo_tag_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Todo: 프로젝트 및 서브태스크 참조 추가
ALTER TABLE todo ADD COLUMN project_row_id BIGINT NULL AFTER user_row_id;
ALTER TABLE todo ADD COLUMN parent_row_id BIGINT NULL AFTER project_row_id;
ALTER TABLE todo ADD CONSTRAINT fk_todo_project FOREIGN KEY (project_row_id) REFERENCES todo_project (row_id);
ALTER TABLE todo ADD CONSTRAINT fk_todo_parent FOREIGN KEY (parent_row_id) REFERENCES todo (row_id);

-- 4. Todo Tag Mapping (다대다 매핑)
CREATE TABLE todo_tag_mapping (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    todo_row_id         BIGINT          NOT NULL,
    tag_row_id          BIGINT          NOT NULL,
    PRIMARY KEY (row_id),
    UNIQUE KEY UK_todo_tag (todo_row_id, tag_row_id),
    CONSTRAINT fk_ttm_todo FOREIGN KEY (todo_row_id) REFERENCES todo (row_id),
    CONSTRAINT fk_ttm_tag FOREIGN KEY (tag_row_id) REFERENCES todo_tag (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
