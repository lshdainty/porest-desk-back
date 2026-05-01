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
    /**
     * 템플릿 자체로 거래를 만들지 않고 useCount/lastUsedAt 만 갱신한다.
     * AddTxSheet 에서 칩으로 프리셋을 적용한 뒤 사용자가 폼을 수정해 일반 거래로 저장하는 경로용.
     */
    ExpenseTemplateServiceDto.TemplateInfo markTemplateUsed(Long templateId, Long userRowId);
}
