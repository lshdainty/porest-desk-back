package com.porest.desk.dataimport.sms.service.parser;

import com.porest.desk.dataimport.sms.service.SmsCardIssuer;

import java.util.List;
import java.util.Locale;

/**
 * 문자(SMS)와 카드사 앱 알림 — 결제 알림이 오는 가장 흔한 경로.
 *
 * <p>이 둘을 한 파서로 묶은 이유는 <b>내용이 같기 때문</b>이다. 카드사 앱 푸시는
 * 문자로 보내던 승인 내역을 그대로 알림으로 띄운 것이라 문구가 거의 겹친다.
 * 판정 기준을 나눌 실익이 없다.
 *
 * <p>게이트는 느슨하다 — 금액과 결제 키워드만 있으면 통과한다. 이 경로로 오는
 * 알림은 대부분 결제라서 굳이 좁힐 이유가 없고, 애매한 건 신뢰도를 낮춰
 * 사용자 확인으로 넘긴다. (은행 앱은 사정이 달라 {@link BankAppPaymentParser} 가 맡는다.)
 */
public class SmsPaymentParser extends AbstractPaymentTextParser {

    /** 결제 문자로 볼 최소 조건에 쓰는 키워드 — 하나도 없으면 아무 텍스트로 본다. */
    private static final List<String> PAYMENT_KEYWORDS =
        List.of("승인", "취소", "결제", "출금", "사용");

    /**
     * 금액과 결제 키워드가 함께 있어야 한다.
     *
     * <p>이 게이트가 있어야 클립보드의 아무 텍스트나 서버로 흘러가지 않는다 —
     * 앱·웹의 로컬 프리필터도 같은 규칙을 쓴다.
     */
    @Override
    public boolean accepts(String text) {
        if (text == null || text.isBlank()) return false;
        if (findAmount(text) == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return PAYMENT_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /** 은행 앱 파서가 먼저 보도록, 이쪽은 기본 순위를 그대로 쓴다. */
    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }

    /** 은행 문자인지 판단할 때 쓰는 공용 헬퍼 — 라우팅에서 참조한다. */
    protected static boolean isBankIssuer(String text) {
        SmsCardIssuer issuer = SmsCardIssuer.detect(text);
        return issuer != null && issuer.isBank();
    }
}
