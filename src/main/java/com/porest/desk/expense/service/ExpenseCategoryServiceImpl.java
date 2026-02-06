package com.porest.desk.expense.service;

import com.porest.core.exception.EntityNotFoundException;
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

        ExpenseCategory category = ExpenseCategory.createCategory(
            user,
            command.categoryName(),
            command.icon(),
            command.color(),
            command.expenseType()
        );

        expenseCategoryRepository.save(category);
        log.info("지출 카테고리 등록 완료: categoryId={}, userRowId={}", category.getRowId(), command.userRowId());

        return ExpenseCategoryServiceDto.CategoryInfo.from(category);
    }

    @Override
    public List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId) {
        log.debug("지출 카테고리 목록 조회: userRowId={}", userRowId);

        List<ExpenseCategory> categories = expenseCategoryRepository.findAllByUser(userRowId);

        return categories.stream()
            .map(ExpenseCategoryServiceDto.CategoryInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, ExpenseCategoryServiceDto.UpdateCommand command) {
        log.debug("지출 카테고리 수정 시작: categoryId={}", categoryId);

        ExpenseCategory category = findCategoryOrThrow(categoryId);

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
    public void deleteCategory(Long categoryId) {
        log.debug("지출 카테고리 삭제 시작: categoryId={}", categoryId);

        ExpenseCategory category = findCategoryOrThrow(categoryId);
        category.deleteCategory();

        log.info("지출 카테고리 삭제 완료: categoryId={}", categoryId);
    }

    private ExpenseCategory findCategoryOrThrow(Long categoryId) {
        return expenseCategoryRepository.findById(categoryId)
            .orElseThrow(() -> {
                log.warn("지출 카테고리 조회 실패 - 존재하지 않는 카테고리: categoryId={}", categoryId);
                return new EntityNotFoundException(DeskErrorCode.EXPENSE_CATEGORY_NOT_FOUND);
            });
    }
}
