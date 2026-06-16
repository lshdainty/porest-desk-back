package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.RecurringTransactionRepository;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseBudgetRepository expenseBudgetRepository;
    private final ExpenseRepository expenseRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ExpenseCategoryServiceDto.CategoryInfo createCategory(ExpenseCategoryServiceDto.CreateCommand command) {
        log.debug("지출 카테고리 등록 시작: userRowId={}, categoryName={}", command.userRowId(), command.categoryName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        ExpenseCategory parent = null;
        if (command.parentRowId() != null) {
            parent = findCategoryOrThrow(command.parentRowId());
            validateCategoryOwnership(parent, command.userRowId());

            if (parent.getParent() != null) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
            }

            if (parent.getExpenseType() != command.expenseType()) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_TYPE_MISMATCH);
            }
            // 정책: 부모는 직접 거래를 가질 수 없음 — 거래/반복 거래가 있는 카테고리는
            // 자식을 가질(부모가 될) 수 없다. 먼저 내역을 다른 카테고리로 옮겨야 함.
            validateCanBecomeParent(parent.getRowId());
        }

        // 같은 위치(부모)·같은 타입 내 활성 카테고리명 중복 금지 (삭제된 같은 이름은 재사용 허용)
        if (expenseCategoryRepository.existsActiveByUserAndParentAndTypeAndName(
                command.userRowId(), command.parentRowId(), command.expenseType(), command.categoryName(), null)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_DUPLICATE_NAME);
        }

        ExpenseCategory category = ExpenseCategory.createCategory(
            user,
            command.categoryName(),
            command.icon(),
            command.color(),
            command.expenseType(),
            parent
        );

        expenseCategoryRepository.save(category);
        log.info("지출 카테고리 등록 완료: categoryId={}, userRowId={}", category.getRowId(), command.userRowId());

        return ExpenseCategoryServiceDto.CategoryInfo.from(category);
    }

    @Override
    public List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId) {
        log.debug("지출 카테고리 목록 조회: userRowId={}", userRowId);

        List<ExpenseCategory> categories = expenseCategoryRepository.findAllByUser(userRowId);

        Set<Long> parentIds = categories.stream()
            .filter(c -> c.getParent() != null)
            .map(c -> c.getParent().getRowId())
            .collect(Collectors.toSet());

        return categories.stream()
            .map(c -> ExpenseCategoryServiceDto.CategoryInfo.fromWithHasChildren(c, parentIds.contains(c.getRowId())))
            .toList();
    }

    @Override
    @Transactional
    public ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, Long userRowId, ExpenseCategoryServiceDto.UpdateCommand command) {
        log.debug("지출 카테고리 수정 시작: categoryId={}", categoryId);

        ExpenseCategory category = findCategoryOrThrow(categoryId);
        validateCategoryOwnership(category, userRowId);

        // 계층(parentRowId) 변경 정책:
        //  - 최상위(부모) → 하위(강등): 금지. 삭제 후 재생성으로만.
        //  - 하위(자식) → 최상위(승격): 금지(현재). 연결 내역 이관 후 별도 기능에서.
        //  - 하위 → 다른 하위(부모 변경/이동): 허용. 단 새 부모는 거래/반복이 없어야 함.
        boolean wasChild = category.getParent() != null;
        boolean willHaveParent = command.parentRowId() != null;

        if (!wasChild && willHaveParent) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_CANNOT_DEMOTE);
        }
        if (wasChild && !willHaveParent) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_CANNOT_PROMOTE);
        }

        ExpenseType targetType = command.expenseType() != null
            ? command.expenseType()
            : category.getExpenseType();

        ExpenseCategory targetParent = null;
        if (willHaveParent) {
            // 이 시점은 하위 → 다른 하위 이동만 도달 (위에서 강등/승격 차단됨).
            if (command.parentRowId().equals(category.getRowId())) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
            }
            targetParent = findCategoryOrThrow(command.parentRowId());
            validateCategoryOwnership(targetParent, userRowId);
            if (targetParent.getParent() != null) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
            }
            if (targetParent.getExpenseType() != targetType) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_TYPE_MISMATCH);
            }
            // 새 부모가 아직 자식이 없던 leaf 라면, 그 자체가 거래/반복을 보유한 경우
            // 부모가 될 수 없다(부모는 직접 거래 불가).
            if (!expenseCategoryRepository.hasChildren(targetParent.getRowId())) {
                validateCanBecomeParent(targetParent.getRowId());
            }
        } else {
            // 최상위 유지 — 자식이 있으면 타입을 바꿔도 자식과 불일치하면 안 됨.
            if (expenseCategoryRepository.hasChildren(categoryId)
                && targetType != category.getExpenseType()) {
                throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_TYPE_MISMATCH);
            }
        }

        // 변경 후 위치(부모)·타입 기준 활성 카테고리명 중복 금지 (자기 자신 제외)
        if (expenseCategoryRepository.existsActiveByUserAndParentAndTypeAndName(
                userRowId, command.parentRowId(), targetType, command.categoryName(), categoryId)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_DUPLICATE_NAME);
        }

        category.updateCategory(
            command.categoryName(),
            command.icon(),
            command.color(),
            command.sortOrder()
        );
        category.changeExpenseType(targetType);
        category.moveParent(targetParent);

        log.info("지출 카테고리 수정 완료: categoryId={}", categoryId);

        return ExpenseCategoryServiceDto.CategoryInfo.from(category);
    }

    /** 거래 또는 반복 거래가 있는 카테고리는 부모(상위)가 될 수 없다. */
    private void validateCanBecomeParent(Long categoryId) {
        if (expenseRepository.existsByCategory(categoryId)
            || recurringTransactionRepository.existsByCategory(categoryId)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_PARENT_HAS_TX);
        }
    }

    @Override
    @Transactional
    public void deleteCategory(Long categoryId, Long userRowId) {
        log.debug("지출 카테고리 삭제 시작: categoryId={}", categoryId);

        ExpenseCategory category = findCategoryOrThrow(categoryId);
        validateCategoryOwnership(category, userRowId);

        if (expenseCategoryRepository.hasChildren(categoryId)) {
            throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_HAS_CHILDREN);
        }

        // 정책: 예산이 걸린 카테고리를 삭제하면 예산도 함께 삭제(cascade).
        // (클라이언트가 사전 확인 다이얼로그로 사용자 동의를 받는다.)
        List<ExpenseBudget> budgets = expenseBudgetRepository.findAllByCategory(categoryId);
        for (ExpenseBudget budget : budgets) {
            expenseBudgetRepository.delete(budget);
        }

        category.deleteCategory();

        log.info("지출 카테고리 삭제 완료: categoryId={}, 예산 {}건 정리", categoryId, budgets.size());
    }

    @Override
    @Transactional
    public void reorderCategories(Long userRowId, List<ExpenseCategoryServiceDto.ReorderItem> items) {
        log.debug("지출 카테고리 정렬 변경: userRowId={}, count={}", userRowId, items.size());

        for (ExpenseCategoryServiceDto.ReorderItem item : items) {
            ExpenseCategory category = findCategoryOrThrow(item.categoryRowId());
            validateCategoryOwnership(category, userRowId);

            // parent 변경 요청이 있으면 적용 (순환/깊이 2+ 방지)
            Long newParentRowId = item.parentRowId();
            Long currentParentRowId = category.getParent() != null ? category.getParent().getRowId() : null;
            boolean parentChanged = !java.util.Objects.equals(newParentRowId, currentParentRowId);
            if (parentChanged) {
                if (newParentRowId == null) {
                    category.moveParent(null);
                } else {
                    if (newParentRowId.equals(category.getRowId())) {
                        throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
                    }
                    ExpenseCategory newParent = findCategoryOrThrow(newParentRowId);
                    validateCategoryOwnership(newParent, userRowId);
                    if (newParent.getParent() != null) {
                        throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
                    }
                    if (newParent.getExpenseType() != category.getExpenseType()) {
                        throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_TYPE_MISMATCH);
                    }
                    // 본인이 이미 부모(자식 있음)인 경우 parent 할당 금지 (2단계 깊이 초과 방지)
                    if (expenseCategoryRepository.hasChildren(category.getRowId())) {
                        throw new InvalidValueException(DeskErrorCode.EXPENSE_CATEGORY_MAX_DEPTH);
                    }
                    category.moveParent(newParent);
                }
            }
            category.updateSortOrder(item.sortOrder());
        }

        log.info("지출 카테고리 정렬 변경 완료: userRowId={}", userRowId);
    }

    private void validateCategoryOwnership(ExpenseCategory category, Long userRowId) {
        if (!category.getUser().getRowId().equals(userRowId)) {
            log.warn("지출 카테고리 소유권 검증 실패 - categoryId={}, ownerRowId={}, requestUserRowId={}",
                category.getRowId(), category.getUser().getRowId(), userRowId);
            throw new ForbiddenException(DeskErrorCode.EXPENSE_ACCESS_DENIED);
        }
    }

    private ExpenseCategory findCategoryOrThrow(Long categoryId) {
        return expenseCategoryRepository.findById(categoryId)
            .orElseThrow(() -> {
                log.warn("지출 카테고리 조회 실패 - 존재하지 않는 카테고리: categoryId={}", categoryId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND);
            });
    }
}
