package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseCategoryServiceDto;

import java.util.List;

public interface ExpenseCategoryService {
    ExpenseCategoryServiceDto.CategoryInfo createCategory(ExpenseCategoryServiceDto.CreateCommand command);
    List<ExpenseCategoryServiceDto.CategoryInfo> getCategories(Long userRowId);
    ExpenseCategoryServiceDto.CategoryInfo updateCategory(Long categoryId, ExpenseCategoryServiceDto.UpdateCommand command);
    void deleteCategory(Long categoryId);
}
