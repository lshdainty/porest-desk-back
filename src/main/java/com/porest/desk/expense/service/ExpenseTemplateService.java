package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseTemplateService {
    ExpenseTemplateServiceDto.TemplateInfo createTemplate(ExpenseTemplateServiceDto.CreateCommand command);
    List<ExpenseTemplateServiceDto.TemplateInfo> getTemplates(Long userRowId);
    ExpenseTemplateServiceDto.TemplateInfo updateTemplate(Long templateId, ExpenseTemplateServiceDto.UpdateCommand command);
    void deleteTemplate(Long templateId);
    ExpenseServiceDto.ExpenseInfo useTemplate(Long templateId, LocalDate expenseDate);
}
