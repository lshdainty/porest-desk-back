-- =============================================
-- POREST Desk - Phase 2B: Asset & Expense Enhancements
-- =============================================

-- 1. Users: 월 시작일 추가
ALTER TABLE users ADD COLUMN month_start_day INT NOT NULL DEFAULT 1 AFTER timezone;

-- 2. Asset (자산)
CREATE TABLE asset (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    asset_name          VARCHAR(100)    NOT NULL,
    asset_type          VARCHAR(30)     NOT NULL,
    balance             BIGINT          NOT NULL DEFAULT 0,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'KRW',
    icon                VARCHAR(50)     NULL,
    color               VARCHAR(20)     NULL,
    institution         VARCHAR(100)    NULL,
    memo                VARCHAR(500)    NULL,
    sort_order          INT             NOT NULL DEFAULT 0,
    is_included_in_total VARCHAR(1)     NOT NULL DEFAULT 'Y',
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_asset_user FOREIGN KEY (user_row_id) REFERENCES users (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Expense: 자산 연결 및 거래처 추가
ALTER TABLE expense ADD COLUMN asset_row_id BIGINT NULL AFTER category_row_id;
ALTER TABLE expense ADD COLUMN merchant VARCHAR(100) NULL AFTER payment_method;
ALTER TABLE expense ADD CONSTRAINT fk_expense_asset FOREIGN KEY (asset_row_id) REFERENCES asset (row_id);

-- 4. Asset Transfer (자산 이체)
CREATE TABLE asset_transfer (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    from_asset_row_id   BIGINT          NOT NULL,
    to_asset_row_id     BIGINT          NOT NULL,
    amount              BIGINT          NOT NULL,
    fee                 BIGINT          NOT NULL DEFAULT 0,
    description         VARCHAR(500)    NULL,
    transfer_date       DATE            NOT NULL,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_transfer_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_transfer_from_asset FOREIGN KEY (from_asset_row_id) REFERENCES asset (row_id),
    CONSTRAINT fk_transfer_to_asset FOREIGN KEY (to_asset_row_id) REFERENCES asset (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Expense Template (자주 사용하는 내역)
CREATE TABLE expense_template (
    row_id              BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id         BIGINT          NOT NULL,
    template_name       VARCHAR(100)    NOT NULL,
    category_row_id     BIGINT          NULL,
    asset_row_id        BIGINT          NULL,
    expense_type        VARCHAR(20)     NOT NULL,
    amount              BIGINT          NULL,
    description         VARCHAR(500)    NULL,
    merchant            VARCHAR(100)    NULL,
    payment_method      VARCHAR(30)     NULL,
    use_count           INT             NOT NULL DEFAULT 0,
    sort_order          INT             NOT NULL DEFAULT 0,
    create_at           DATETIME(6)     NULL,
    create_by           VARCHAR(50)     NULL,
    create_ip           VARCHAR(45)     NULL,
    modify_at           DATETIME(6)     NULL,
    modify_by           VARCHAR(50)     NULL,
    modify_ip           VARCHAR(45)     NULL,
    is_deleted          VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_template_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_template_category FOREIGN KEY (category_row_id) REFERENCES expense_category (row_id),
    CONSTRAINT fk_template_asset FOREIGN KEY (asset_row_id) REFERENCES asset (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Recurring Transaction (자동 반복 거래)
CREATE TABLE recurring_transaction (
    row_id                  BIGINT          NOT NULL AUTO_INCREMENT,
    user_row_id             BIGINT          NOT NULL,
    category_row_id         BIGINT          NULL,
    asset_row_id            BIGINT          NULL,
    expense_type            VARCHAR(20)     NOT NULL,
    amount                  BIGINT          NOT NULL,
    description             VARCHAR(500)    NULL,
    merchant                VARCHAR(100)    NULL,
    payment_method          VARCHAR(30)     NULL,
    frequency               VARCHAR(20)     NOT NULL,
    interval_value          INT             NOT NULL DEFAULT 1,
    day_of_week             INT             NULL,
    day_of_month            INT             NULL,
    start_date              DATE            NOT NULL,
    end_date                DATE            NULL,
    next_execution_date     DATE            NOT NULL,
    last_executed_at        DATETIME(6)     NULL,
    is_active               VARCHAR(1)      NOT NULL DEFAULT 'Y',
    create_at               DATETIME(6)     NULL,
    create_by               VARCHAR(50)     NULL,
    create_ip               VARCHAR(45)     NULL,
    modify_at               DATETIME(6)     NULL,
    modify_by               VARCHAR(50)     NULL,
    modify_ip               VARCHAR(45)     NULL,
    is_deleted              VARCHAR(1)      NOT NULL DEFAULT 'N',
    PRIMARY KEY (row_id),
    CONSTRAINT fk_recurring_user FOREIGN KEY (user_row_id) REFERENCES users (row_id),
    CONSTRAINT fk_recurring_category FOREIGN KEY (category_row_id) REFERENCES expense_category (row_id),
    CONSTRAINT fk_recurring_asset FOREIGN KEY (asset_row_id) REFERENCES asset (row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
