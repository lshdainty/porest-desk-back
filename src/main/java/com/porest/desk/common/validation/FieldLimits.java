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

    /**
     * 사용자가 붙이는 별칭. asset.asset_name 이 이 층이다.
     *
     * <p>컬럼은 varchar(100) 이지만 상한은 <b>정책</b>이다(CONTENT_MAX 와 같은 부류) — 웹·앱
     * 입력칸이 30자에서 끊는데 서버엔 상한이 없어 API 를 직접 부르면 31자가 그대로 저장됐고,
     * 목록·홈 카드 레이아웃이 그 길이를 감당하지 못한다(QA 2026-09-03 #65).
     *
     * <p>이 숫자를 <b>내리면</b> 기존 데이터가 막힌다 — 수정 저장은 이름을 그대로 다시 보내므로,
     * 상한보다 긴 이름을 가진 자산은 아무것도 안 고쳐도 400 이 된다.
     */
    int ALIAS_MAX = 30;

    /**
     * 사용자가 붙이는 <b>이름</b> 중 varchar(50) 층.
     * expense_category.category_name · event_label.label_name · todo_tag.tag_name ·
     * stock_watch_group.group_name 이 여기다.
     *
     * <p>{@link com.porest.desk.common.util.NameNormalizer} 가 이 값을 상한으로 쓴다.
     * <b>컬럼 폭 그대로라 기존 데이터를 막지 않는다</b> — 화면(웹·앱)이 12자에서 끊는 것과는
     * 별개다. 화면 상한을 서버에 그대로 옮기면 그보다 긴 이름을 가진 기존 행은 이름을
     * 그대로 다시 보내는 수정 저장조차 400 이 된다({@link #ALIAS_MAX} 가 겪은 함정).
     */
    int NAME_MAX = 50;

    /**
     * 같은 이름 층의 varchar(100) 쪽.
     * expense_template.template_name · saving_goal.title · memo_folder.folder_name ·
     * dutch_pay_participant.participant_name 이 여기다.
     */
    int WIDE_NAME_MAX = 100;
}
