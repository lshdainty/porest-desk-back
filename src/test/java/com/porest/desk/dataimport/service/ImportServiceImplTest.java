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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.willReturn;
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

    /** 청크로 넘어간 CreateCommand 들을 순서대로 펼쳐 준다. */
    @SuppressWarnings("unchecked")
    private List<ExpenseServiceDto.CreateCommand> capturedCommands() {
        ArgumentCaptor<List<ExpenseServiceDto.CreateCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(expenseService, atLeastOnce()).createExpensesChunk(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
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
        assertThat(capturedCommands()).hasSize(2);
        verify(expenseCategoryService, times(2)).createCategory(any()); // 식비(지출)·급여(수입)
        // 묻지 않고 만들었으면 무엇을 만들었는지는 알려줘야 한다.
        assertThat(r.createdCategories()).containsExactly("식비", "급여");
        assertThat(r.createdCategoryCount()).isEqualTo(2);
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
        assertThat(capturedCommands()).hasSize(1);
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
        assertThat(capturedCommands()).extracting(ExpenseServiceDto.CreateCommand::categoryRowId)
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

        assertThat(capturedCommands()).singleElement()
            .extracting(ExpenseServiceDto.CreateCommand::categoryRowId).isEqualTo(9L);
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

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.EASYBUDGET, mapping, false, true, 1L);

        // 계층은 미리보기 카테고리 칸과 같은 "대분류 > 소분류" 표기로 알린다.
        assertThat(r.createdCategories()).containsExactly("관리비", "관리비 > 전기비");
        assertThat(r.createdCategoryCount()).isEqualTo(2);

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
        assertThat(r.createdCategories()).containsExactly("식비", "식비 > 아침", "식비 > 미분류");
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
    @DisplayName("execute — 청크가 실패하면 건별 재시도로 문제 행만 가려낸다")
    void retriesRowByRowWhenChunkFails() {
        // 청크째 실패로 끝내면 멀쩡한 행까지 버려져 부분 성공 보장이 깨진다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));
        willThrow(new RuntimeException("청크 실패")).given(expenseService).createExpensesChunk(any());
        // 재시도에서 두 번째 행만 실패
        willReturn(null).willThrow(new RuntimeException("행 실패"))
            .given(expenseService).createExpense(any(), eq(true));

        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r = sut.execute(csv(POREST_CSV), ImportSource.POREST, mapping, false, true, 1L);

        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.failures()).singleElement()
            .extracting(ImportService.Failure::reason).isEqualTo("save");
        verify(expenseService, times(2)).createExpense(any(), eq(true));
    }

    @Test
    @DisplayName("analyze — 거래가 달린 최상위를 부모로 써야 하는 행은 미리 표시한다")
    void flagsRowsWhoseParentAlreadyHasTransactions() {
        // 이전 가져오기가 '관리비' 를 최상위로 만들고 거래를 직접 달아뒀다면,
        // 그 아래 '통신비' 를 만들 수 없다(부모는 거래를 가질 수 없다는 규칙).
        // 실행하고 나서 행마다 실패하면 이유를 알 수 없으니 분석 단계에서 짚어준다.
        ExpenseCategory mgmt = cat(1L, "관리비", null);
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of(mgmt));
        given(expenseRepository.existsByCategory(1L)).willReturn(true);
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,관리비,통신비,통신비,55000,지출\n"
            + "2026-05-29,체크카드,식비,아침,김밥,5000,지출\n";
        var result = sut.analyze(csv(content), ImportSource.EASYBUDGET, 1L);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.validRows()).isEqualTo(1);   // 관리비 행은 유효하지 않다
        assertThat(result.preview()).extracting(StandardRow::error)
            .containsExactly(StandardRow.ERROR_PARENT_HAS_TX, null);
    }

    // ── 실행 전 "새로 만들 카테고리" 예고 ──────────────────────────

    @Test
    @DisplayName("analyze — 새로 만들어질 카테고리를 알리되 실제로 만들지는 않는다")
    void analyze_reportsNewCategoriesWithoutCreatingThem() {
        // 오타('싟비')가 그대로 새 카테고리가 되던 결함 — 이제 실행 전에 목록으로 보여준다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of(cat(1L, "식비", null)));
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,유형,카테고리,자산,금액,설명\n"
            + "2026-05-28,EXPENSE,식비,체크카드,5700,편의점\n"
            + "2026-05-27,EXPENSE,싟비,체크카드,3000,오타\n";

        ImportService.AnalyzeResult r = sut.analyze(csv(content), ImportSource.POREST, 1L);

        assertThat(r.newCategories()).containsExactly("싟비");
        assertThat(r.newCategoryCount()).isEqualTo(1);
        // analyze 는 읽기 전용이다 — 예고하려다 만들어 버리면 결함이 그대로다.
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("analyze — 이미 있는 카테고리만 쓰면 예고할 것이 없다")
    void analyze_reportsNothingWhenAllCategoriesExist() {
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of(cat(1L, "식비", null)));
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,유형,카테고리,자산,금액,설명\n"
            + "2026-05-28,EXPENSE,식비,체크카드,5700,편의점\n";

        ImportService.AnalyzeResult r = sut.analyze(csv(content), ImportSource.POREST, 1L);

        assertThat(r.newCategories()).isEmpty();
        assertThat(r.newCategoryCount()).isZero();
    }

    @Test
    @DisplayName("analyze — 부모/자식 계층을 경로로 알리고 부모는 한 번만 센다")
    void analyze_reportsParentOnceWithChildPaths() {
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,자산,대분류,소분류,내용,금액,유형\n"
            + "2026-05-28,체크카드,여행,기타,기념품,12000,지출\n"
            + "2026-05-29,체크카드,여행,숙박,호텔,50000,지출\n";

        ImportService.AnalyzeResult r = sut.analyze(csv(content), ImportSource.EASYBUDGET, 1L);

        // '여행' 을 두 번 세면 "새 카테고리 4개" 라고 겁을 주게 된다.
        assertThat(r.newCategories()).containsExactly("여행", "여행 > 기타", "여행 > 숙박");
        assertThat(r.newCategoryCount()).isEqualTo(3);
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("analyze — 새 카테고리가 많으면 목록은 자르되 개수는 끝까지 센다")
    void analyze_capsNewCategoryListButKeepsCount() {
        // 카테고리 열을 잘못 매핑하면 행마다 다른 이름이 나온다 — 목록을 다 실으면
        // 응답이 부풀고 화면이 그리지 못한다. 경고의 핵심인 '개수' 는 살려 둔다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        StringBuilder sb = new StringBuilder("날짜,유형,카테고리,자산,금액,설명\n");
        for (int i = 1; i <= 120; i++) {
            sb.append("2026-05-28,EXPENSE,가게").append(i).append(",체크카드,5700,결제\n");
        }

        ImportService.AnalyzeResult r = sut.analyze(csv(sb.toString()), ImportSource.POREST, 1L);

        assertThat(r.newCategories()).hasSize(50);
        assertThat(r.newCategories().get(0)).isEqualTo("가게1");
        assertThat(r.newCategoryCount()).isEqualTo(120);
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("analyze — 실패할 행(존재하지 않는 날짜)의 카테고리는 예고하지 않는다")
    void analyze_ignoresInvalidRowsWhenPreviewingCategories() {
        // 어차피 저장되지 않을 행 때문에 "새 카테고리가 생겨요" 라고 하면 안 된다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,유형,카테고리,자산,금액,설명\n"
            + "2026-02-30,EXPENSE,없는카테고리,체크카드,5700,깨진날짜\n";

        ImportService.AnalyzeResult r = sut.analyze(csv(content), ImportSource.POREST, 1L);

        assertThat(r.validRows()).isZero();
        assertThat(r.newCategories()).isEmpty();
        assertThat(r.newCategoryCount()).isZero();
    }

    @Test
    @DisplayName("execute — 실패 목록은 상한까지만 담는다(개수는 그대로 센다)")
    void execute_capsFailureListButKeepsCount() {
        // 목록이 잘려도 failed 는 전부 세야 컨트롤러가 "잘렸다" 를 판단할 수 있다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        StringBuilder sb = new StringBuilder("날짜,유형,카테고리,자산,금액,설명\n");
        for (int i = 0; i < 60; i++) {
            sb.append("2026-05-28,EXPENSE,식비,체크카드,abc,금액오류\n");
        }
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r =
            sut.execute(csv(sb.toString()), ImportSource.POREST, mapping, false, true, 1L);

        assertThat(r.failed()).isEqualTo(60);
        assertThat(r.failures()).hasSize(50);
        assertThat(r.failures()).extracting(ImportService.Failure::reason).containsOnly("amount");
    }

    // ── 없는 날짜 ───────────────────────────────────────────

    @Test
    @DisplayName("execute — 달력에 없는 날짜(2026-02-30 10:00)는 저장하지 않고 date 실패로 돌린다")
    void execute_rejectsNonExistentDate() {
        // 예전엔 조용히 2026-02-28 로 당겨 저장했다. 같은 값을 거래 API 로 보내면 400 이라
        // 같은 입력이 경로에 따라 다른 결과였다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(assetRepository.findByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());

        String content = "날짜,유형,카테고리,자산,금액,설명\n"
            + "2026-02-30 10:00,EXPENSE,식비,체크카드,5700,없는날짜\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.POREST, mapping, false, true, 1L);

        assertThat(r.imported()).isZero();
        assertThat(r.failed()).isEqualTo(1);
        assertThat(r.failures()).singleElement()
            .extracting(ImportService.Failure::reason).isEqualTo("date");
        verify(expenseService, never()).createExpensesChunk(any());
        verify(expenseService, never()).createExpense(any(), anyBoolean());
        // 저장도 안 될 행 때문에 카테고리가 생기면 안 된다.
        verify(expenseCategoryService, never()).createCategory(any());
    }

    @Test
    @DisplayName("execute — 내보내기 CSV 형식(yyyy-MM-dd HH:mm)은 그대로 다시 읽힌다")
    void execute_acceptsExportedTimestampFormat() {
        // 내보내기는 전 파일을 이 형식으로 쓴다. 왕복(내보내기 → 다시 가져오기)이 여기서 깨지면 안 된다.
        given(expenseCategoryRepository.findAllByUser(1L)).willReturn(List.of());
        given(assetRepository.findByUser(1L)).willReturn(List.of());
        given(expenseRepository.findByDateRange(any(), any(), any())).willReturn(List.of());
        given(expenseCategoryService.createCategory(any())).willReturn(categoryInfo(10L));

        String content = "날짜,유형,카테고리,자산,금액,설명\n"
            + "2026-05-28 13:20,EXPENSE,식비,체크카드,5700,편의점\n";
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(
            ImportSource.POREST, List.of("날짜", "유형", "카테고리", "자산", "금액", "설명"));

        ImportService.ExecuteResult r =
            sut.execute(csv(content), ImportSource.POREST, mapping, false, true, 1L);

        assertThat(r.imported()).isEqualTo(1);
        assertThat(r.failed()).isZero();
        assertThat(capturedCommands()).singleElement()
            .extracting(ExpenseServiceDto.CreateCommand::expenseDate)
            .isEqualTo(LocalDateTime.of(2026, 5, 28, 13, 20));
    }

    @Test
    @DisplayName("execute — 날짜/금액 매핑 누락 시 예외")
    void execute_missingRequiredMapping_throws() {
        Map<ImportField, Integer> bad = Map.of(ImportField.AMOUNT, 4); // DATE 없음
        assertThatThrownBy(() -> sut.execute(csv(POREST_CSV), ImportSource.POREST, bad, false, false, 1L))
            .isInstanceOf(InvalidValueException.class);
    }
}
