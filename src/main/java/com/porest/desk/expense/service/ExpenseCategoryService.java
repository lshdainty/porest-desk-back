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

    /**
     * 하위 카테고리를 만들면서 이 카테고리의 거래를 그리로 옮긴다.
     *
     * <p>거래가 달린 카테고리는 하위를 만들 수 없고, 옮길 하위가 없으면 거래도 못 옮기는
     * 교착이 생긴다. 생성과 이동을 한 트랜잭션으로 묶어 그 고리를 끊는다 —
     * 생성 시점엔 규칙 위반이지만 커밋 시점엔 부모에 직접 거래가 없어 정합하다.
     */
    ExpenseCategoryServiceDto.MoveResult moveTransactionsToNewChild(
            Long categoryId, String childName, String icon, String color, Long userRowId);

    ExpenseCategoryServiceDto.MoveResult moveTransactions(Long sourceCategoryId, Long targetCategoryId, Long userRowId);

    void deleteCategory(Long categoryId, Long userRowId);
    void reorderCategories(Long userRowId, List<ExpenseCategoryServiceDto.ReorderItem> items);
}
