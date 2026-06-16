package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.domain.ExpenseTemplate;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.repository.ExpenseTemplateRepository;
import com.porest.desk.expense.service.dto.ExpenseServiceDto;
import com.porest.desk.expense.service.dto.ExpenseTemplateServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 경비 템플릿 정책 회귀 방지 단위 테스트 — 거래와 동일하게 leaf 카테고리만, 소유권 검증.
 * useTemplate 는 생성 이후 카테고리가 부모가 된 경우도 거래 생성을 막는다.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseTemplateServiceImplTest {

    @Mock private ExpenseTemplateRepository expenseTemplateRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ExpenseTemplateServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, null);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseTemplateServiceDto.CreateCommand createCmd(long categoryRowId) {
        return new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "점심 템플릿", categoryRowId, null, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null, null);
    }

    private ExpenseTemplateServiceDto.UpdateCommand updateCmd(long categoryRowId) {
        return new ExpenseTemplateServiceDto.UpdateCommand(
                "점심 템플릿", categoryRowId, null, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null);
    }

    @Test
    @DisplayName("createTemplate — 자식 보유(상위) 카테고리에는 템플릿 불가")
    void createRejectsNonLeafCategory() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(true);

        assertThatThrownBy(() -> sut.createTemplate(createCmd(10L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTemplate — 남의 카테고리에는 템플릿 불가")
    void createRejectsOthersCategory() {
        User u = user(USER_ID);
        ExpenseCategory othersCategory = category(20L, user(999L));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> sut.createTemplate(createCmd(20L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateTemplate — 자식 보유(상위) 카테고리로 변경 불가")
    void updateRejectsNonLeafCategory() {
        User u = user(USER_ID);
        ExpenseTemplate template = mock(ExpenseTemplate.class);
        given(template.getUser()).willReturn(u);
        ExpenseCategory parent = category(30L, u);
        given(expenseTemplateRepository.findById(5L)).willReturn(Optional.of(template));
        given(expenseCategoryRepository.findById(30L)).willReturn(Optional.of(parent));
        given(expenseCategoryRepository.hasChildren(30L)).willReturn(true);

        assertThatThrownBy(() -> sut.updateTemplate(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("updateTemplate — 남의 템플릿은 수정 불가")
    void updateRejectsOthersTemplate() {
        ExpenseTemplate template = mock(ExpenseTemplate.class);
        given(template.getUser()).willReturn(user(999L));
        given(expenseTemplateRepository.findById(5L)).willReturn(Optional.of(template));

        assertThatThrownBy(() -> sut.updateTemplate(5L, USER_ID, updateCmd(30L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("useTemplate — 카테고리가 사후에 부모가 됐으면 거래 생성 불가")
    void useTemplateRejectsNonLeafCategory() {
        User u = user(USER_ID);
        ExpenseCategory nowParent = category(40L, u);
        ExpenseTemplate template = mock(ExpenseTemplate.class);
        given(template.getUser()).willReturn(u);
        given(template.getCategory()).willReturn(nowParent);
        given(expenseTemplateRepository.findById(5L)).willReturn(Optional.of(template));
        given(expenseCategoryRepository.hasChildren(40L)).willReturn(true);

        assertThatThrownBy(() -> sut.useTemplate(5L, USER_ID, LocalDate.of(2026, 6, 1)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("useTemplate — 성공 시 템플릿 값으로 거래 생성 + 사용횟수 증가")
    void useTemplateCreatesExpenseAndIncrementsUseCount() {
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        ExpenseTemplate template = mock(ExpenseTemplate.class);
        given(template.getUser()).willReturn(u);
        given(template.getCategory()).willReturn(leaf);
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(template.getAsset()).willReturn(null);
        given(template.getExpenseType()).willReturn(ExpenseType.EXPENSE);
        given(template.getAmount()).willReturn(15_000L);
        given(template.getDescription()).willReturn("점심");
        given(template.getMerchant()).willReturn("식당");
        given(template.getPaymentMethod()).willReturn("CARD");
        given(expenseTemplateRepository.findById(5L)).willReturn(Optional.of(template));

        ExpenseServiceDto.ExpenseInfo info = sut.useTemplate(5L, USER_ID, LocalDate.of(2026, 7, 1));

        assertThat(info.amount()).isEqualTo(15_000L);
        assertThat(info.description()).isEqualTo("점심");
        verify(expenseRepository).save(org.mockito.ArgumentMatchers.any());
        verify(template).incrementUseCount();
    }
}
