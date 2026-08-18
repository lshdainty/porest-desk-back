package com.porest.desk.dataimport.sms.service.parser;

import com.porest.desk.dataimport.sms.service.SmsCardIssuer;
import com.porest.desk.dataimport.sms.service.SmsParsed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소스별 파서 라우팅 — 어느 파서가 맡느냐로 판정이 갈리는 지점만 본다.
 *
 * <p>필드 추출 자체는 {@code SmsParserTest} 가 픽스처로 지킨다.
 */
class PaymentTextParsersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Nested
    @DisplayName("은행 앱 알림 — 결제만 받고 이체는 거른다")
    class BankRouting {

        @Test
        @DisplayName("계좌이체 출금은 결제가 아니다 — 지출로 둔갑하면 장부가 틀어진다")
        void rejectsTransfer() {
            // "출금" 은 일반 게이트의 결제 키워드라 그냥 두면 통과한다.
            // 이체는 자산 사이의 이동이지 쓴 돈이 아니므로 은행 알림에서는 걸러야 한다.
            String text = """
                토스
                100,000원 출금
                홍길동님께 이체""";

            assertThat(PaymentTextParsers.looksLikePayment(text)).isFalse();
            assertThat(PaymentTextParsers.parse(text, TODAY).matched()).isFalse();
        }

        @Test
        @DisplayName("입금 알림도 거른다")
        void rejectsDeposit() {
            String text = """
                카카오뱅크
                500,000원 입금
                급여""";

            assertThat(PaymentTextParsers.looksLikePayment(text)).isFalse();
        }

        @Test
        @DisplayName("체크카드 결제는 받는다 — 카드 신호가 있다")
        void acceptsCardPayment() {
            String text = """
                케이뱅크
                승인 4,000원
                씨유(CU) 샤로수길점
                카드(6678) | 08/14 19:09
                출금가능액 19,510원""";

            SmsParsed p = PaymentTextParsers.parse(text, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.amount()).isEqualTo(4_000L);
            assertThat(p.merchant()).isEqualTo("씨유(CU) 샤로수길점");
            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.KBANK);
        }

        @Test
        @DisplayName("은행 앱의 결제 알림이라도 '이체' 가 섞였으면 버린다")
        void rejectsWhenTransferWordPresent() {
            String text = """
                케이뱅크
                승인 4,000원
                자동이체 처리 안내""";

            assertThat(PaymentTextParsers.looksLikePayment(text)).isFalse();
        }
    }

    @Nested
    @DisplayName("카드사 문자 — 은행 게이트에 걸리지 않는다")
    class CardRouting {

        @Test
        @DisplayName("시중은행 이름이 붙은 카드 문자도 일반 게이트로 간다")
        void retailBankCardStillParses() {
            // "국민은행" 은 카드사(KB) 별칭이라 은행 앱 파서로 라우팅되지 않아야 한다.
            // 잘못 라우팅되면 멀쩡한 결제가 엄격 게이트에 걸린다.
            String text = """
                [Web발신]
                국민은행 체크카드 승인
                12,000원
                08/13 19:05
                김밥천국""";

            SmsParsed p = PaymentTextParsers.parse(text, TODAY);

            assertThat(p.matched()).isTrue();
            assertThat(p.issuer()).isEqualTo(SmsCardIssuer.KB);
            assertThat(p.merchant()).isEqualTo("김밥천국");
        }

        @Test
        @DisplayName("일반 카드 문자는 '출금' 만 있어도 통과한다(느슨한 게이트)")
        void looseGateForCards() {
            String text = """
                신한카드(1234)
                3,000원 출금
                08/13 08:15
                투썸플레이스""";

            assertThat(PaymentTextParsers.looksLikePayment(text)).isTrue();
        }
    }

    @Nested
    @DisplayName("파서 목록")
    class Registry {

        @Test
        @DisplayName("결제가 아닌 텍스트는 어느 파서도 맡지 않는다")
        void noParserForPlainText() {
            assertThat(PaymentTextParsers.looksLikePayment("오늘 저녁에 만나자")).isFalse();
            assertThat(PaymentTextParsers.looksLikePayment(null)).isFalse();
            assertThat(PaymentTextParsers.looksLikePayment("")).isFalse();
        }

        @Test
        @DisplayName("은행 파서가 일반 파서보다 먼저 본다")
        void bankParserHasHigherPriority() {
            assertThat(new BankAppPaymentParser().priority())
                .isLessThan(new SmsPaymentParser().priority());
        }
    }
}
