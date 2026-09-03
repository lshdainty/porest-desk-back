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

        @Test
        @DisplayName("달력에 없는 날짜는 그 달 말일로 당기지 않고 실패시킨다")
        void 없는_날짜는_null() {
            // 기본 해석(SMART)은 2026-02-30 을 2026-02-28 로 끌어당긴다 — 사용자가 쓴 적 없는
            // 날에 거래가 조용히 생기고, 같은 값을 거래 API 로 보내면 400 이라 경로마다 결과가 달랐다.
            assertThat(ValueNormalizer.parseDate("2026-02-30 10:00")).isNull();
            assertThat(ValueNormalizer.parseDate("2026-02-30")).isNull();
            assertThat(ValueNormalizer.parseDate("2026-02-30T10:00")).isNull();
            assertThat(ValueNormalizer.parseDate("2026.02.30")).isNull();
            assertThat(ValueNormalizer.parseDate("2026/02/30 10:00")).isNull();
            assertThat(ValueNormalizer.parseDate("02/30/2026")).isNull();
            assertThat(ValueNormalizer.parseDate("20260230")).isNull();
            assertThat(ValueNormalizer.parseDate("2026-04-31")).isNull();
            assertThat(ValueNormalizer.parseDate("2026-02-29")).isNull(); // 2026 은 윤년이 아니다
        }

        @Test
        @DisplayName("정상 날짜는 STRICT 로 바꿔도 그대로 읽힌다(내보내기 CSV 왕복 포함)")
        void 정상_형식은_계속_읽힌다() {
            // 내보내기는 전 파일을 yyyy-MM-dd HH:mm 으로 쓴다. 여기가 막히면 왕복이 깨진다.
            assertThat(ValueNormalizer.parseDate("2026-05-28 13:20"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 13, 20));
            assertThat(ValueNormalizer.parseDate("2026-05-28 13:20:45"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 13, 20, 45));
            assertThat(ValueNormalizer.parseDate("2026.05.28 09:05"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 9, 5));
            assertThat(ValueNormalizer.parseDate("2024-02-29")) // 2024 는 윤년
                .isEqualTo(LocalDateTime.of(2024, 2, 29, 0, 0));
            assertThat(ValueNormalizer.parseDate("05/28/2026"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 0, 0));
            assertThat(ValueNormalizer.parseDate("20260528"))
                .isEqualTo(LocalDateTime.of(2026, 5, 28, 0, 0));
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
