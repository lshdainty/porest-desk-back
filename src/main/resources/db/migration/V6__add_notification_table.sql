-- =========================================
-- V6: 알림 시스템 테이블 추가
-- =========================================

CREATE TABLE `notification` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_row_id` bigint(20) NOT NULL COMMENT '알림 수신자',
    `notification_type` varchar(30) NOT NULL COMMENT 'EVENT_REMINDER, BUDGET_ALERT, TODO_REMINDER, SYSTEM',
    `title` varchar(200) NOT NULL COMMENT '알림 제목',
    `message` varchar(1000) NOT NULL COMMENT '알림 메시지',
    `reference_type` varchar(30) DEFAULT NULL COMMENT 'CALENDAR_EVENT, EXPENSE_BUDGET, TODO',
    `reference_id` bigint(20) DEFAULT NULL COMMENT '참조 엔티티 ID',
    `is_read` varchar(1) DEFAULT 'N' NOT NULL COMMENT '읽음 여부',
    `read_at` datetime(6) DEFAULT NULL,
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_notification_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`),
    KEY `IDX_notification_user_read` (`user_row_id`, `is_read`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='알림';
