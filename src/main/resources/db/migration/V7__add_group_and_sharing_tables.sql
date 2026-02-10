-- =========================================
-- V7: 그룹/공유 인프라 테이블 추가
-- =========================================

-- 사용자 그룹
CREATE TABLE `user_group` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `group_name` varchar(100) NOT NULL COMMENT '그룹 이름',
    `description` varchar(500) DEFAULT NULL,
    `group_type` varchar(20) NOT NULL DEFAULT 'CUSTOM' COMMENT 'FAMILY, COUPLE, FRIENDS, CUSTOM',
    `invite_code` varchar(20) DEFAULT NULL COMMENT '초대 코드',
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    UNIQUE KEY `UK_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 그룹';

-- 그룹 멤버
CREATE TABLE `user_group_member` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `group_row_id` bigint(20) NOT NULL,
    `user_row_id` bigint(20) NOT NULL,
    `role` varchar(20) NOT NULL DEFAULT 'MEMBER' COMMENT 'OWNER, ADMIN, MEMBER',
    `joined_at` datetime(6) NOT NULL,
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    UNIQUE KEY `UK_group_user` (`group_row_id`, `user_row_id`),
    CONSTRAINT `FK_member_group` FOREIGN KEY (`group_row_id`) REFERENCES `user_group` (`row_id`),
    CONSTRAINT `FK_member_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='그룹 멤버';

-- 일정 댓글
CREATE TABLE `event_comment` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `event_row_id` bigint(20) NOT NULL,
    `user_row_id` bigint(20) NOT NULL,
    `parent_row_id` bigint(20) DEFAULT NULL COMMENT '대댓글 부모',
    `content` varchar(1000) NOT NULL,
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_comment_event` FOREIGN KEY (`event_row_id`) REFERENCES `calendar_event` (`row_id`),
    CONSTRAINT `FK_comment_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`),
    CONSTRAINT `FK_comment_parent` FOREIGN KEY (`parent_row_id`) REFERENCES `event_comment` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일정 댓글';

-- calendar_event에 그룹 참조 추가
ALTER TABLE `calendar_event` ADD COLUMN `group_row_id` bigint(20) DEFAULT NULL COMMENT '공유 그룹' AFTER `is_exception`;
ALTER TABLE `calendar_event` ADD CONSTRAINT `FK_event_group` FOREIGN KEY (`group_row_id`) REFERENCES `user_group` (`row_id`);

-- todo에 그룹, 담당자 참조 추가
ALTER TABLE `todo` ADD COLUMN `group_row_id` bigint(20) DEFAULT NULL COMMENT '공유 그룹' AFTER `parent_row_id`;
ALTER TABLE `todo` ADD COLUMN `assignee_row_id` bigint(20) DEFAULT NULL COMMENT '담당자' AFTER `group_row_id`;
ALTER TABLE `todo` ADD CONSTRAINT `FK_todo_group` FOREIGN KEY (`group_row_id`) REFERENCES `user_group` (`row_id`);
ALTER TABLE `todo` ADD CONSTRAINT `FK_todo_assignee` FOREIGN KEY (`assignee_row_id`) REFERENCES `users` (`row_id`);
