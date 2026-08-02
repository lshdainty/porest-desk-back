package com.porest.desk.dataimport.service;

import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImportColumnMapper — 프리셋 자동매핑 + 행 매핑")
class ImportColumnMapperTest {

    @Test
    @DisplayName("편한가계부는 위치 고정(A~G) 매핑 — 헤더 중복('자산')에도 안전")
    void suggest_easybudget_positional() {
        var headers = List.of("기간", "자산", "분류", "소분류", "내용", "KRW", "수입/지출", "추가입력", "금액", "화폐", "자산");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.EASYBUDGET, headers);
        assertThat(m.get(ImportField.DATE)).isZero();
        assertThat(m.get(ImportField.ASSET)).isEqualTo(1);
        assertThat(m.get(ImportField.CATEGORY)).isEqualTo(2);
        assertThat(m.get(ImportField.SUBCATEGORY)).isEqualTo(3);
        assertThat(m.get(ImportField.MEMO)).isEqualTo(4);
        assertThat(m.get(ImportField.AMOUNT)).isEqualTo(5);
        assertThat(m.get(ImportField.TYPE)).isEqualTo(6);
    }

    @Test
    @DisplayName("Porest 헤더 동의어 매핑 — 자산/설명 우선, 결제수단/거래처는 중복 제외")
    void suggest_porest_byName() {
        var headers = List.of("날짜", "유형", "카테고리", "자산", "금액", "설명", "거래처", "결제수단");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.POREST, headers);
        assertThat(m.get(ImportField.DATE)).isZero();
        assertThat(m.get(ImportField.TYPE)).isEqualTo(1);
        assertThat(m.get(ImportField.CATEGORY)).isEqualTo(2);
        assertThat(m.get(ImportField.ASSET)).isEqualTo(3);
        assertThat(m.get(ImportField.AMOUNT)).isEqualTo(4);
        assertThat(m.get(ImportField.MEMO)).isEqualTo(5);
    }

    @Test
    @DisplayName("토스 은행내역 — 출금액/입금액 분리 매핑")
    void suggest_toss_outIn() {
        var headers = List.of("거래일시", "적요", "출금액", "입금액", "거래후잔액");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.TOSS, headers);
        assertThat(m.get(ImportField.DATE)).isZero();
        assertThat(m.get(ImportField.MEMO)).isEqualTo(1);
        assertThat(m.get(ImportField.AMOUNT_OUT)).isEqualTo(2);
        assertThat(m.get(ImportField.AMOUNT_IN)).isEqualTo(3);
        assertThat(m).doesNotContainKey(ImportField.AMOUNT);
    }

    @Test
    @DisplayName("mapRow — 편한가계부 지출 행")
    void mapRow_easybudget_expense() {
        var headers = List.of("기간", "자산", "분류", "소분류", "내용", "KRW", "수입/지출");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.EASYBUDGET, headers);
        var row = List.of("2026-05-28", "체크카드", "식비", "점심", "편의점", "5700", "지출");

        StandardRow s = ImportColumnMapper.mapRow(m, row, 1);

        assertThat(s.valid()).isTrue();
        assertThat(s.type()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(s.amount()).isEqualTo(5700L);
        assertThat(s.category()).isEqualTo("식비");
        assertThat(s.subcategory()).isEqualTo("점심");
        assertThat(s.asset()).isEqualTo("체크카드");
        assertThat(s.memo()).isEqualTo("편의점");
    }

    @Test
    @DisplayName("mapRow — 토스 출금/입금에서 유형·금액 파생")
    void mapRow_toss_derivesType() {
        var headers = List.of("거래일시", "적요", "출금액", "입금액");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.TOSS, headers);

        StandardRow expense = ImportColumnMapper.mapRow(m, List.of("2026-05-28", "편의점", "5000", ""), 1);
        assertThat(expense.type()).isEqualTo(ExpenseType.EXPENSE);
        assertThat(expense.amount()).isEqualTo(5000L);

        StandardRow income = ImportColumnMapper.mapRow(m, List.of("2026-05-27", "월급", "", "3200000"), 2);
        assertThat(income.type()).isEqualTo(ExpenseType.INCOME);
        assertThat(income.amount()).isEqualTo(3_200_000L);
    }

    @Test
    @DisplayName("mapRow — 필수값 누락은 error 코드")
    void mapRow_invalid() {
        var headers = List.of("기간", "자산", "분류", "소분류", "내용", "KRW", "수입/지출");
        Map<ImportField, Integer> m = ImportColumnMapper.suggest(ImportSource.EASYBUDGET, headers);

        StandardRow noDate = ImportColumnMapper.mapRow(m, List.of("", "체크카드", "식비", "점심", "편의점", "5700", "지출"), 1);
        assertThat(noDate.valid()).isFalse();
        assertThat(noDate.error()).isEqualTo("date");

        StandardRow noAmount = ImportColumnMapper.mapRow(m, List.of("2026-05-28", "체크카드", "식비", "점심", "편의점", "", "지출"), 2);
        assertThat(noAmount.error()).isEqualTo("amount");
    }

    // ── 플랫폼별 전용 사양 ────────────────────────────────────

    @Test
    @DisplayName("POREST — 우리 내보내기 헤더를 그대로 매핑(거래처·결제수단 포함)")
    void porestSpec() {
        var m = ImportColumnMapper.suggest(ImportSource.POREST,
            List.of("날짜", "유형", "카테고리", "자산", "금액", "설명", "거래처", "결제수단"));

        assertThat(m).containsEntry(ImportField.DATE, 0)
            .containsEntry(ImportField.TYPE, 1)
            .containsEntry(ImportField.CATEGORY, 2)
            .containsEntry(ImportField.ASSET, 3)
            .containsEntry(ImportField.AMOUNT, 4)
            .containsEntry(ImportField.MEMO, 5)
            .containsEntry(ImportField.MERCHANT, 6)
            .containsEntry(ImportField.PAYMENT_METHOD, 7);
    }

    @Test
    @DisplayName("BANKSALAD — 날짜/시간 분리와 대분류·소분류를 각각 잡는다")
    void banksaladSpec() {
        var m = ImportColumnMapper.suggest(ImportSource.BANKSALAD,
            List.of("날짜", "시간", "타입", "대분류", "소분류", "내용", "금액", "화폐", "결제수단", "메모"));

        assertThat(m).containsEntry(ImportField.DATE, 0)
            .containsEntry(ImportField.TIME, 1)
            .containsEntry(ImportField.TYPE, 2)
            .containsEntry(ImportField.CATEGORY, 3)
            .containsEntry(ImportField.SUBCATEGORY, 4)
            .containsEntry(ImportField.MERCHANT, 5)
            .containsEntry(ImportField.AMOUNT, 6)
            .containsEntry(ImportField.ASSET, 8)
            .containsEntry(ImportField.MEMO, 9);
    }

    @Test
    @DisplayName("TOSS — 출금액/입금액 분리를 우선 잡고 단일 금액으로 오인하지 않는다")
    void tossSpec() {
        var m = ImportColumnMapper.suggest(ImportSource.TOSS,
            List.of("거래일시", "적요", "출금액", "입금액", "거래후잔액", "거래점"));

        assertThat(m).containsEntry(ImportField.DATE, 0)
            .containsEntry(ImportField.AMOUNT_OUT, 2)
            .containsEntry(ImportField.AMOUNT_IN, 3);
        assertThat(m).doesNotContainKey(ImportField.AMOUNT);
    }

    @Test
    @DisplayName("소스 사양에 없는 열은 동의어 사전으로 보완한다")
    void fallsBackToSynonyms() {
        // 뱅크샐러드인데 '메모' 대신 '비고' 로 나간 변형 — 사양이 못 잡으면 동의어가 받는다.
        var m = ImportColumnMapper.suggest(ImportSource.BANKSALAD,
            List.of("날짜", "타입", "대분류", "금액", "비고"));

        assertThat(m).containsEntry(ImportField.DATE, 0)
            .containsEntry(ImportField.TYPE, 1)
            .containsEntry(ImportField.CATEGORY, 2)
            .containsEntry(ImportField.AMOUNT, 3)
            .containsEntry(ImportField.MEMO, 4);
    }
}
