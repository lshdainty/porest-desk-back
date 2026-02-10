-- =========================================
-- V8: 기능 간 연동 (일정↔지출, Todo↔지출)
-- =========================================

-- expense에 일정/할일 참조 추가
ALTER TABLE `expense` ADD COLUMN `calendar_event_row_id` bigint(20) DEFAULT NULL COMMENT '연결된 일정' AFTER `payment_method`;
ALTER TABLE `expense` ADD COLUMN `todo_row_id` bigint(20) DEFAULT NULL COMMENT '연결된 할일' AFTER `calendar_event_row_id`;
ALTER TABLE `expense` ADD CONSTRAINT `FK_expense_event` FOREIGN KEY (`calendar_event_row_id`) REFERENCES `calendar_event` (`row_id`);
ALTER TABLE `expense` ADD CONSTRAINT `FK_expense_todo` FOREIGN KEY (`todo_row_id`) REFERENCES `todo` (`row_id`);
