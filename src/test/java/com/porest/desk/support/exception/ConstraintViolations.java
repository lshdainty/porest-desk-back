package com.porest.desk.support.exception;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 테스트용 — DB 가 실제로 던지는 모양의 무결성 위반을 만든다.
 *
 * <p><b>왜 필요한가.</b> 종전 테스트들은 {@code new DataIntegrityViolationException("UK_...")}
 * 처럼 <b>원인 없는</b> 예외로 유일성 위반을 흉내 냈다. 서비스가 종류를 안 가리고 전부
 * "이름 중복" 으로 번역하던 시절엔 그것으로 충분했지만, 이제는 종류를 가린다(QA #81) —
 * 원인이 없으면 판정할 근거도 없어 UNIQUE 로 안 읽힌다. 실물은 언제나
 * {@code 스프링 래퍼 → 하이버네이트 ConstraintViolationException → JDBC SQLException} 3단이다
 * (2026-09-07 H2 슬라이스 실측, {@code IntegrityViolationKindOnH2Test} 가 그 사실을 붙들고 있다).
 *
 * <p>여기 한 곳에 두는 이유는 같은 조립이 열 곳 넘는 테스트에 필요하기 때문이다. 각자 만들면
 * 하이버네이트가 사슬 모양을 바꾸는 날 열 곳을 따로 고쳐야 한다.
 */
public final class ConstraintViolations {

    private ConstraintViolations() {
    }

    /** H2 는 SQLState 와 벤더 에러코드가 같은 숫자다. */
    private static final String H2_DUPLICATE_KEY = "23505";
    private static final String H2_NULL_NOT_ALLOWED = "23502";

    /** 활성 이름 UNIQUE 같은 유일성 위반 — 도메인 서비스가 "이름 중복" 으로 번역해야 하는 것. */
    public static DataIntegrityViolationException unique(String constraintName) {
        return wrap(ConstraintViolationException.ConstraintKind.UNIQUE, H2_DUPLICATE_KEY, constraintName);
    }

    /** NOT NULL 위반 — 도메인 서비스가 손대지 말고 그대로 올려야 하는 것(QA #81). */
    public static DataIntegrityViolationException notNull(String columnName) {
        return wrap(ConstraintViolationException.ConstraintKind.NOT_NULL, H2_NULL_NOT_ALLOWED, columnName);
    }

    private static DataIntegrityViolationException wrap(
            ConstraintViolationException.ConstraintKind kind, String sqlState, String constraintName) {
        SQLException sql = new SQLIntegrityConstraintViolationException(
                constraintName, sqlState, Integer.parseInt(sqlState));
        return new DataIntegrityViolationException(constraintName,
                new ConstraintViolationException("could not execute statement", sql, kind, constraintName));
    }
}
