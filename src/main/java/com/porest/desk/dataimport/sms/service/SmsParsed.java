package com.porest.desk.dataimport.sms.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 문자에서 뽑아낸 원시 결과 — 아직 사용자 데이터(자산·카테고리)와 엮이기 전이다.
 *
 * <p>자산 매칭·카테고리 추론은 {@code SmsImportService} 가 이 위에 얹는다.
 * 파서를 사용자 문맥에서 떼어 놓아야 포맷 테스트를 픽스처만으로 돌릴 수 있다.
 *
 * @param matched            결제 문자로 인식했는가. false 면 나머지 필드는 의미가 없다
 * @param confidence         신뢰도
 * @param issuer             카드사(미상이면 null)
 * @param cardLast4          카드 끝 4자리(마스킹돼 못 읽으면 null)
 * @param cancel             취소·승인취소 문자인가
 * @param amount             결제 금액(원). 외화 결제도 원화 청구액이 있으면 채운다
 * @param installmentMonths  할부 개월(일시불·1개월이면 null)
 * @param occurredAt         결제 일시(KST 벽시계). 연도는 문자에 없으므로 유추한다
 * @param merchant           가맹점명
 * @param originalAmount     외화 결제 시 원 통화 금액
 * @param originalCurrency   외화 결제 시 통화 코드(ISO 4217)
 */
public record SmsParsed(
    boolean matched,
    SmsConfidence confidence,
    SmsCardIssuer issuer,
    String cardLast4,
    boolean cancel,
    Long amount,
    Integer installmentMonths,
    LocalDateTime occurredAt,
    String merchant,
    BigDecimal originalAmount,
    String originalCurrency
) {

    /** 결제 문자가 아니다 — 클립보드에 들어온 아무 텍스트. */
    public static SmsParsed noMatch() {
        return new SmsParsed(false, SmsConfidence.LOW, null, null, false,
            null, null, null, null, null, null);
    }

    /**
     * 카드 식별 힌트 — 자산 매핑을 기억할 때의 키.
     *
     * <p>"KB국민카드|1234" 처럼 카드사와 끝 4자리를 합친다. 끝자리가 안 읽히면
     * 카드사만으로 기억한다(카드사당 카드 한 장인 흔한 경우는 이것으로 충분하다).
     * 둘 다 없으면 기억할 수 없으므로 null.
     */
    public String cardHint() {
        if (issuer == null && cardLast4 == null) return null;
        String issuerPart = issuer == null ? "" : issuer.displayName();
        String last4Part = cardLast4 == null ? "" : cardLast4;
        return issuerPart + "|" + last4Part;
    }
}
