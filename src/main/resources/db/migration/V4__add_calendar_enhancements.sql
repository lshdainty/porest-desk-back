-- V4: Calendar enhancements - Labels, Reminders, Recurrence, Location

-- Event Label table
CREATE TABLE `event_label` (
  `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_row_id` bigint(20) NOT NULL,
  `label_name` varchar(50) NOT NULL COMMENT '라벨명',
  `color` varchar(20) NOT NULL COMMENT '색상',
  `sort_order` int(11) NOT NULL DEFAULT 0,
  `is_deleted` varchar(1) NOT NULL DEFAULT 'N',
  `create_at` datetime(6) DEFAULT NULL,
  `create_by` varchar(200) DEFAULT NULL,
  `create_ip` varchar(50) DEFAULT NULL,
  `modify_at` datetime(6) DEFAULT NULL,
  `modify_by` varchar(200) DEFAULT NULL,
  `modify_ip` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`row_id`),
  CONSTRAINT `FK_event_label_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일정 라벨';

-- Event Reminder table
CREATE TABLE `event_reminder` (
  `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_row_id` bigint(20) NOT NULL,
  `reminder_type` varchar(20) NOT NULL DEFAULT 'NOTIFICATION' COMMENT 'NOTIFICATION, EMAIL',
  `minutes_before` int(11) NOT NULL COMMENT '일정 시작 전 분',
  `is_sent` varchar(1) NOT NULL DEFAULT 'N',
  `sent_at` datetime(6) DEFAULT NULL,
  `create_at` datetime(6) DEFAULT NULL,
  `create_by` varchar(200) DEFAULT NULL,
  `create_ip` varchar(50) DEFAULT NULL,
  `modify_at` datetime(6) DEFAULT NULL,
  `modify_by` varchar(200) DEFAULT NULL,
  `modify_ip` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`row_id`),
  CONSTRAINT `FK_reminder_event` FOREIGN KEY (`event_row_id`) REFERENCES `calendar_event` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='일정 알림';

-- Add new columns to calendar_event
ALTER TABLE `calendar_event` ADD COLUMN `label_row_id` bigint(20) DEFAULT NULL AFTER `is_all_day`;
ALTER TABLE `calendar_event` ADD COLUMN `location` varchar(500) DEFAULT NULL COMMENT '장소' AFTER `label_row_id`;
ALTER TABLE `calendar_event` ADD COLUMN `rrule` varchar(500) DEFAULT NULL COMMENT 'RFC 5545 반복 규칙' AFTER `location`;
ALTER TABLE `calendar_event` ADD COLUMN `recurrence_id` bigint(20) DEFAULT NULL COMMENT '원본 반복 이벤트 참조' AFTER `rrule`;
ALTER TABLE `calendar_event` ADD COLUMN `is_exception` varchar(1) NOT NULL DEFAULT 'N' COMMENT '반복 예외 여부' AFTER `recurrence_id`;

ALTER TABLE `calendar_event` ADD CONSTRAINT `FK_event_label` FOREIGN KEY (`label_row_id`) REFERENCES `event_label` (`row_id`);
