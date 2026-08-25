package com.porest.desk.dataimport.sms.service;

import com.porest.desk.dataimport.sms.service.parser.PaymentTextParsers;

import java.time.LocalDate;

/**
 * 결제 문자·알림 파싱 진입점.
 *
 * <p>실제 규칙은 {@code service.parser} 패키지로 옮겼다 — 문자·카드사 앱·은행 앱이
 * 한 파일에 뒤엉켜 있어, 한쪽을 고치면 다른 쪽이 깨질 수 있었기 때문이다
 * (실제로 케이뱅크·현대카드에서 거래처가 잘못 잡히는 버그가 났다).
 *
 * <p>이 클래스는 호출부가 쓰던 이름을 그대로 두기 위한 얇은 위임이다.
 * 새 코드는 {@link PaymentTextParsers} 를 직접 써도 된다.
 */
public final class SmsParser {

    private SmsParser() {
    }

    /** 결제 알림으로 볼 수 있는가 — 클라이언트 로컬 프리필터와 같은 판정. */
    public static boolean looksLikePayment(String text) {
        return PaymentTextParsers.looksLikePayment(text);
    }

    /**
     * JVM 기본 타임존의 오늘로 파싱. 컨테이너(UTC)에서는 KST 새벽·연말연시에 연도 유추가
     * 어긋나므로, 사용자 맥락에서는 {@link #parse(String, LocalDate)} 에 사용자 기준 오늘
     * (UserClock)을 넘겨라.
     */
    public static SmsParsed parse(String text) {
        return PaymentTextParsers.parse(text);
    }

    /** 파싱 — {@code today} 는 연도 유추 기준일(테스트가 시계에 흔들리지 않게 주입). */
    public static SmsParsed parse(String text, LocalDate today) {
        return PaymentTextParsers.parse(text, today);
    }
}
