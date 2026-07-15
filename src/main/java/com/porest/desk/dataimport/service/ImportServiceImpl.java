package com.porest.desk.dataimport.service;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.dataimport.type.ImportField;
import com.porest.desk.dataimport.type.ImportSource;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.ExpenseCategoryService;
import com.porest.desk.expense.service.ExpenseService;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 데이터 가져오기 오케스트레이션 — 파싱({@link FileParser}) → 매핑({@link ImportColumnMapper})
 * → 중복표시 → (execute) 거래 저장.
 *
 * <p>execute 는 {@link Propagation#NOT_SUPPORTED} 로 자신은 트랜잭션을 열지 않는다.
 * 각 {@code createExpense}/{@code createCategory} 가 자기 {@code @Transactional} 로 <b>건별 커밋</b>되어
 * 한 행 실패가 전체를 롤백하지 않는다(대량 import 부분성공 보장).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ImportServiceImpl implements ImportService {

    private static final int PREVIEW_LIMIT = 8;
    private static final int MAX_FAILURES = 50;
    private static final String UNCATEGORIZED = "미분류";
    private static final String DEFAULT_ICON = "tag";
    private static final String DEFAULT_COLOR = "#9E9E9E";

    private final ExpenseService expenseService;
    private final ExpenseCategoryService expenseCategoryService;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;
    private final ExpenseRepository expenseRepository;

    @Override
    public AnalyzeResult analyze(MultipartFile file, ImportSource source, Long userRowId) {
        ParsedFile parsed = FileParser.parse(file);
        Map<ImportField, Integer> mapping = ImportColumnMapper.suggest(source, parsed.headers());
        List<StandardRow> rows = mapRows(mapping, parsed.rows());
        markDuplicates(rows, userRowId);

        int validRows = (int) rows.stream().filter(StandardRow::valid).count();
        int dupCount = (int) rows.stream().filter(r -> r.valid() && r.duplicate()).count();
        List<StandardRow> preview = rows.stream().limit(PREVIEW_LIMIT).toList();

        return new AnalyzeResult(
            safeName(file), parsed.rows().size(), validRows, dupCount,
            parsed.headers(), mapping, preview);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExecuteResult execute(MultipartFile file, ImportSource source, Map<ImportField, Integer> mapping,
                                 boolean dupSkip, boolean autoCat, Long userRowId) {
        validateMapping(mapping);
        ParsedFile parsed = FileParser.parse(file);
        List<StandardRow> rows = mapRows(mapping, parsed.rows());
        markDuplicates(rows, userRowId);

        CategoryResolver categories = new CategoryResolver(userRowId);
        AssetResolver assets = new AssetResolver(userRowId);

        int imported = 0, skipped = 0, failed = 0;
        List<Failure> failures = new ArrayList<>();

        for (StandardRow r : rows) {
            if (!r.valid()) {
                failed++;
                addFailure(failures, r.lineNo(), r.error());
                continue;
            }
            if (dupSkip && r.duplicate()) {
                skipped++;
                continue;
            }
            try {
                Long categoryRowId = categories.resolve(r.category(), r.type(), autoCat);
                Long assetRowId = assets.resolve(r.asset());
                String description = mergeDesc(r.subcategory(), r.memo());
                expenseService.createExpense(new ExpenseServiceDto.CreateCommand(
                    userRowId, categoryRowId, assetRowId, r.type(), r.amount(),
                    description, r.date(), null, r.asset(), null, null));
                imported++;
            } catch (Exception e) {
                failed++;
                addFailure(failures, r.lineNo(), "save");
                log.warn("가져오기 행 저장 실패 line={}: {}", r.lineNo(), e.getMessage());
            }
        }
        log.info("가져오기 완료: userRowId={}, imported={}, skipped={}, failed={}", userRowId, imported, skipped, failed);
        return new ExecuteResult(imported, skipped, failed, failures);
    }

    // ── 공통 ─────────────────────────────────────────────────

    private void validateMapping(Map<ImportField, Integer> mapping) {
        boolean hasAmount = mapping != null && (mapping.containsKey(ImportField.AMOUNT)
            || mapping.containsKey(ImportField.AMOUNT_OUT) || mapping.containsKey(ImportField.AMOUNT_IN));
        if (mapping == null || !mapping.containsKey(ImportField.DATE) || !hasAmount) {
            throw new InvalidValueException(DeskErrorCode.IMPORT_MAPPING_REQUIRED);
        }
    }

    private List<StandardRow> mapRows(Map<ImportField, Integer> mapping, List<List<String>> rows) {
        List<StandardRow> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            out.add(ImportColumnMapper.mapRow(mapping, rows.get(i), i + 1));
        }
        return out;
    }

    /** 유효행을 기존 거래(같은 날짜·금액·설명)와 대조해 중복 표시. */
    private void markDuplicates(List<StandardRow> rows, Long userRowId) {
        LocalDate min = null, max = null;
        for (StandardRow r : rows) {
            if (!r.valid()) continue;
            LocalDate d = r.date().toLocalDate();
            if (min == null || d.isBefore(min)) min = d;
            if (max == null || d.isAfter(max)) max = d;
        }
        if (min == null) return;

        Set<String> existing = new HashSet<>();
        expenseRepository.findByDateRange(userRowId, min, max).forEach(e ->
            existing.add(dupKey(e.getExpenseDate().toLocalDate(), e.getAmount(), e.getDescription())));
        if (existing.isEmpty()) return;

        for (int i = 0; i < rows.size(); i++) {
            StandardRow r = rows.get(i);
            if (r.valid() && existing.contains(dupKey(r.date().toLocalDate(), r.amount(), mergeDesc(r.subcategory(), r.memo())))) {
                rows.set(i, r.withDuplicate(true));
            }
        }
    }

    private static String dupKey(LocalDate date, Long amount, String desc) {
        return date + "|" + amount + "|" + (desc == null ? "" : desc.trim());
    }

    private static String mergeDesc(String sub, String memo) {
        if (sub != null && memo != null) return sub + " " + memo;
        if (sub != null) return sub;
        return memo;
    }

    private static void addFailure(List<Failure> failures, int lineNo, String reason) {
        if (failures.size() < MAX_FAILURES) failures.add(new Failure(lineNo, reason));
    }

    private static String safeName(MultipartFile file) {
        String n = file.getOriginalFilename();
        return n == null || n.isBlank() ? "import" : n;
    }

    // ── 카테고리 해석(캐시 + 자동생성) ───────────────────────

    private final class CategoryResolver {
        private final Long userRowId;
        private final Map<String, Long> leafByKey = new HashMap<>();

        CategoryResolver(Long userRowId) {
            this.userRowId = userRowId;
            var cats = expenseCategoryRepository.findAllByUser(userRowId);
            Set<Long> parentIds = new HashSet<>();
            cats.forEach(c -> {
                if (c.getParent() != null) parentIds.add(c.getParent().getRowId());
            });
            cats.forEach(c -> {
                if (!parentIds.contains(c.getRowId())) { // leaf 만 매칭 대상 (거래 귀속은 leaf 강제)
                    leafByKey.putIfAbsent(key(c.getExpenseType(), c.getCategoryName()), c.getRowId());
                }
            });
        }

        Long resolve(String name, ExpenseType type, boolean autoCat) {
            if (name != null && !name.isBlank()) {
                Long hit = leafByKey.get(key(type, name));
                if (hit != null) return hit;
                if (autoCat) return create(name, type);
            }
            return create(UNCATEGORIZED, type); // 카테고리 필수 → 미분류 확보
        }

        private Long create(String name, ExpenseType type) {
            Long cached = leafByKey.get(key(type, name));
            if (cached != null) return cached;
            var info = expenseCategoryService.createCategory(new ExpenseCategoryServiceDto.CreateCommand(
                userRowId, name, DEFAULT_ICON, DEFAULT_COLOR, type, null));
            leafByKey.put(key(type, name), info.rowId());
            return info.rowId();
        }

        private String key(ExpenseType type, String name) {
            return type.name() + "|" + name.trim().toLowerCase();
        }
    }

    // ── 자산 해석(이름 매칭, 없으면 null) ────────────────────

    private final class AssetResolver {
        private final Map<String, Long> rowIdByName = new HashMap<>();

        AssetResolver(Long userRowId) {
            assetRepository.findByUser(userRowId).forEach(a ->
                rowIdByName.putIfAbsent(a.getAssetName().trim().toLowerCase(), a.getRowId()));
        }

        Long resolve(String name) {
            if (name == null || name.isBlank()) return null;
            return rowIdByName.get(name.trim().toLowerCase());
        }
    }
}
