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
                Long categoryRowId = categories.resolve(r.category(), r.subcategory(), r.type(), autoCat);
                Long assetRowId = assets.resolve(r.asset());
                // 소분류는 이제 카테고리(자식)로 쓰이므로 설명에 중복해 넣지 않는다.
                String description = r.memo();
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
            if (r.valid() && existing.contains(dupKey(r.date().toLocalDate(), r.amount(), r.memo()))) {
                rows.set(i, r.withDuplicate(true));
            }
        }
    }

    private static String dupKey(LocalDate date, Long amount, String desc) {
        return date + "|" + amount + "|" + (desc == null ? "" : desc.trim());
    }


    private static void addFailure(List<Failure> failures, int lineNo, String reason) {
        if (failures.size() < MAX_FAILURES) failures.add(new Failure(lineNo, reason));
    }

    private static String safeName(MultipartFile file) {
        String n = file.getOriginalFilename();
        return n == null || n.isBlank() ? "import" : n;
    }

    // ── 카테고리 해석(캐시 + 자동생성) ───────────────────────

    /**
     * 카테고리 해석 — 원본의 대분류/소분류를 우리 부모/자식 계층에 그대로 대응시킨다.
     *
     * <p>편한가계부·뱅크샐러드 같은 소스는 대분류/소분류로 나뉘어 있고, 우리 카테고리도
     * 부모/자식 2단계라 구조가 맞는다. 예전엔 leaf 이름 하나만 봐서 이 정보를 버렸는데,
     * 그러면 "문화생활 > 기타" 와 "여행 > 기타" 처럼 <b>이름이 같은 자식을 구분하지 못했다</b>
     * (먼저 스캔된 쪽으로 전부 몰림).
     *
     * <p>매칭 규칙
     * <ul>
     *   <li>소분류 있음 → 대분류 이름의 부모 아래, 소분류 이름의 자식</li>
     *   <li>소분류 없음 → 대분류 이름의 최상위 leaf (자식 없는 카테고리)</li>
     *   <li>못 찾고 autoCat 이면 부모부터 만들고 그 아래 자식을 만든다</li>
     * </ul>
     * 거래는 leaf 에만 귀속시킨다 — 자식이 있는 부모에 직접 달면 합계가 이중 집계된다.
     */
    private final class CategoryResolver {
        private final Long userRowId;
        /** "타입|부모명|자식명" → rowId. 최상위 leaf 는 부모명을 빈 문자열로 둔다. */
        private final Map<String, Long> byPath = new HashMap<>();
        /** "타입|이름" → rowId (부모 카테고리). 자식을 매달 부모를 찾을 때 쓴다. */
        private final Map<String, Long> parentByName = new HashMap<>();

        CategoryResolver(Long userRowId) {
            this.userRowId = userRowId;
            var cats = expenseCategoryRepository.findAllByUser(userRowId);
            Set<Long> parentIds = new HashSet<>();
            cats.forEach(c -> {
                if (c.getParent() != null) parentIds.add(c.getParent().getRowId());
            });
            cats.forEach(c -> {
                boolean isParent = parentIds.contains(c.getRowId());
                if (isParent) {
                    parentByName.putIfAbsent(key(c.getExpenseType(), c.getCategoryName()), c.getRowId());
                } else {
                    String parentName = c.getParent() != null ? c.getParent().getCategoryName() : "";
                    byPath.putIfAbsent(pathKey(c.getExpenseType(), parentName, c.getCategoryName()), c.getRowId());
                }
            });
            // 자식이 없는 최상위도 "부모 후보" 로 둔다 — 소분류가 들어오면 그 아래로 매달아야 한다.
            cats.forEach(c -> {
                if (c.getParent() == null) {
                    parentByName.putIfAbsent(key(c.getExpenseType(), c.getCategoryName()), c.getRowId());
                }
            });
        }

        Long resolve(String category, String subcategory, ExpenseType type, boolean autoCat) {
            String parentName = blank(category) ? null : category.trim();
            String leafName = blank(subcategory) ? null : subcategory.trim();

            if (parentName == null && leafName == null) {
                return topLevel(UNCATEGORIZED, type);
            }
            // 대분류만 있으면 그 이름의 최상위 leaf 로.
            if (leafName == null) {
                Long hit = byPath.get(pathKey(type, "", parentName));
                if (hit != null) return hit;
                return autoCat ? topLevel(parentName, type) : topLevel(UNCATEGORIZED, type);
            }
            // 소분류만 있으면(대분류 빈칸) 최상위 leaf 로 취급.
            if (parentName == null) {
                Long hit = byPath.get(pathKey(type, "", leafName));
                if (hit != null) return hit;
                return autoCat ? topLevel(leafName, type) : topLevel(UNCATEGORIZED, type);
            }

            Long hit = byPath.get(pathKey(type, parentName, leafName));
            if (hit != null) return hit;
            if (!autoCat) return topLevel(UNCATEGORIZED, type);

            Long parentRowId = parentByName.get(key(type, parentName));
            if (parentRowId == null) {
                parentRowId = create(parentName, type, null);
                parentByName.put(key(type, parentName), parentRowId);
                // 방금 만든 최상위가 leaf 로도 캐시돼 있으면 지운다 — 이제 부모가 됐다.
                byPath.remove(pathKey(type, "", parentName));
            }
            Long childRowId = create(leafName, type, parentRowId);
            byPath.put(pathKey(type, parentName, leafName), childRowId);
            return childRowId;
        }

        /** 최상위(부모 없음) leaf 확보 — 있으면 재사용, 없으면 생성. */
        private Long topLevel(String name, ExpenseType type) {
            Long cached = byPath.get(pathKey(type, "", name));
            if (cached != null) return cached;
            Long rowId = create(name, type, null);
            byPath.put(pathKey(type, "", name), rowId);
            return rowId;
        }

        private Long create(String name, ExpenseType type, Long parentRowId) {
            var info = expenseCategoryService.createCategory(new ExpenseCategoryServiceDto.CreateCommand(
                userRowId, name, DEFAULT_ICON, DEFAULT_COLOR, type, parentRowId));
            return info.rowId();
        }

        private String key(ExpenseType type, String name) {
            return type.name() + "|" + name.trim().toLowerCase();
        }

        private String pathKey(ExpenseType type, String parentName, String name) {
            return type.name() + "|" + parentName.trim().toLowerCase() + "|" + name.trim().toLowerCase();
        }

        private boolean blank(String s) {
            return s == null || s.isBlank();
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
