-- =========================================
-- V5: Dutch Pay (더치페이) 테이블 추가
-- =========================================

-- 더치페이
CREATE TABLE `dutch_pay` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_row_id` bigint(20) NOT NULL COMMENT '생성자',
    `title` varchar(200) NOT NULL COMMENT '더치페이 제목',
    `description` varchar(500) DEFAULT NULL COMMENT '설명',
    `total_amount` bigint(20) NOT NULL COMMENT '총 금액',
    `currency` varchar(10) NOT NULL DEFAULT 'KRW' COMMENT '통화',
    `split_method` varchar(20) NOT NULL DEFAULT 'EQUAL' COMMENT 'EQUAL, CUSTOM, RATIO',
    `dutch_pay_date` date NOT NULL COMMENT '더치페이 날짜',
    `is_settled` varchar(1) DEFAULT 'N' NOT NULL COMMENT '전체 정산 완료 여부',
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_dutch_pay_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='더치페이';

-- 더치페이 참가자
CREATE TABLE `dutch_pay_participant` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `dutch_pay_row_id` bigint(20) NOT NULL COMMENT '더치페이 참조',
    `participant_name` varchar(100) NOT NULL COMMENT '참가자 이름',
    `amount` bigint(20) NOT NULL COMMENT '부담 금액',
    `is_paid` varchar(1) DEFAULT 'N' NOT NULL COMMENT '정산 완료 여부',
    `paid_at` datetime(6) DEFAULT NULL COMMENT '정산 일시',
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_participant_dutch_pay` FOREIGN KEY (`dutch_pay_row_id`) REFERENCES `dutch_pay` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='더치페이 참가자';
