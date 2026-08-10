package com.porest.desk.common.audit;

import com.porest.core.audit.AbstractAccessLog;
import com.porest.core.audit.AccessLogEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개인정보 접속기록 엔티티.
 *
 * <p>desk 는 자산·가계부·메모·캘린더 모두 <strong>본인 데이터</strong>라 고시 제8조의
 * 접속기록(개인정보취급자가 타인 정보를 처리한 기록) 의무 대상이 아니다.
 * 이 테이블은 <strong>대량 반출(export)</strong> 흔적을 남기기 위한 것이다 —
 * 계정이 탈취됐을 때 소비 내역 전체가 빠져나간 사실을 사후에 확인할 수 있어야 한다.
 * (고시 제13조 출력·복사 시 보호조치의 연장선)</p>
 *
 * <p>본인 조회까지 전부 기록하면 노이즈에 묻히므로 대상을 좁게 유지한다.</p>
 *
 * @see AbstractAccessLog 공통 컬럼
 * @see AccessLogPortImpl 저장 담당
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "access_logs")
public class AccessLog extends AbstractAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    /** core 가 넘겨준 기록 내용을 엔티티로 옮긴다. */
    public static AccessLog from(AccessLogEntry entry) {
        AccessLog accessLog = new AccessLog();
        accessLog.apply(entry);
        return accessLog;
    }
}
