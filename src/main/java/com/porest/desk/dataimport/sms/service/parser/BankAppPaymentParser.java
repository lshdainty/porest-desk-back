package com.porest.desk.dataimport.sms.service.parser;

import java.util.List;

/**
 * 은행 앱 알림 — 체크카드 결제가 여기로 온다.
 *
 * <p><b>왜 따로 두는가</b> — 체크카드는 발급 주체가 은행이라 승인 알림이 카드사가
 * 아니라 은행에서 온다(토스·카카오뱅크·케이뱅크가 대표적이다). 그런데 은행은 결제
 * 말고도 입금·이체·잔액·공지 알림을 훨씬 많이 보낸다.
 *
 * <p>일반 게이트는 "출금" 한 단어만 있어도 통과시키는데, <b>계좌이체 출금이 그렇게
 * 들어오면 지출로 둔갑한다</b> — 이체는 자산 사이의 이동이지 쓴 돈이 아니다. 저장은
 * {@code EXPENSE} 로 고정이라 그대로 장부가 틀어진다.
 *
 * <p>그래서 카드로 긁었다는 신호를 <b>명시적으로 요구</b>하고, 이체·입금이 분명한
 * 알림은 버린다. "출금" 자체는 배제하지 않는다 — 체크카드 결제를 그렇게 적는 은행이
 * 있는데, 카드 신호가 없으면 어차피 위 조건에서 걸린다.
 *
 * <p>같은 판정이 안드로이드 네이티브에도 있지만(수신 단계에서 거른다), 웹·iOS 는
 * 사용자가 아무 알림이나 붙여넣을 수 있어 <b>서버에도 같은 방어가 있어야 한다</b>.
 */
public class BankAppPaymentParser extends AbstractPaymentTextParser {

    /** 은행 알림에서 "카드로 긁었다" 를 알리는 말. 하나는 있어야 결제로 본다. */
    private static final List<String> CARD_MARKERS =
        List.of("승인", "체크카드", "카드결제", "카드 결제", "일시불", "결제");

    /** 명백히 결제가 아닌 알림 — 있으면 버린다. */
    private static final List<String> EXCLUDE =
        List.of("입금", "이체", "송금", "자동납부");

    /**
     * 은행이 보낸 알림 중 <b>카드 결제</b>만 받는다.
     *
     * <p>발신 기관이 인터넷은행일 때만 이 파서가 맡는다 — 시중은행 카드사 문자
     * ("KB국민카드1234승인")까지 여기로 오면 멀쩡한 결제가 엄격 게이트에 걸린다.
     */
    @Override
    public boolean accepts(String text) {
        if (text == null || text.isBlank()) return false;
        if (!SmsPaymentParser.isBankIssuer(text)) return false;
        if (findAmount(text) == null) return false;
        if (EXCLUDE.stream().anyMatch(text::contains)) return false;
        return CARD_MARKERS.stream().anyMatch(text::contains);
    }

    /**
     * 인터넷은행이 보낸 알림은 결제가 아니어도 <b>이 파서가 전담</b>한다.
     *
     * <p>거절한 뒤 뒤의 느슨한 파서로 넘어가면 "출금" 한 단어만으로 계좌이체가
     * 결제로 통과한다 — 엄격 게이트를 둔 의미가 사라진다.
     */
    @Override
    public boolean claims(String text) {
        return text != null && !text.isBlank() && SmsPaymentParser.isBankIssuer(text);
    }

    /** 일반 파서보다 먼저 본다 — 은행 알림은 엄격 게이트를 타야 한다. */
    @Override
    public int priority() {
        return 50;
    }
}
