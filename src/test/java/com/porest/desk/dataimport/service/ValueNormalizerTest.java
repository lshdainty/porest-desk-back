package com.porest.desk.dataimport.service;

import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ValueNormalizer — 날짜/금액/유형 정규화")
class ValueNormalizerTest {

    @Nested
    @DisplayName("parseDate")
    class ParseDate {
        @Test
        void ISO_일시() {
            assertThat(ValueNormalizer.parseDate("2026-05-28T13:20:00"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 13, 20, 0));
        }

        @Test
        void ISO_날짜만_자정() {
            assertThat(ValueNormalizer.parseDate("2026-05-28"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 0, 0));
        }

        @Test
        void 점구분_한국형() {
            assertThat(ValueNormalizer.parseDate("2026.05.28"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 0, 0));
        }

        @Test
        void 슬래시_시분() {
            assertThat(ValueNormalizer.parseDate("2026/05/28 09:05"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 9, 5));
        }

        @Test
        @DisplayName("편한가계부 Excel serial number → 날짜+시각")
        void excel_serial() {
            assertThat(ValueNormalizer.parseDate("46242.69784722223"))
                .isEqualTo(LocalDateTime.of(2026, 8, 8, 16, 44, 54));
        }

        @Test
        void 실패시_null() {
            assertThat(ValueNormalizer.parseDate("헬로")).isNull();
            assertThat(ValueNormalizer.parseDate("")).isNull();
            assertThat(ValueNormalizer.parseDate(null)).isNull();
        }
    }

    @Nested
    @DisplayName("parseAmount")
    class ParseAmount {
        @Test
        void 콤마_통화기호_제거() {
            assertThat(ValueNormalizer.parseAmount("₩1,234,500")).isEqualTo(1_234_500L);
        }

        @Test
        void 음수는_절대값() {
            assertThat(ValueNormalizer.parseAmount("-5700")).isEqualTo(5700L);
        }

        @Test
        void 회계식_괄호_절대값() {
            assertThat(ValueNormalizer.parseAmount("(3,200)")).isEqualTo(3200L);
        }

        @Test
        void 원단위_접미사() {
            assertThat(ValueNormalizer.parseAmount("5700원")).isEqualTo(5700L);
        }

        @Test
        void 실패_또는_0이하_null() {
            assertThat(ValueNormalizer.parseAmount("-")).isNull();
            assertThat(ValueNormalizer.parseAmount("0")).isNull();
            assertThat(ValueNormalizer.parseAmount("abc")).isNull();
            assertThat(ValueNormalizer.parseAmount(null)).isNull();
        }
    }

    @Nested
    @DisplayName("parseType")
    class ParseType {
        @Test
        void 한국어_수입지출() {
            assertThat(ValueNormalizer.parseType("지출")).isEqualTo(ExpenseType.EXPENSE);
            assertThat(ValueNormalizer.parseType("수입")).isEqualTo(ExpenseType.INCOME);
        }

        @Test
        void 영어_income_expense() {
            assertThat(ValueNormalizer.parseType("Expenses")).isEqualTo(ExpenseType.EXPENSE);
            assertThat(ValueNormalizer.parseType("Income")).isEqualTo(ExpenseType.INCOME);
        }

        @Test
        void 입출금() {
            assertThat(ValueNormalizer.parseType("출금")).isEqualTo(ExpenseType.EXPENSE);
            assertThat(ValueNormalizer.parseType("입금")).isEqualTo(ExpenseType.INCOME);
        }

        @Test
        void 이체_및_미상은_null() {
            assertThat(ValueNormalizer.parseType("이체")).isNull();
            assertThat(ValueNormalizer.parseType("transfer")).isNull();
            assertThat(ValueNormalizer.parseType("")).isNull();
            assertThat(ValueNormalizer.parseType(null)).isNull();
        }
    }
}
