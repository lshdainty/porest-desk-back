package com.porest.desk.stock.service;

import com.porest.core.exception.DuplicateException;
import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.stock.domain.StockMaster;
import com.porest.desk.stock.domain.StockWatchGroup;
import com.porest.desk.stock.domain.StockWatchItem;
import com.porest.desk.stock.repository.StockWatchGroupRepository;
import com.porest.desk.stock.repository.StockWatchItemRepository;
import com.porest.desk.stock.service.dto.StockWatchServiceDto;
import com.porest.desk.stock.type.StockMarket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class StockWatchServiceImpl implements StockWatchService {

    /** 사용자당 그룹 상한 — 화면 탭 UI 가 감당 가능한 수준의 방어선 */
    private static final int MAX_GROUPS_PER_USER = 20;
    /** 그룹당 종목 상한 — 시세 폴링 비용 방어선 */
    private static final int MAX_ITEMS_PER_GROUP = 100;
    /** 시장 미지정 심볼 해석 시 우선하는 시장 — 토스 시세 대상(KR/US) */

    private final StockWatchGroupRepository groupRepository;
    private final StockWatchItemRepository itemRepository;
    private final StockMasterResolver stockMasterResolver;

    @Override
    public List<StockWatchServiceDto.GroupInfo> getGroups(Long userRowId) {
        log.debug("관심목록 조회: userRowId={}", userRowId);
        List<StockWatchGroup> groups = groupRepository.findAllActiveByUser(userRowId);

        Map<Long, List<StockWatchServiceDto.ItemInfo>> itemsByGroup = new LinkedHashMap<>();
        for (StockWatchItemRepository.ItemWithStock row : itemRepository.findAllActiveByUserWithStock(userRowId)) {
            itemsByGroup.computeIfAbsent(row.item().getGroupRowId(), k -> new ArrayList<>())
                .add(StockWatchServiceDto.ItemInfo.of(row.item(), row.stock()));
        }

        return groups.stream()
            .map(g -> StockWatchServiceDto.GroupInfo.of(g, itemsByGroup.getOrDefault(g.getRowId(), List.of())))
            .toList();
    }

    @Override
    @Transactional
    public StockWatchServiceDto.GroupInfo createGroup(Long userRowId, String groupName) {
        log.debug("관심목록 그룹 생성: userRowId={}, groupName={}", userRowId, groupName);
        String name = normalizeGroupName(groupName);

        if (groupRepository.countActiveByUser(userRowId) >= MAX_GROUPS_PER_USER) {
            throw new InvalidValueException(DeskErrorCode.STOCK_WATCH_GROUP_LIMIT_EXCEEDED);
        }
        if (groupRepository.existsActiveByUserAndName(userRowId, name)) {
            throw new DuplicateException(DeskErrorCode.STOCK_WATCH_GROUP_NAME_DUPLICATE);
        }

        int nextOrder = groupRepository.findAllActiveByUser(userRowId).stream()
            .mapToInt(StockWatchGroup::getSortOrder)
            .max()
            .orElse(-1) + 1;
        StockWatchGroup group = groupRepository.save(StockWatchGroup.create(userRowId, name, nextOrder));
        log.info("관심목록 그룹 생성 완료: userRowId={}, groupRowId={}", userRowId, group.getRowId());
        return StockWatchServiceDto.GroupInfo.of(group, List.of());
    }

    @Override
    @Transactional
    public StockWatchServiceDto.GroupInfo renameGroup(Long userRowId, Long groupRowId, String groupName) {
        log.debug("관심목록 그룹 이름 변경: userRowId={}, groupRowId={}", userRowId, groupRowId);
        String name = normalizeGroupName(groupName);
        StockWatchGroup group = findOwnedGroup(userRowId, groupRowId);

        if (!group.getGroupName().equals(name) && groupRepository.existsActiveByUserAndName(userRowId, name)) {
            throw new DuplicateException(DeskErrorCode.STOCK_WATCH_GROUP_NAME_DUPLICATE);
        }
        group.rename(name);

        List<StockWatchServiceDto.ItemInfo> items = itemRepository.findAllActiveByUserWithStock(userRowId).stream()
            .filter(row -> groupRowId.equals(row.item().getGroupRowId()))
            .map(row -> StockWatchServiceDto.ItemInfo.of(row.item(), row.stock()))
            .toList();
        return StockWatchServiceDto.GroupInfo.of(group, items);
    }

    @Override
    @Transactional
    public void deleteGroup(Long userRowId, Long groupRowId) {
        log.debug("관심목록 그룹 삭제: userRowId={}, groupRowId={}", userRowId, groupRowId);
        StockWatchGroup group = findOwnedGroup(userRowId, groupRowId);

        // 그룹을 지우면 소속 종목도 함께 정리한다. 남겨두면 조인 조회에서 고아 행이 걸러질 뿐 데이터가 쌓인다.
        for (StockWatchItem item : itemRepository.findAllActiveByGroup(groupRowId)) {
            item.delete();
        }
        group.delete();
        log.info("관심목록 그룹 삭제 완료: userRowId={}, groupRowId={}", userRowId, groupRowId);
    }

    @Override
    @Transactional
    public StockWatchServiceDto.ItemInfo addItem(Long userRowId, Long groupRowId, String symbol, StockMarket marketCode) {
        log.debug("관심 종목 추가: userRowId={}, groupRowId={}, symbol={}, market={}", userRowId, groupRowId, symbol, marketCode);
        findOwnedGroup(userRowId, groupRowId);
        StockMaster stock = resolveStock(symbol, marketCode);

        StockWatchItem existing = itemRepository
            .findByGroupAndStockIncludingDeleted(groupRowId, stock.getRowId())
            .orElse(null);

        // 이미 담긴 종목은 그대로 돌려준다 — 별 토글 연타·화면 간 경합에 멱등.
        if (existing != null && !existing.isDeleted()) {
            return StockWatchServiceDto.ItemInfo.of(existing, stock);
        }

        if (itemRepository.countActiveByGroup(groupRowId) >= MAX_ITEMS_PER_GROUP) {
            throw new InvalidValueException(DeskErrorCode.STOCK_WATCH_ITEM_LIMIT_EXCEEDED);
        }

        int nextOrder = itemRepository.findAllActiveByGroup(groupRowId).stream()
            .mapToInt(StockWatchItem::getSortOrder)
            .max()
            .orElse(-1) + 1;

        StockWatchItem item;
        if (existing != null) {
            // (group, stock) 유니크 제약 때문에 새 행 대신 삭제 행을 되살린다.
            existing.restore(nextOrder);
            item = existing;
        } else {
            item = itemRepository.save(StockWatchItem.create(groupRowId, stock.getRowId(), nextOrder));
        }
        log.info("관심 종목 추가 완료: userRowId={}, groupRowId={}, symbol={}", userRowId, groupRowId, stock.getSymbol());
        return StockWatchServiceDto.ItemInfo.of(item, stock);
    }

    @Override
    @Transactional
    public void removeItem(Long userRowId, Long itemRowId) {
        log.debug("관심 종목 제거: userRowId={}, itemRowId={}", userRowId, itemRowId);
        StockWatchItem item = itemRepository.findActiveById(itemRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.STOCK_WATCH_ITEM_NOT_FOUND));

        // 소유권은 소속 그룹으로 검증한다.
        groupRepository.findActiveByIdAndUser(item.getGroupRowId(), userRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.STOCK_WATCH_ITEM_NOT_FOUND));

        item.delete();
        log.info("관심 종목 제거 완료: userRowId={}, itemRowId={}", userRowId, itemRowId);
    }

    private StockWatchGroup findOwnedGroup(Long userRowId, Long groupRowId) {
        return groupRepository.findActiveByIdAndUser(groupRowId, userRowId)
            .orElseThrow(() -> {
                log.warn("관심목록 그룹 조회 실패 - 없거나 소유자가 아님: userRowId={}, groupRowId={}", userRowId, groupRowId);
                return new EntityNotFoundException(DeskErrorCode.STOCK_WATCH_GROUP_NOT_FOUND);
            });
    }

    /** 심볼 → 마스터 해석. 시장 미지정이면 정확 일치 중 KR/US 를 우선한다 (시장 간 6자리 코드 충돌 대비). */
    /**
     * 규칙은 {@link StockMasterResolver} 한 곳에 있다 — 관심목록과 자산 평가가 각자 다른
     * 규칙으로 풀면 같은 심볼이 서로 다른 종목을 가리킨다.
     */
    private StockMaster resolveStock(String symbol, StockMarket marketCode) {
        return stockMasterResolver.resolve(marketCode, symbol)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.STOCK_NOT_FOUND));
    }

    private String normalizeGroupName(String groupName) {
        String name = groupName == null ? "" : groupName.trim();
        if (name.isEmpty() || name.length() > 50) {
            throw new InvalidValueException(DeskErrorCode.INVALID_INPUT);
        }
        return name;
    }
}
