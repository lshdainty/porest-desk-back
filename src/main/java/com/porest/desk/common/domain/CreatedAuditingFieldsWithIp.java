package com.porest.desk.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성(create) 전용 JPA Auditing 필드 + IP
 * <p>
 * create_at / create_by / create_ip 만 가집니다. 수정(modify) 컬럼이 없는
 * append-only 성격의 엔티티(seed 재동기화 시 DELETE 후 INSERT 되는 카드 카탈로그
 * 하위 테이블 등)에서 상속받아 사용합니다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class CreatedAuditingFieldsWithIp {

    @CreatedDate
    /** [UTC] 시스템 기록 시각 — 저장·비교 UTC, 표시할 때만 사용자 타임존 변환 */
    @Column(name = "create_at", nullable = false, updatable = false)
    private LocalDateTime createAt;

    @CreatedBy
    @Column(name = "create_by", length = 50, updatable = false)
    private String createBy;

    @Column(name = "create_ip", length = 45, updatable = false)
    private String createIp;

    public void setCreateIp(String ip) {
        this.createIp = ip;
    }
}
