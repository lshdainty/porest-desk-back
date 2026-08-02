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
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyBoolean;
import com.porest.desk.expense.domain.ExpenseCategory;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.asset.service.AssetBalanceHistoryService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ImportServiceImpl — 분석/저장 오케스트레이션")
class ImportServiceImplTest {

    @Mock private ExpenseService expenseService;
    @Mock private ExpenseCategoryService expenseCategoryService;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;
    @InjectMocks private ImportServiceImpl sut;

    private static final String POREST_CSV =
        "날짜,유형,카테고리,자산,금액,설명\n"
        + "2026-05-28,EXPENSE,식비,체크카드,5700,편의점\n"
        + "2026-05-27,INCOME,급여,토스뱅크,3200000,월급\n";

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "t.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    /** 부모/자식 카테고리 픽스처 — rowId 는 리플렉션으로 심는다(엔티티에 setter 없음). */
    private ExpenseCategory cat(long rowId, String name, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(
            null, name, "tag", "#9E9E9E", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
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
        verify(expenseService, times(2)).createExpense(any(), anyBoolean());
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
        verify(expenseService, times(1)).createExpense(any(), anyBoolean());
    }

    // ── 편한가계부 대분류/소분류 → 부모/자식 계층 매칭 ──────────────

    private static final String EASYBUDGET_CSV =
        "날짜,자산,대분류,소분류,내용,금액,유형\n"
        + "2026-05-28,체크카드,문화생활,기타,어플결제,5700,지출\n"
        + "2026-05-27,체크카드,여행,기타,기념품,12000,지출\n";

    @Test
    @DisplayName("execute — 대분류/소분류로 부모 아래 자식에 귀속한다(이름이 같아도 부모로 구분)")
    void resolvesCategoryByParentAndChild() {
        ExpenseCategory culture = cat(1L, "문화생활", null);
        ExpenseCategory cultureEtc = cat(2L, "기타", culture);
        ExpenseCategory travel = cat(3L, "여행", null);
        ExpenseCategory travelEtc = cat(4L, "기타", travel);
        given(expenseCategoryRepository.findAllByUser(1L))
            .willReturn(List.of(culture, cultureEtc, travel, travelEtc));
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        ImportService.ExecuteResult r =
            sut.execute(csv(EASYBUDGET_CSV), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        assertThat(r.imported()).isEqualTo(2);
        // 같은 "기타" 라도 대분류가 다르면 다른 카테고리로 들어가야 한다.
        ArgumentCaptor<ExpenseServiceDto.CreateCommand> captor =
            ArgumentCaptor.forClass(ExpenseServiceDto.CreateCommand.class);
        verify(expenseService, times(2)).createExpense(captor.capture(), eq(true));
        assertThat(captor.getAllValues()).extracting(ExpenseServiceDto.CreateCommand::categoryRowId)
            .containsExactly(2L, 4L);
        // 이미 있는 카테고리라 새로 만들지 않는다.
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("execute — 소분류가 비면 대분류 이름의 최상위 카테고리에 귀속한다")
    void resolvesTopLevelWhenSubcategoryBlank() {
        ExpenseCategory date = cat(9L, "데이트", null);
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of(date));
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,데이트,,영화,20000,지출\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        ArgumentCaptor<ExpenseServiceDto.CreateCommand> captor =
            ArgumentCaptor.forClass(ExpenseServiceDto.CreateCommand.class);
        verify(expenseService).createExpense(captor.capture(), eq(true));
        assertThat(captor.getValue().categoryRowId()).isEqualTo(9L);
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("execute — autoCat 이면 없는 부모·자식을 계층 그대로 만든다")
    void autoCreatesParentAndChild() {
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any()))
            .willReturn(categoryInfo(100L), categoryInfo(101L));

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,관리비,전기비,5월분,30000,지출\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        ArgumentCaptor<ExpenseCategoryServiceDto.CreateCommand> captor =
            ArgumentCaptor.forClass(ExpenseCategoryServiceDto.CreateCommand.class);
        verify(expenseCategoryService, times(2)).createCategory(captor.capture());
        // 부모(관리비) 먼저, 그 다음 자식(전기비)이 부모를 가리켜야 한다.
        assertThat(captor.getAllValues().get(0).categoryName()).isEqualTo("관리비");
        assertThat(captor.getAllValues().get(0).parentRowId()).isNull();
        assertThat(captor.getAllValues().get(1).categoryName()).isEqualTo("전기비");
        assertThat(captor.getAllValues().get(1).parentRowId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("execute — 같은 대분류에 소분류 있는 행과 빈 행이 섞여도 중복 생성하지 않는다")
    void doesNotDuplicateParentWhenSubcategoryBlank() {
        // 편한가계부는 소분류 빈칸이 잦다. 부모를 만든 뒤 같은 대분류의 빈칸 행이 오면
        // 예전엔 같은 이름의 최상위를 또 만들려다 EXP_019(이름 중복)로 행이 통째로 실패했다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any()))
            .willReturn(categoryInfo(100L), categoryInfo(101L), categoryInfo(102L));

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,식비,아침,김밥,5000,지출\n"
            + "2026-05-29,체크카드,식비,,점심,8000,지출\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        assertThat(r.imported()).isEqualTo(2);
        assertThat(r.failed()).isZero();

        // 만들어진 카테고리: 식비(부모) → 아침(자식) → 미분류(자식). 식비를 두 번 만들지 않는다.
        ArgumentCaptor<ExpenseCategoryServiceDto.CreateCommand> captor =
            ArgumentCaptor.forClass(ExpenseCategoryServiceDto.CreateCommand.class);
        verify(expenseCategoryService, times(3)).createCategory(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExpenseCategoryServiceDto.CreateCommand::categoryName)
            .containsExactly("식비", "아침", "미분류");
        assertThat(captor.getAllValues().get(2).parentRowId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("execute — 잔액 재산정은 행마다가 아니라 자산당 한 번만 한다")
    void recomputesOncePerAsset() {
        // 행마다 재산정하면 그 자산의 전체 이력을 매번 다시 읽어 O(N²) 이 된다.
        // 1만 행이면 수천만 건 읽기라 요청이 사실상 끝나지 않는다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));
        given(assetRepository.findByUser(1L)).willReturn(List.of());

        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        sut.execute(csv(POREST_CSV), ImportSource.POREST, mapping, false, true, 1L);

        // 자산 이름이 매칭되지 않아 assetRowId 가 없으므로 재산정 대상도 비어 있어야 한다.
        verify(balanceHistoryService, times(1)).recomputeAssets(any());
        // 건별 재산정은 createExpense(.., bulk=true) 안에서 억제된다.
        verify(expenseService, times(2)).createExpense(any(), eq(true));
    }

    @Test
    @DisplayName("execute — 이체 행은 실패가 아니라 건너뜀으로 집계한다")
    void countsTransferRowsAsSkipped() {
        // 편한가계부 파일엔 이체 행이 섞여 있다. 우리는 가계부 거래로 다루지 않으므로 넣지 않되,
        // 실패로 집계하면 진짜 오류(카테고리·금액 문제)와 구분이 안 된다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,식비,아침,김밥,5000,지출\n"
            + "2026-05-29,체크카드,,,계좌이동,100000,이체\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.skipped()).isEqualTo(1);
        assertThat(r.failed()).isZero();
        assertThat(r.failures()).isEmpty();
    }

    @Test
    @DisplayName("execute — 알 수 없는 유형은 계속 실패로 집계한다(이체와 구분)")
    void unknownTypeStillFails() {
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-29,체크카드,식비,아침,김밥,5000,알수없음\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.EASYBUDGET, List.of("날짜", "자산", "대분류", "소분류", "내용", "금액", "유형"));

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.skipped()).isZero();
    }

    @Test
    @DisplayName("execute — 날짜/금액 매핑 누락 시 예외")
    void execute_missingRequiredMapping_throws() {
        Map<ImportField, Integer> bad = Map.of(ImportField.AMOUNT, 4); // DATE 없음
        assertThatThrownBy(() -> sut.execute(csv(POREST_CSV), ImportSource.POREST, bad, false, false, 1L))
            .isInstanceOf(InvalidValueException.class);
    }
}
