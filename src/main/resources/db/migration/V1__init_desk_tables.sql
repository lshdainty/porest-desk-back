-- =============================================
-- POREST Desk - Initial Schema Migration
-- =============================================

-- 1. Users
CREATE TABLE users (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    sso_user_row_id BIGINT          NULL,
    user_id         VARCHAR(20)     NOT NULL,
    user_name       VARCHAR(20)     NOT NULL,
    user_email      VARCHAR(100)    NOT NULL,
    dashboard       TEXT            NULL,
    timezone        VARCHAR(50)     NOT NULL DEFAULT 'Asia/Seoul',
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Todo
CREATE TABLE todo (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    content         TEXT            NULL,
    priority        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    category        VARCHAR(50)     NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    due_date        DATE            NULL,
    completed_at    DATETIME(6)     NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_todo_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Calendar Event
CREATE TABLE calendar_event (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    description     TEXT            NULL,
    event_type      VARCHAR(20)     NOT NULL DEFAULT 'PERSONAL',
    color           VARCHAR(20)     NULL,
    start_date      DATETIME(6)     NOT NULL,
    end_date        DATETIME(6)     NOT NULL,
    is_all_day      VARCHAR(1)      NOT NULL DEFAULT 'N',
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_calendar_event_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Memo Folder
CREATE TABLE memo_folder (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    parent_row_id   BIGINT          NULL,
    folder_name     VARCHAR(100)    NOT NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_memo_folder_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_memo_folder_parent FOREIGN KEY (parent_row_id) REFERENCES memo_folder (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Memo
CREATE TABLE memo (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    folder_row_id   BIGINT          NULL,
    title           VARCHAR(200)    NOT NULL,
    content         LONGTEXT        NULL,
    is_pinned       VARCHAR(1)      NOT NULL DEFAULT 'N',
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_memo_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_memo_folder FOREIGN KEY (folder_row_id) REFERENCES memo_folder (row_id),
    FULLTEXT INDEX idx_memo_fulltext (title, content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Calculator History
CREATE TABLE calculator_history (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    expression      VARCHAR(500)    NOT NULL,
    result          VARCHAR(100)    NOT NULL,
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    PRIMARY KEY (row_id),
    CONSTRAINT fk_calculator_history_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Timer Session
CREATE TABLE timer_session (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    timer_type          VARCHAR(20)     NOT NULL,
    label               VARCHAR(100)    NULL,
    start_time          DATETIME(6)     NOT NULL,
    end_time            DATETIME(6)     NULL,
    duration_seconds    BIGINT          NOT NULL DEFAULT 0,
    target_seconds      BIGINT          NULL,
    is_completed        VARCHAR(1)      NOT NULL DEFAULT 'N',
    laps                TEXT            NULL,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    PRIMARY KEY (row_id),
    CONSTRAINT fk_timer_session_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Expense Category
CREATE TABLE expense_category (
    row_id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id     BIGINT          NOT NULL,
    category_name   VARCHAR(50)     NOT NULL,
    icon            VARCHAR(50)     NULL,
    color           VARCHAR(20)     NULL,
    expense_type    VARCHAR(20)     NOT NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    create_at       DATETIME(6)     NULL,
    create_by       VARCHAR(50)     NULL,
    create_ip       VARCHAR(45)     NULL,
    modify_at       DATETIME(6)     NULL,
    modify_by       VARCHAR(50)     NULL,
    modify_ip       VARCHAR(45)     NULL,
    is_deleted      VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_expense_category_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Expense
CREATE TABLE expense (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    category_row_id     BIGINT          NOT NULL,
    expense_type        VARCHAR(20)     NOT NULL,
    amount              BIGINT          NOT NULL,
    description         VARCHAR(500)    NULL,
    expense_date        DATE            NOT NULL,
    payment_method      VARCHAR(30)     NULL,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_expense_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_expense_category FOREIGN KEY (category_row_id) REFERENCES expense_category (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Expense Budget
CREATE TABLE expense_budget (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    category_row_id     BIGINT          NULL,
    budget_amount       BIGINT          NOT NULL,
    budget_year         INT             NOT NULL,
    budget_month        INT             NOT NULL,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    PRIMARY KEY (row_id),
    CONSTRAINT fk_expense_budget_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_expense_budget_category FOREIGN KEY (category_row_id) REFERENCES expense_category (row_id),
    CONSTRAINT uk_expense_budget UNIQUE (user_row_id, category_row_id, budget_year, budget_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
