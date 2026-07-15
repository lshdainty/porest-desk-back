package com.porest.desk.dataimport.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.domain.Expense;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseCategoryService;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImportServiceImpl — 분석/저장 오케스트레이션")
class ImportServiceImplTest {

    @Mock private ExpenseService expenseService;
    @Mock private ExpenseCategoryService expenseCategoryService;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @InjectMocks private ImportServiceImpl sut;

    private static final String POREST_CSV =
        "날짜,유형,카테고리,자산,금액,설명\n"
        + "2026-05-28,EXPENSE,식비,체크카드,5700,편의점\n"
        + "2026-05-27,INCOME,급여,토스뱅크,3200000,월급\n";

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "t.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private ExpenseCategoryServiceDto.CategoryInfo categoryInfo(long rowId) {
        return new ExpenseCategoryServiceDto.CategoryInfo(
            rowId, 1L, "cat", "tag", "#9E9E9E", ExpenseType.EXPENSE, 0, null, false, null, null);
    }

    @Test
    @DisplayName("analyze — 자동매핑 제안 + 유효건수 산출, 중복 없음")
    void analyze_suggestsMappingAndCounts() {
        given(expenseRepository.findByDateRange(eq(1L), any(), any())).willReturn(List.of());

        ImportService.AnalyzeResult r = sut.analyze(csv(POREST_CSV), ImportSource.POREST, 1L);

        assertThat(r.totalRows()).isEqualTo(2);
        assertThat(r.validRows()).isEqualTo(2);
        assertThat(r.duplicateCount()).isZero();
        assertThat(r.suggestedMapping())
            .containsEntry(ImportField.DATE, 0)
            .containsEntry(ImportField.TYPE, 1)
            .containsEntry(ImportField.AMOUNT, 4);
        assertThat(r.preview()).hasSize(2);
    }

    @Test
    @DisplayName("analyze — 기존 거래(날짜·금액·설명 동일)를 중복 표시")
    void analyze_marksDuplicates() {
        Expense existing = mock(Expense.class);
        given(existing.getExpenseDate()).willReturn(LocalDateTime.of(2026, 5, 28, 0, 0));
        given(existing.getAmount()).willReturn(5700L);
        given(existing.getDescription()).willReturn("편의점");
        given(expenseRepository.findByDateRange(eq(1L), any(), any())).willReturn(List.of(existing));

        ImportService.AnalyzeResult r = sut.analyze(csv(POREST_CSV), ImportSource.POREST, 1L);

        assertThat(r.duplicateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("execute — 유효행 저장 + 없는 카테고리 자동생성")
    void execute_savesAndAutoCreatesCategory() {
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(assetRepository.findByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));

        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r = sut.execute(csv(POREST_CSV), ImportSource.POREST, mapping, true, true, 1L);

        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.failed()).isZero();
        verify(expenseService, times(2)).createExpense(any());
        verify(expenseCategoryService, times(2)).createCategory(any()); // 식비(지출)·급여(수입)
    }

    @Test
    @DisplayName("execute — dupSkip 시 중복행 건너뛰기")
    void execute_skipsDuplicates() {
        Expense existing = mock(Expense.class);
        given(existing.getExpenseDate()).willReturn(LocalDateTime.of(2026, 5, 28, 0, 0));
        given(existing.getAmount()).willReturn(5700L);
        given(existing.getDescription()).willReturn("편의점");
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of(existing));
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(assetRepository.findByUser(1L)).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));

        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r = sut.execute(csv(POREST_CSV), ImportSource.POREST, mapping, true, true, 1L);

        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.imported()).isEqualTo(1);
        verify(expenseService, times(1)).createExpense(any());
    }

    @Test
    @DisplayName("execute — 날짜/금액 매핑 누락 시 예외")
    void execute_missingRequiredMapping_throws() {
        Map<ImportField, Integer> bad = Map.of(ImportField.AMOUNT, 4); // DATE 없음
        assertThatThrownBy(() -> sut.execute(csv(POREST_CSV), ImportSource.POREST, bad, false, false, 1L))
            .isInstanceOf(InvalidValueException.class);
    }
}
