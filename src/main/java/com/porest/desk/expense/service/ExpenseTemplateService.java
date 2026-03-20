package com.porest.desk.expense.service;

import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;

import java.time.LocalDate;
import java.util.List;

public interface ExpenseTemplateService {
    ExpenseTemplateServiceDto.TemplateInfo createTemplate(ExpenseTemplateServiceDto.CreateCommand command);
    List<ExpenseTemplateServiceDto.TemplateInfo> getTemplates(Long userRowId);
    ExpenseTemplateServiceDto.TemplateInfo updateTemplate(Long templateId, Long userRowId, ExpenseTemplateServiceDto.UpdateCommand command);
    void deleteTemplate(Long templateId, Long userRowId);
    ExpenseServiceDto.ExpenseInfo useTemplate(Long templateId, Long userRowId, LocalDate expenseDate);
}
