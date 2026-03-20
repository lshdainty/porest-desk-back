package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;
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

        category.updateCategory(
            command.categoryName(),
            command.icon(),
            command.color(),
            command.sortOrder()
        );

        log.info("지출 카테고리 수정 완료: categoryId={}", categoryId);

        return ExpenseCategoryServiceDto.CategoryInfo.from(category);
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

        category.deleteCategory();

        log.info("지출 카테고리 삭제 완료: categoryId={}", categoryId);
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
