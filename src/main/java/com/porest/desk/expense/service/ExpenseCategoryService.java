package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;

import java.util.List;

public interface ExpenseCategoryService {
    ExpenseCategoryServiceDto.CategoryInfo createCategory(ExpenseCategoryServiceDto.CreateCommand command);
    List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId);
    ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, Long userRowId, ExpenseCategoryServiceDto.UpdateCommand command);

    /**
     * 카테고리에 달린 거래·반복거래·분할을 다른 카테고리로 일괄 이동한다.
     *
     * <p>거래가 직접 달린 카테고리는 부모가 될 수 없어 하위 분류를 만들 수 없다.
     * 그 상태를 푸는 유일한 방법이 거래를 다른 곳으로 옮기는 것인데, 지금까지는
     * 거래를 하나씩 편집하는 수밖에 없었다.
     */
    ExpenseCategoryServiceDto.MoveResult moveTransactions(Long sourceCategoryId, Long targetCategoryId, Long userRowId);

    void deleteCategory(Long categoryId, Long userRowId);
    void reorderCategories(Long userRowId, List<ExpenseCategoryServiceDto.ReorderItem> items);
}
