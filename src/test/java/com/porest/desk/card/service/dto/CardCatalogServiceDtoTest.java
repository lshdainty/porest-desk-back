package com.porest.desk.card.service.dto;

import com.porest.desk.card.domain.CardCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 카드 카탈로그 서비스 DTO 변환 단위 테스트.
 *
 * <p>연회비는 "0원인 카드"와 "정보가 없는 카드"를 구분해야 한다.
 * {@code annualFeeAmount} 가 NOT NULL DEFAULT 0 이라 미수집분도 0 으로 내려가는데,
 * 화면이 둘을 같게 취급하면 연회비를 안 받는 카드처럼 보인다.
 * 2026-08 실측으로 9,466 장 중 5,399 장이 미수집 상태였다(카드사 공시 PDF 는 연회비를 안 준다).
 */
class CardCatalogServiceDtoTest {

    private static CardCatalog cardWith(Integer amount, String label) {
        CardCatalog c = mock(CardCatalog.class);
        given(c.getAnnualFeeAmount()).willReturn(amount);
        given(c.getAnnualFeeLabel()).willReturn(label);
        return c;
    }

    @Nested
    @DisplayName("AnnualFeeInfo.from")
    class AnnualFeeInfoFrom {

        @Test
        @DisplayName("금액 0 · 라벨 없음 → null (연회비 정보가 없는 카드)")
        void nullWhenNoAmountAndNoLabel() {
            assertThat(CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(0, null))).isNull();
        }

        @Test
        @DisplayName("금액 0 · 라벨 빈 문자열 → null")
        void nullWhenLabelIsEmpty() {
            assertThat(CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(0, ""))).isNull();
        }

        @Test
        @DisplayName("금액 0 · 라벨 공백뿐 → null")
        void nullWhenLabelIsBlank() {
            assertThat(CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(0, "   "))).isNull();
        }

        @Test
        @DisplayName("금액 null · 라벨 없음 → null")
        void nullWhenAmountIsNull() {
            assertThat(CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(null, null))).isNull();
        }

        @Test
        @DisplayName("금액 0 이지만 라벨이 있으면 → 정보가 있는 것이므로 null 이 아니다")
        void keptWhenLabelExistsEvenIfAmountIsZero() {
            CardCatalogServiceDto.AnnualFeeInfo info =
                CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(0, "면제"));

            assertThat(info).isNotNull();
            assertThat(info.amount()).isZero();
            assertThat(info.label()).isEqualTo("면제");
        }

        @Test
        @DisplayName("금액이 있으면 라벨이 없어도 → null 이 아니다")
        void keptWhenAmountExists() {
            CardCatalogServiceDto.AnnualFeeInfo info =
                CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(15000, null));

            assertThat(info).isNotNull();
            assertThat(info.amount()).isEqualTo(15000);
            assertThat(info.label()).isNull();
        }

        @Test
        @DisplayName("금액과 라벨이 모두 있으면 → 둘 다 그대로 내려간다")
        void keptWhenBothExist() {
            CardCatalogServiceDto.AnnualFeeInfo info =
                CardCatalogServiceDto.AnnualFeeInfo.from(cardWith(20000, "국내외겸용 20,000원"));

            assertThat(info).isNotNull();
            assertThat(info.amount()).isEqualTo(20000);
            assertThat(info.label()).isEqualTo("국내외겸용 20,000원");
        }
    }
}
