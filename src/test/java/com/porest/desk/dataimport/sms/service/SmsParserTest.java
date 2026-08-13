package com.porest.desk.dataimport.sms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카드사별 결제 문자 픽스처 회귀 테스트.
 *
 * <p>포맷은 카드사가 언제든 바꾼다. 실제 문자를 그대로 박아 두고 값 하나하나를 못 박아야
 * 파서를 고칠 때 다른 카드사가 깨지는 걸 즉시 안다.
 *
 * <p>연도는 문자에 없으므로 기준일을 고정 주입한다 — 실제 시계로 돌리면
 * 연말·연초에만 실패하는 테스트가 된다.
 */
class SmsParserTest {

    /** 기준일 고정 — 2026-08-14. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Nested
    @DisplayName("카드사별 승인 문자")
    class Issuers {

        @Test
        @DisplayName("KB국민 — 카드번호가 카드사명에 붙는 포맷")
        void kb() {
            String sms = """
                [Web발신]
                KB국민카드1234승인
                홍*동
                5,500원 일시불
                08/13 13:22
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.KB);
            assertThat(p.cardLast4()).isEqualTo("1234");
            assertThat(p.amount()).isEqualTo(5500L);
            assertThat(p.installmentMonths()).isNull();
            assertThat(p.occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 13, 13, 22));
            assertThat(p.merchant()).isEqualTo("스타벅스강남");
            assertThat(p.cancel()).isFalse();
            assertThat(p.confidence()).isEqualTo(SmsConfidence.HIGH);
        }

        @Test
        @DisplayName("신한 — 카드번호가 괄호에 들어가는 포맷")
        void shinhan() {
            String sms = """
                [Web발신]
                신한카드(1234)승인
                홍*동
                12,000원 일시불
                08/13 19:05
                김밥천국""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.SHINHAN);
            assertThat(p.cardLast4()).isEqualTo("1234");
            assertThat(p.amount()).isEqualTo(12_000L);
            assertThat(p.merchant()).isEqualTo("김밥천국");
        }

        @Test
        @DisplayName("삼성 — 뒤에 붙는 '누적' 금액을 결제액으로 읽지 않는다")
        void samsung() {
            String sms = """
                [Web발신]
                삼성카드 승인 홍*동
                33,000원 일시불
                08/13 20:11
                이마트성수
                누적 1,234,567원""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.SAMSUNG);
            assertThat(p.amount()).isEqualTo(33_000L);
            assertThat(p.merchant()).isEqualTo("이마트성수");
        }

        @Test
        @DisplayName("현대 — 이름에 '님'이 붙고 할부가 섞인 포맷")
        void hyundai() {
            String sms = """
                [Web발신]
                현대카드 승인
                홍*동님 7,900원 3개월
                08/13 09:30
                쿠팡""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.HYUNDAI);
            assertThat(p.amount()).isEqualTo(7_900L);
            assertThat(p.installmentMonths()).isEqualTo(3);
            assertThat(p.merchant()).isEqualTo("쿠팡");
        }

        @Test
        @DisplayName("롯데 — 금액이 승인보다 앞에 오는 포맷")
        void lotte() {
            String sms = """
                [Web발신]
                롯데카드 4,500원 일시불 승인
                홍*동
                08/13 12:00
                GS25강남점""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.LOTTE);
            assertThat(p.amount()).isEqualTo(4_500L);
            assertThat(p.merchant()).isEqualTo("GS25강남점");
        }

        @Test
        @DisplayName("체크카드 — 할부 표기가 아예 없다")
        void checkCard() {
            String sms = """
                [Web발신]
                신한체크카드(5678)승인
                홍*동
                3,000원
                08/13 08:15
                투썸플레이스""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.SHINHAN);
            assertThat(p.cardLast4()).isEqualTo("5678");
            assertThat(p.amount()).isEqualTo(3_000L);
            assertThat(p.installmentMonths()).isNull();
            assertThat(p.merchant()).isEqualTo("투썸플레이스");
        }
    }

    @Nested
    @DisplayName("변형")
    class Variants {

        @Test
        @DisplayName("한 줄로 붙어 온 문자 — 줄바꿈 없이도 필드를 뽑는다")
        void singleLine() {
            String sms = "[Web발신] KB국민카드1234승인 홍*동 5,500원 일시불 08/13 13:22 스타벅스강남";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.amount()).isEqualTo(5_500L);
            assertThat(p.occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 13, 13, 22));
            assertThat(p.merchant()).isEqualTo("스타벅스강남");
        }

        @Test
        @DisplayName("Web발신 접두가 없어도 동일하게 읽는다")
        void withoutWebPrefix() {
            String sms = """
                KB국민카드1234승인
                5,500원 일시불
                08/13 13:22
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.amount()).isEqualTo(5_500L);
            assertThat(p.merchant()).isEqualTo("스타벅스강남");
        }

        @Test
        @DisplayName("카드번호가 마스킹되면 끝자리는 비우고 카드사만 남긴다")
        void maskedCardNumber() {
            String sms = "[Web발신] KB국민카드1*3*승인 홍*동 5,500원 일시불 08/13 13:22 스타벅스강남";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.cardLast4()).isNull();
            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.KB);
            assertThat(p.cardHint()).isEqualTo("KB국민카드|");
        }

        @Test
        @DisplayName("승인취소 — 취소로 판정한다(승인 글자가 함께 있어도)")
        void cancel() {
            String sms = """
                [Web발신]
                KB국민카드1234승인취소
                홍*동
                5,500원
                08/13 14:00
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.cancel()).isTrue();
            assertThat(p.amount()).isEqualTo(5_500L);
        }

        @Test
        @DisplayName("해외 결제 — 원 통화를 함께 담고 신뢰도를 낮춘다")
        void foreignCurrency() {
            String sms = """
                [Web발신]
                KB국민카드1234승인
                홍*동
                USD 12.34 (16,500원)
                08/13 03:22
                AMAZON.COM""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.amount()).isEqualTo(16_500L);
            assertThat(p.originalCurrency()).isEqualTo("USD");
            assertThat(p.originalAmount()).isEqualByComparingTo(new BigDecimal("12.34"));
            assertThat(p.confidence()).isEqualTo(SmsConfidence.LOW);
        }

        @Test
        @DisplayName("연말 문자를 새해에 읽으면 작년으로 내린다")
        void yearRollback() {
            String sms = """
                KB국민카드1234승인
                5,500원 일시불
                12/30 23:50
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, LocalDate.of(2026, 1, 2));

            assertThat(p.occurredAt()).isEqualTo(LocalDateTime.of(2025, 12, 30, 23, 50));
        }

        @Test
        @DisplayName("가맹점 뒤에 잔액 안내가 붙어도 가맹점만 남긴다")
        void trailingBalance() {
            String sms = """
                신한체크카드(5678)승인
                3,000원
                08/13 08:15
                투썸플레이스 잔액 120,000원""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.merchant()).isEqualTo("투썸플레이스");
            assertThat(p.amount()).isEqualTo(3_000L);
        }

        @Test
        @DisplayName("일시가 없으면 비우고 신뢰도를 내린다 — 나머지는 살린다")
        void withoutDateTime() {
            String sms = """
                KB국민카드1234승인
                5,500원 일시불
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.occurredAt()).isNull();
            assertThat(p.merchant()).isEqualTo("스타벅스강남");
            assertThat(p.confidence()).isEqualTo(SmsConfidence.MEDIUM);
        }

        @Test
        @DisplayName("모르는 카드사 — 파싱은 되지만 신뢰도가 낮다")
        void unknownIssuer() {
            String sms = """
                우체국체크카드 승인
                5,500원 일시불
                08/13 13:22
                스타벅스강남""";

            SmsParsed p = SmsParser.parse(sms, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.issuer()).isNull();
            assertThat(p.confidence()).isEqualTo(SmsConfidence.LOW);
            assertThat(p.amount()).isEqualTo(5_500L);
        }
    }

    @Nested
    @DisplayName("결제 문자가 아닌 입력")
    class NotPayment {

        @Test
        @DisplayName("아무 텍스트는 걸러낸다 — 클립보드가 통째로 서버로 가지 않도록")
        void plainText() {
            assertThat(SmsParser.looksLikePayment("오늘 저녁에 만나자")).isFalse();
            assertThat(SmsParser.parse("오늘 저녁에 만나자", TODAY).matched()).isFalse();
        }

        @Test
        @DisplayName("금액만 있고 결제 키워드가 없으면 결제 문자가 아니다")
        void amountWithoutKeyword() {
            assertThat(SmsParser.looksLikePayment("회비 30,000원 입금 부탁드립니다")).isFalse();
        }

        @Test
        @DisplayName("빈 입력·null 은 조용히 걸러낸다")
        void blank() {
            assertThat(SmsParser.looksLikePayment(null)).isFalse();
            assertThat(SmsParser.looksLikePayment("")).isFalse();
            assertThat(SmsParser.parse("", TODAY).matched()).isFalse();
        }
    }
}
