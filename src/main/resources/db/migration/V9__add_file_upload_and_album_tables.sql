-- =========================================
-- V9: 파일 업로드 + 공유 앨범 테이블 추가
-- =========================================

-- 파일 첨부
CREATE TABLE `file_attachment` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_row_id` bigint(20) NOT NULL,
    `original_name` varchar(255) NOT NULL COMMENT '원본 파일명',
    `stored_name` varchar(255) NOT NULL COMMENT '저장 파일명 (UUID)',
    `file_path` varchar(500) NOT NULL COMMENT '저장 경로',
    `content_type` varchar(100) NOT NULL COMMENT 'MIME 타입',
    `file_size` bigint(20) NOT NULL COMMENT '파일 크기 (bytes)',
    `reference_type` varchar(30) NOT NULL COMMENT 'EXPENSE_RECEIPT, ALBUM_PHOTO, MEMO_ATTACHMENT',
    `reference_row_id` bigint(20) DEFAULT NULL COMMENT '참조 엔티티 ID',
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_file_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`),
    KEY `IDX_file_reference` (`reference_type`, `reference_row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='파일 첨부';

-- 공유 앨범
CREATE TABLE `album` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `group_row_id` bigint(20) NOT NULL,
    `album_name` varchar(100) NOT NULL COMMENT '앨범 이름',
    `description` varchar(500) DEFAULT NULL,
    `cover_file_row_id` bigint(20) DEFAULT NULL COMMENT '커버 이미지',
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_album_group` FOREIGN KEY (`group_row_id`) REFERENCES `user_group` (`row_id`),
    CONSTRAINT `FK_album_cover` FOREIGN KEY (`cover_file_row_id`) REFERENCES `file_attachment` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='공유 앨범';

-- 앨범 사진
CREATE TABLE `album_photo` (
    `row_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `album_row_id` bigint(20) NOT NULL,
    `file_row_id` bigint(20) NOT NULL,
    `user_row_id` bigint(20) NOT NULL COMMENT '업로드한 사용자',
    `caption` varchar(500) DEFAULT NULL COMMENT '사진 설명',
    `sort_order` int(11) NOT NULL DEFAULT 0,
    `is_deleted` varchar(1) DEFAULT 'N' NOT NULL,
    `create_at` datetime(6) DEFAULT NULL,
    `create_by` varchar(100) DEFAULT NULL,
    `create_ip` varchar(50) DEFAULT NULL,
    `modify_at` datetime(6) DEFAULT NULL,
    `modify_by` varchar(100) DEFAULT NULL,
    `modify_ip` varchar(50) DEFAULT NULL,
    PRIMARY KEY (`row_id`),
    CONSTRAINT `FK_photo_album` FOREIGN KEY (`album_row_id`) REFERENCES `album` (`row_id`),
    CONSTRAINT `FK_photo_file` FOREIGN KEY (`file_row_id`) REFERENCES `file_attachment` (`row_id`),
    CONSTRAINT `FK_photo_user` FOREIGN KEY (`user_row_id`) REFERENCES `users` (`row_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='앨범 사진';
