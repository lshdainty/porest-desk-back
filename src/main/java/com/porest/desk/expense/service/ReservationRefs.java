package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.type.TxKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 예약(반복 거래·프리셋)이 <b>참조를 풀 때</b>의 공통 규칙.
 *
 * <p>반복과 프리셋은 저장하는 것이 다르지만 카테고리·자산을 받는 방식은 똑같다 — 같은 조회,
 * 같은 소유 검증, 같은 거절 사유. 그 블록이 네 자리(양쪽 서비스의 생성·수정)에 그대로
 * 복사돼 있었고, 이체를 넣을 때도 같은 {@code isTransfer()} 분기를 양쪽에 각각 넣었다.
 *
 * <p>카테고리와 자산을 한 클래스에 둔 이유는 <b>쓰는 자리가 같기 때문</b>이다 — 네 자리가
 * 둘을 나란히 풀고, 남의 것을 가리키면 둘 다 {@code EXPENSE_ACCESS_DENIED} 로 끊는다.
 * 도메인이 달라서 가르면 호출부는 매번 둘을 함께 부르면서 임포트만 두 줄이 된다.
 *
 * <p><b>입구가 셋인 이유</b>는 네 자리가 참조를 푸는 방식이 실제로 다르기 때문이다.
 *
 * <ul>
 *   <li>생성(반복·프리셋) — 실린 id 로 찾고 <b>소유까지</b> 본다</li>
 *   <li>반복 수정 — 실린 id 로 찾는다. 규칙 소유는 이미 확인했고, 카테고리 소유 재확인은
 *       종전 동작에 없다(여기서 늘리지 않는다 — 정리 PR 이다)</li>
 *   <li>프리셋 수정 — 카테고리가 {@code Patch} 병합 결과로 이미 정해져 온다. 조회할 id 가
 *       없을 수 있으므로 <b>규칙만</b> 본다</li>
 * </ul>
 *
 * 셋이 모두 {@link #validateCategory} 한 줄로 모인다 — 갈리면 안 되는 건 그 규칙이다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationRefs {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final AssetRepository assetRepository;

    /** 생성 경로 — id 가 없으면 null. 있으면 찾고, 남의 것인지 보고, 규칙까지 본다. */
    public ExpenseCategory resolveOwnedCategory(TxKind kind, Long categoryRowId, Long userRowId) {
        if (categoryRowId == null) {
            return null;
        }
        ExpenseCategory category = findCategoryOrThrow(categoryRowId);
        if (!category.getUser().getRowId().equals(userRowId)) {
            log.warn("지출 카테고리 소유권 검증 실패 - categoryId={}, ownerRowId={}, requestUserRowId={}",
                category.getRowId(), category.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
        validateCategory(kind, category);
        return category;
    }

    /** 반복 수정 — id 가 없으면 null. 있으면 찾고 규칙만 본다. */
    public ExpenseCategory resolveCategory(TxKind kind, Long categoryRowId) {
        if (categoryRowId == null) {
            return null;
        }
        ExpenseCategory category = findCategoryOrThrow(categoryRowId);
        validateCategory(kind, category);
        return category;
    }

    /**
     * 이미 정해진 카테고리가 이 종류에 맞는가 — 네 자리가 같이 보는 규칙.
     *
     * <p>{@code category} 가 null 이면 볼 것이 없다(카테고리를 안 붙인 예약).
     */
    public void validateCategory(TxKind kind, ExpenseCategory category) {
        if (category == null) {
            return;
        }
        // 거래 유형 == 카테고리 유형 강제 (혼재 시 집계 오염 방지).
        // 이체는 카테고리 자체가 없어야 하므로 카테고리가 실린 순간 틀린 요청이다.
        if (kind.isTransfer() || category.getExpenseType() != kind.toExpenseType()) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_TYPE_CATEGORY_MISMATCH);
        }
        // 정책: 상위(자식 보유) 카테고리에는 예약을 둘 수 없음.
        if (expenseCategoryRepository.hasChildren(category.getRowId())) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_NOT_LEAF);
        }
    }

    /** id 가 없으면 null. 있으면 조회하고 남의 자산인지 본다 — 생성·수정이 같은 규칙을 쓴다. */
    public Asset findOwnedAsset(Long assetRowId, Long userRowId) {
        if (assetRowId == null) {
            return null;
        }
        Asset asset = assetRepository.findById(assetRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.ASSET_NOT_FOUND));
        if (!asset.getUser().getRowId().equals(userRowId)) {
            log.warn("자산 소유권 검증 실패 - assetId={}, ownerRowId={}, requestUserRowId={}",
                asset.getRowId(), asset.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
        return asset;
    }

    private ExpenseCategory findCategoryOrThrow(Long categoryRowId) {
        return expenseCategoryRepository.findById(categoryRowId)
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND));
    }
}
