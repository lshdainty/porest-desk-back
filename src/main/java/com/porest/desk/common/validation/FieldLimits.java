package com.porest.desk.common.validation;

/**
 * 요청 문자열 길이 상한.
 *
 * <p>값은 전부 {@code porest-sql/desk/table/*.sql} 의 컬럼 폭에서 따왔다 —
 * <b>여기 숫자를 올리려면 DDL 이 먼저다</b>(이 레포에는 마이그레이션 도구가 없어
 * 상수만 올리면 dev/prod 에서 Data too long 으로 터진다).
 *
 * <p>종전엔 상한이 없어 컬럼을 넘는 입력이 DB 제약에 걸려 500 으로 나갔다
 * (QA 2026-09-03 #24 #27 #30 #31 #33). 층을 넷으로 못 박아 다음에 새 필드가 생겨도
 * 리터럴을 새로 고르지 않게 한다.
 */
public interface FieldLimits {

    /** 제목. todo.title · memo.title · calendar_event.title 모두 varchar(200). */
    int TITLE_MAX = 200;

    /**
     * 긴 본문. todo.content · memo.content 는 LONGTEXT,
     * calendar_event.description 은 TEXT(65,535 byte — 4바이트 이모지로만 10,000자를 채워도 40,000 byte).
     * 컬럼이 아니라 <b>정책</b>으로 정한 값이다(QA 가 관측한 최대 입력 10,000자를 그대로 상한으로 삼아
     * 기존 데이터가 막히지 않게 했다).
     */
    int CONTENT_MAX = 10_000;

    /** 짧은 메모·설명. expense.description varchar(500) 이 이 층의 컬럼 상한이다. */
    int SHORT_NOTE_MAX = 500;

    /** 분류 문자열. todo.category · memo.tag 모두 varchar(50). */
    int LABEL_MAX = 50;
}
