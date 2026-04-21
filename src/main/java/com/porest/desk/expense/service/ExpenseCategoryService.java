package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;

import java.util.List;

public interface ExpenseCategoryService {
    ExpenseCategoryServiceDto.CategoryInfo createCategory(ExpenseCategoryServiceDto.CreateCommand command);
    List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId);
    ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, Long userRowId, ExpenseCategoryServiceDto.UpdateCommand command);
    void deleteCategory(Long categoryId, Long userRowId);
    void reorderCategories(Long userRowId, List<ExpenseCategoryServiceDto.ReorderItem> items);
}
