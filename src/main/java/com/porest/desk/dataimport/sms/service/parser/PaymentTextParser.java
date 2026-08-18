package com.porest.desk.dataimport.sms.service.parser;

import com.porest.desk.dataimport.sms.service.SmsParsed;

import java.time.LocalDate;

/**
 * 결제 알림·문자 한 통을 구조화하는 파서.
 *
 * <p><b>왜 나눠 두는가</b> — 예전에는 파일 하나에 문자·카드사 앱·은행 앱 규칙이 전부
 * 뒤엉켜 있었다. 그러다 은행 포맷을 맞추려고 가맹점 추출을 손대면 카드사 문자가
 * 깨질 수 있는 상태였다(실제로 케이뱅크·현대카드에서 거래처가 잘못 잡히는 버그가 났다).
 * 소스마다 다른 부분만 따로 두면 한쪽을 고쳐도 다른 쪽이 안 흔들린다.
 *
 * <p><b>확장 방법</b> — 특정 카드사·은행만 유독 안 맞으면 그 기관 전용 구현을
 * 새로 추가하면 된다. {@link #priority()} 를 낮게 주어 일반 파서보다 먼저 보게 하고,
 * {@code AbstractPaymentTextParser} 를 상속해 <b>어긋나는 메서드 하나만</b> 재정의한다.
 * 나머지 필드 추출은 부모가 그대로 처리하므로 기존 파일은 손대지 않는다.
 *
 * <pre>
 * class KBankTextParser extends AbstractPaymentTextParser {
 *     public int priority() { return 10; }          // 일반 파서(100)보다 먼저
 *     public boolean accepts(String t) { ... }      // 케이뱅크 알림일 때만
 *     protected String findMerchant(String t) { ... } // 이 은행만의 규칙
 * }
 * </pre>
 */
public interface PaymentTextParser {

    /**
     * 이 파서가 <b>결제로 인정</b>하는 문자인가.
     *
     * <p>{@code true} 를 준 파서 중 {@link #priority()} 가 가장 낮은 하나가 파싱을 맡는다.
     */
    boolean accepts(String text);

    /**
     * 이 문자를 <b>전담</b>하는가 — 소유권 주장.
     *
     * <p>{@link #accepts} 와 나누는 이유가 있다. 은행 앱 알림은 "이 파서가 봐야 할
     * 문자"({@code claims}) 이면서 동시에 "결제는 아님"({@code accepts} 이 거짓)일 수
     * 있다 — 계좌이체가 그렇다. 이때 뒤의 느슨한 파서로 넘어가면 이체가 결제로
     * 통과해 버린다. 전담을 선언한 파서가 거절하면 거기서 끝난다.
     *
     * <p>기본값은 {@code accepts} 와 같다 — 특별히 전담할 이유가 없는 파서는
     * 받아들인 것만 맡는다.
     */
    default boolean claims(String text) {
        return accepts(text);
    }

    /**
     * 파싱 순서 — 작을수록 먼저 본다.
     *
     * <p>기관 전용 파서는 낮게(10~50), 소스 유형별 일반 파서는 100 을 쓴다.
     * 특수한 규칙이 일반 규칙을 이기게 하려는 것이다.
     */
    int priority();

    /**
     * 파싱 — {@code today} 는 연도 유추 기준일이다.
     *
     * <p>문자에는 연도가 없어(대개 {@code MM/DD}) 현재 날짜로 유추해야 하는데,
     * 시계를 직접 읽으면 테스트가 연말·연초에만 깨진다. 그래서 주입받는다.
     */
    SmsParsed parse(String text, LocalDate today);
}
