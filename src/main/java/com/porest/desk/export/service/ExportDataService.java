package com.porest.desk.export.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.calendar.repository.CalendarEventRepository;
import com.porest.desk.calendar.service.dto.CalendarEventServiceDto;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.export.repository.ExportCountRepository;
import com.porest.desk.export.type.ExportType;
import com.porest.desk.memo.repository.MemoRepository;
import com.porest.desk.memo.service.dto.MemoServiceDto;
import com.porest.desk.todo.repository.TodoRepository;
import com.porest.desk.todo.service.dto.TodoServiceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 각 데이터 종류를 내보내기 표({@link ExportTable})로 변환.
 *
 * <p>기간 적용 규칙(타입별 분기): 거래=expenseDate, 예산=budgetYear/Month 교집합,
 * 캘린더=start/endDate, 할일=dueDate. 자산·카테고리·메모는 기간 무관 전체.
 *
 * <p>FK 는 이름 우선(categoryName/assetName 등 DTO 동봉 이름). 마스킹 ON 시
 * 금융 민감필드(잔액·금액·신용한도·기관)를 "****" 로 가린다.
 *
 * <p>건수는 현재 목록 size 기반(MVP). 대용량 거래의 전용 COUNT 쿼리는 후속 최적화.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportDataService {

    private static final String MASKED = "****";

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final AssetRepository assetRepository;
    private final MemoRepository memoRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final TodoRepository todoRepository;
    private final ExportCountRepository exportCountRepository;

    /** 전체 행을 담은 표. */
    public ExportTable buildTable(ExportType type, Long userRowId, LocalDate start, LocalDate end, boolean mask) {
        return switch (type) {
            case EXPENSE -> expenseTable(userRowId, start, end, mask);
            case ASSET -> assetTable(userRowId, mask);
            case BUDGET -> budgetTable(userRowId, start, end, mask);
            case CATEGORY -> categoryTable(userRowId);
            case MEMO -> memoTable(userRowId);
            case CALENDAR -> calendarTable(userRowId, start, end, mask);
            case TODO -> todoTable(userRowId, start, end, mask);
        };
    }

    /** 건수 (기간 적용 후). 대용량 타입은 전용 COUNT(*), 그 외는 목록 size. */
    public long count(ExportType type, Long userRowId, LocalDate start, LocalDate end) {
        return switch (type) {
            case EXPENSE -> exportCountRepository.countExpense(userRowId, start, end);
            case CALENDAR -> exportCountRepository.countCalendar(userRowId, start, end);
            case TODO -> exportCountRepository.countTodo(userRowId, start, end);
            // 자산·카테고리·예산·메모는 사용자당 소량 — 목록 size 로 충분.
            default -> buildTable(type, userRowId, start, end, false).rows().size();
        };
    }

    // ── 타입별 표 ─────────────────────────────────────────────

    private ExportTable expenseTable(Long userRowId, LocalDate start, LocalDate end, boolean mask) {
        List<String> headers = List.of("날짜", "유형", "카테고리", "자산", "금액", "설명", "거래처", "결제수단");
        List<List<String>> rows = new ArrayList<>();
        expenseRepository.findByDateRange(userRowId, start, end).forEach(e -> {
            ExpenseServiceDto.ExpenseInfo i = ExpenseServiceDto.ExpenseInfo.from(e);
            rows.add(List.of(
                cell(i.expenseDate()),
                cell(i.expenseType()),
                cell(i.categoryName()),
                cell(i.assetName()),
                money(mask, i.amount()),
                cell(i.description()),
                cell(i.merchant()),
                cell(i.paymentMethod())
            ));
        });
        return new ExportTable(ExportType.EXPENSE, headers, rows);
    }

    private ExportTable assetTable(Long userRowId, boolean mask) {
        List<String> headers = List.of("자산명", "유형", "잔액", "통화", "기관", "메모", "총자산 포함");
        List<List<String>> rows = new ArrayList<>();
        assetRepository.findByUser(userRowId).forEach(a -> {
            AssetServiceDto.AssetInfo i = AssetServiceDto.AssetInfo.from(a);
            rows.add(List.of(
                cell(i.assetName()),
                cell(i.assetType()),
                money(mask, i.balance()),
                cell(i.currency()),
                mask ? MASKED : cell(i.institution()),
                cell(i.memo()),
                yn(i.isIncludedInTotal())
            ));
        });
        return new ExportTable(ExportType.ASSET, headers, rows);
    }

    private ExportTable budgetTable(Long userRowId, LocalDate start, LocalDate end, boolean mask) {
        List<String> headers = List.of("연도", "월", "카테고리", "예산액");
        List<List<String>> rows = new ArrayList<>();
        YearMonth from = YearMonth.from(start);
        YearMonth to = YearMonth.from(end);
        for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
            expenseBudgetRepository.findByUser(userRowId, ym.getYear(), ym.getMonthValue()).forEach(b -> {
                ExpenseBudgetServiceDto.BudgetInfo i = ExpenseBudgetServiceDto.BudgetInfo.from(b);
                rows.add(List.of(
                    cell(i.budgetYear()),
                    cell(i.budgetMonth()),
                    cell(i.categoryName()),
                    money(mask, i.budgetAmount())
                ));
            });
        }
        return new ExportTable(ExportType.BUDGET, headers, rows);
    }

    private ExportTable categoryTable(Long userRowId) {
        List<String> headers = List.of("카테고리명", "유형", "상위 카테고리", "정렬순서");
        var categories = expenseCategoryRepository.findAllByUser(userRowId);
        Map<Long, String> nameById = new HashMap<>();
        categories.forEach(c -> nameById.put(c.getRowId(), c.getCategoryName()));
        List<List<String>> rows = new ArrayList<>();
        categories.forEach(c -> {
            ExpenseCategoryServiceDto.CategoryInfo i = ExpenseCategoryServiceDto.CategoryInfo.from(c);
            String parentName = i.parentRowId() != null
                ? nameById.getOrDefault(i.parentRowId(), String.valueOf(i.parentRowId()))
                : "";
            rows.add(List.of(
                cell(i.categoryName()),
                cell(i.expenseType()),
                parentName,
                cell(i.sortOrder())
            ));
        });
        return new ExportTable(ExportType.CATEGORY, headers, rows);
    }

    private ExportTable memoTable(Long userRowId) {
        List<String> headers = List.of("제목", "내용", "고정", "생성일");
        List<List<String>> rows = new ArrayList<>();
        memoRepository.findAllByUser(userRowId, null, null).forEach(m -> {
            MemoServiceDto.MemoInfo i = MemoServiceDto.MemoInfo.from(m);
            rows.add(List.of(
                cell(i.title()),
                cell(i.content()),
                yn(i.isPinned()),
                cell(i.createAt())
            ));
        });
        return new ExportTable(ExportType.MEMO, headers, rows);
    }

    private ExportTable calendarTable(Long userRowId, LocalDate start, LocalDate end, boolean mask) {
        List<String> headers = List.of("제목", "시작", "종료", "종일", "유형", "라벨", "캘린더", "장소", "설명");
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atTime(LocalTime.MAX);
        List<List<String>> rows = new ArrayList<>();
        calendarEventRepository.findByUserAndDateRange(userRowId, startDt, endDt).forEach(e -> {
            CalendarEventServiceDto.EventInfo i = CalendarEventServiceDto.EventInfo.from(e);
            rows.add(List.of(
                cell(i.title()),
                cell(i.startDate()),
                cell(i.endDate()),
                yn(i.isAllDay()),
                cell(i.eventType()),
                cell(i.labelName()),
                cell(i.calendarName()),
                cell(i.location()),
                cell(i.description())
            ));
        });
        return new ExportTable(ExportType.CALENDAR, headers, rows);
    }

    private ExportTable todoTable(Long userRowId, LocalDate start, LocalDate end, boolean mask) {
        List<String> headers = List.of("제목", "유형", "상태", "우선순위", "카테고리", "마감일", "완료일", "내용");
        List<List<String>> rows = new ArrayList<>();
        todoRepository.findByUserAndDueDateBetween(userRowId, start, end).forEach(t -> {
            TodoServiceDto.TodoInfo i = TodoServiceDto.TodoInfo.from(t);
            rows.add(List.of(
                cell(i.title()),
                cell(i.type()),
                cell(i.status()),
                cell(i.priority()),
                cell(i.category()),
                cell(i.dueDate()),
                cell(i.completedAt()),
                cell(i.content())
            ));
        });
        return new ExportTable(ExportType.TODO, headers, rows);
    }

    // ── 셀 헬퍼 ──────────────────────────────────────────────

    private static String cell(Object o) {
        if (o == null) return "";
        if (o instanceof Enum<?> e) return e.name();
        return String.valueOf(o);
    }

    /** 금융 민감 금액 — 마스킹 ON 이면 가린다. */
    private static String money(boolean mask, Object amount) {
        if (amount == null) return "";
        return mask ? MASKED : String.valueOf(amount);
    }

    private static String yn(YNType v) {
        if (v == null) return "";
        return v == YNType.Y ? "예" : "아니오";
    }
}
