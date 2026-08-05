package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.core.type.YNType;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    @DisplayName("createTemplate — 고정 금액을 안 쓰면 금액 없이도 저장된다")
    void createAllowsBlankAmountWhenNotLocked() {
        // 프리셋은 금액을 모르는 채로 양식만 저장하려고 만든 것이다.
        // 매번 금액이 다른 항목(구독료 변동·병원비 등)은 불러올 때 비어 있어야 편하다.
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(expenseTemplateRepository.save(any(ExpenseTemplate.class)))
            .willAnswer(inv -> inv.getArgument(0));

        var cmd = new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "구독", 10L, null, ExpenseType.EXPENSE, null,
                null, null, null, null, YNType.N);

        assertThatCode(() -> sut.createTemplate(cmd)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("createTemplate — 고정 금액을 쓰면 금액이 있어야 한다")
    void createRequiresAmountWhenLocked() {
        // 고정 금액은 불러오는 거래가 그 값을 그대로 받는다 — 비어 있으면 의미가 없다.
        // 금액 검증이 사용자 조회보다 먼저라 저장소를 건드리지 않는다.
        var cmd = new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "점심", 10L, null, ExpenseType.EXPENSE, null,
                null, null, null, null, YNType.Y);

        assertThatThrownBy(() -> sut.createTemplate(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createTemplate — 고정 금액이 꺼져 있으면 적어 넣은 금액은 버린다")
    void createDropsAmountWhenNotLocked() {
        // 둘 중 하나다 — 고정이면 금액이 있고, 아니면 없다. 중간 상태를 남기면
        // 화면엔 안 보이는 값이 붙어 다니다 고정을 켜는 순간 엉뚱한 금액이 살아난다.
        User u = user(USER_ID);
        ExpenseCategory leaf = category(10L, u);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(leaf));
        given(expenseCategoryRepository.hasChildren(10L)).willReturn(false);
        given(expenseTemplateRepository.save(any(ExpenseTemplate.class)))
            .willAnswer(inv -> inv.getArgument(0));

        var cmd = new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "구독", 10L, null, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null, YNType.N);

        var info = sut.createTemplate(cmd);

        assertThat(info.amount()).isNull();
    }

    @Test
    @DisplayName("createTemplate — 고정 금액을 켰는데 0 이면 거부한다")
    void createRejectsZeroWhenLocked() {
        var cmd = new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "점심", 10L, null, ExpenseType.EXPENSE, 0L,
                null, null, null, null, YNType.Y);

        assertThatThrownBy(() -> sut.createTemplate(cmd))
                .isInstanceOf(InvalidValueException.class);
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
    @DisplayName("updateTemplate — 남의 자산으로 변경 불가(소유권 검증 누락 보강)")
    void updateRejectsOthersAsset() {
        User u = user(USER_ID);
        ExpenseTemplate template = mock(ExpenseTemplate.class);
        given(template.getUser()).willReturn(u);
        given(expenseTemplateRepository.findById(5L)).willReturn(Optional.of(template));
        Asset othersAsset = mock(Asset.class);
        given(othersAsset.getUser()).willReturn(user(999L));
        given(assetRepository.findById(20L)).willReturn(Optional.of(othersAsset));

        var cmd = new ExpenseTemplateServiceDto.UpdateCommand(
                "점심 템플릿", null, 20L, ExpenseType.EXPENSE, 10_000L,
                null, null, null, null);

        assertThatThrownBy(() -> sut.updateTemplate(5L, USER_ID, cmd))
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

    @Nested
    @DisplayName("금액 부호 — 음수 프리셋이 저장되면 그걸 불러 쓰는 거래도 오염된다")
    class AmountSign {

        @Test
        @DisplayName("음수 금액 프리셋을 만들 수 없다")
        void rejectsNegative() {
            var cmd = new ExpenseTemplateServiceDto.CreateCommand(
                USER_ID, "잘못된 프리셋", 1L, null, ExpenseType.EXPENSE, -10_000L,
                null, null, null, null, YNType.Y);
            assertThatThrownBy(() -> sut.createTemplate(cmd))
                .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("수정에서도 음수를 막는다")
        void rejectsNegativeOnUpdate() {
            var cmd = new ExpenseTemplateServiceDto.UpdateCommand(
                "프리셋", 1L, null, ExpenseType.EXPENSE, -5_000L,
                null, null, null, YNType.Y);
            assertThatThrownBy(() -> sut.updateTemplate(1L, USER_ID, cmd))
                .isInstanceOf(InvalidValueException.class);
        }
    }
}
