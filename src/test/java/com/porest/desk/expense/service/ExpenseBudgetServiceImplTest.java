package com.porest.desk.expense.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.expense.domain.ExpenseBudget;
import com.porest.desk.expense.domain.ExpenseCategory;
import com.porest.desk.expense.repository.ExpenseBudgetRepository;
import com.porest.desk.expense.repository.ExpenseCategoryRepository;
import com.porest.desk.expense.repository.ExpenseRepository;
import com.porest.desk.expense.service.dto.ExpenseBudgetServiceDto;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;

/**
 * 예산 정책 회귀 방지 단위 테스트 — 예산은 최상위(부모) 카테고리에만 설정 가능.
 */
@ExtendWith(MockitoExtension.class)
class ExpenseBudgetServiceImplTest {

    @Mock private ExpenseBudgetRepository expenseBudgetRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private UserRepository userRepository;
    // 날짜 판정용 — mock 이면 null 이 흘러 NPE. 실물을 주입하되 사용자 조회는 비어
    // 서비스 기준(Asia/Seoul)으로 폴백한다.
    @Spy private UserClock userClock = new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));
    // createBudget 은 시도마다 새 트랜잭션을 연다(TransactionTemplate). mock 트랜잭션 매니저는
    // getTransaction 이 null 을 주고 commit 이 no-op 이라, 템플릿은 콜백만 그대로 실행한다.
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private ExpenseBudgetServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private ExpenseCategory category(long rowId, User owner, ExpenseCategory parent) {
        ExpenseCategory c = ExpenseCategory.createCategory(owner, "식비", "tag", "#fff", ExpenseType.EXPENSE, parent);
        ReflectionTestUtils.setField(c, "rowId", rowId);
        return c;
    }

    private ExpenseBudgetServiceDto.CreateCommand command(Long categoryRowId) {
        return new ExpenseBudgetServiceDto.CreateCommand(USER_ID, categoryRowId, 300_000L, 2026, 6);
    }

    @Test
    @DisplayName("최상위 카테고리에는 예산을 설정할 수 있다")
    void createOnRootCategory() {
        User u = user(USER_ID);
        ExpenseCategory root = category(10L, u, null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(root));

        var info = sut.createBudget(command(10L));

        assertThat(info.categoryRowId()).isEqualTo(10L);
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("자식(하위) 카테고리에는 예산을 설정할 수 없다")
    void rejectOnChildCategory() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u, null);
        ExpenseCategory child = category(11L, u, parent);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(11L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> sut.createBudget(command(11L)))
                .isInstanceOf(InvalidValueException.class);
        verify(expenseBudgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("남의 카테고리에는 예산을 설정할 수 없다")
    void rejectOnOthersCategory() {
        User u = user(USER_ID);
        User other = user(999L);
        ExpenseCategory othersCategory = category(20L, other, null);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(20L)).willReturn(Optional.of(othersCategory));

        assertThatThrownBy(() -> sut.createBudget(command(20L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("카테고리 없는 전체 예산(category=null)은 설정할 수 있다")
    void createOverallBudget() {
        User u = user(USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));

        var info = sut.createBudget(command(null));

        assertThat(info.categoryRowId()).isNull();
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("createBudget — 0 이하 금액은 불가(스케줄러 0 나눗셈 방지)")
    void createRejectsNonPositiveAmount() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new ExpenseBudgetServiceDto.CreateCommand(USER_ID, null, 0L, 2026, 6);

        assertThatThrownBy(() -> sut.createBudget(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("updateBudget — 0 이하 금액은 불가")
    void updateRejectsNonPositiveAmount() {
        ExpenseBudget budget = mock(ExpenseBudget.class);
        given(budget.getUser()).willReturn(user(USER_ID));
        given(expenseBudgetRepository.findById(5L)).willReturn(Optional.of(budget));

        assertThatThrownBy(() -> sut.updateBudget(5L, USER_ID,
                new ExpenseBudgetServiceDto.UpdateCommand(0L)))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("createBudget — 100억 초과는 불가(스케줄러·가져오기처럼 컨트롤러를 안 거치는 경로 방어)")
    void createRejectsOverLimit() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new ExpenseBudgetServiceDto.CreateCommand(USER_ID, null, 10_000_000_001L, 2026, 6);

        assertThatThrownBy(() -> sut.createBudget(cmd))
                .isInstanceOf(InvalidValueException.class);
        verify(expenseBudgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBudget — 정확히 100억(경계)은 통과")
    void createAcceptsAmountAtLimit() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var info = sut.createBudget(
                new ExpenseBudgetServiceDto.CreateCommand(USER_ID, null, 10_000_000_000L, 2026, 6));

        assertThat(info.budgetAmount()).isEqualTo(10_000_000_000L);
    }

    @Test
    @DisplayName("updateBudget — 100억 초과는 불가")
    void updateRejectsOverLimit() {
        ExpenseBudget budget = mock(ExpenseBudget.class);
        given(budget.getUser()).willReturn(user(USER_ID));
        given(expenseBudgetRepository.findById(5L)).willReturn(Optional.of(budget));

        assertThatThrownBy(() -> sut.updateBudget(5L, USER_ID,
                new ExpenseBudgetServiceDto.UpdateCommand(10_000_000_001L)))
                .isInstanceOf(InvalidValueException.class);
    }

    // ── QA 2026-09-03 #77 ① : 같은 (사용자 · 카테고리 · 연월) 에는 행이 하나 ──────────────

    private ExpenseBudget existingBudget(long rowId, User owner, ExpenseCategory category, long amount) {
        ExpenseBudget b = ExpenseBudget.createBudget(owner, category, amount, 2026, 6);
        ReflectionTestUtils.setField(b, "rowId", rowId);
        return b;
    }

    @Test
    @DisplayName("같은 달 같은 카테고리에 두 번 등록하면 새 행을 만들지 않고 기존 행 금액을 고친다")
    void createUpdatesExistingRowInsteadOfInserting() {
        User u = user(USER_ID);
        ExpenseCategory root = category(10L, u, null);
        ExpenseBudget already = existingBudget(77L, u, root, 100_000L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(10L)).willReturn(Optional.of(root));
        given(expenseBudgetRepository.findByUserAndCategory(USER_ID, 10L, 2026, 6))
                .willReturn(Optional.of(already));

        var info = sut.createBudget(command(10L));

        // save 가 아니라 더티 체킹 — 조회한 엔티티의 필드가 바뀌어야 한다(merge 금지, QA #78 ④).
        verify(expenseBudgetRepository, never()).save(any());
        assertThat(already.getBudgetAmount()).isEqualTo(300_000L);
        // 응답은 생성과 같은 모양이고, 행 아이디는 원래 있던 그 행이다.
        assertThat(info.rowId()).isEqualTo(77L);
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("전체 예산(categoryRowId=null)도 두 번 등록하면 기존 행을 고친다 — null 을 `= null` 로 비교하면 여기서 샌다")
    void createUpdatesExistingOverallBudget() {
        User u = user(USER_ID);
        ExpenseBudget already = existingBudget(78L, u, null, 100_000L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseBudgetRepository.findByUserAndCategory(USER_ID, null, 2026, 6))
                .willReturn(Optional.of(already));

        var info = sut.createBudget(command(null));

        verify(expenseBudgetRepository, never()).save(any());
        assertThat(already.getBudgetAmount()).isEqualTo(300_000L);
        assertThat(info.rowId()).isEqualTo(78L);
        assertThat(info.categoryRowId()).isNull();
    }

    @Test
    @DisplayName("조회 키는 커맨드의 categoryRowId 를 그대로 넘긴다 — 전체 예산이면 null 그대로")
    void lookupKeyKeepsNullCategory() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        sut.createBudget(command(null));

        verify(expenseBudgetRepository).findByUserAndCategory(USER_ID, null, 2026, 6);
        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("지운 예산이 있어도 조회가 비면 새로 만든다 — 예산에는 삭제 플래그가 없어 행이 통째로 사라진다")
    void createsNewRowWhenNothingFound() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(expenseBudgetRepository.findByUserAndCategory(USER_ID, null, 2026, 6))
                .willReturn(Optional.empty());

        var info = sut.createBudget(command(null));

        verify(expenseBudgetRepository).save(any(ExpenseBudget.class));
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("동시 저장 경쟁 — UK 위반이 나면 새 트랜잭션으로 재조회해 상대 행을 고친다")
    void recoversFromConcurrentInsert() {
        User u = user(USER_ID);
        ExpenseBudget winner = existingBudget(99L, u, null, 100_000L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        // 1회차: 아직 아무도 안 넣었다 → INSERT 시도 → 상대가 먼저 커밋해 UK 위반.
        // 2회차(새 트랜잭션): 상대 행이 보인다.
        given(expenseBudgetRepository.findByUserAndCategory(USER_ID, null, 2026, 6))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));
        given(expenseBudgetRepository.save(any(ExpenseBudget.class)))
                .willThrow(new DataIntegrityViolationException("uk_expense_budget"));

        var info = sut.createBudget(command(null));

        // 500 이 아니라 200 + 행이고, 그 행은 상대가 넣은 행이다.
        assertThat(info.rowId()).isEqualTo(99L);
        assertThat(info.budgetAmount()).isEqualTo(300_000L);
        assertThat(winner.getBudgetAmount()).isEqualTo(300_000L);
        verify(expenseBudgetRepository, times(2)).findByUserAndCategory(USER_ID, null, 2026, 6);
        // 두 번째 시도는 수정 경로다 — INSERT 를 다시 시도하지 않는다.
        verify(expenseBudgetRepository, times(1)).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("재시도는 1회 — 두 번째도 UK 위반이면 그대로 올린다(무한 루프 금지)")
    void retriesOnlyOnce() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(expenseBudgetRepository.findByUserAndCategory(USER_ID, null, 2026, 6))
                .willReturn(Optional.empty());
        given(expenseBudgetRepository.save(any(ExpenseBudget.class)))
                .willThrow(new DataIntegrityViolationException("uk_expense_budget"));

        assertThatThrownBy(() -> sut.createBudget(command(null)))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(expenseBudgetRepository, times(2)).save(any(ExpenseBudget.class));
    }

    @Test
    @DisplayName("기존 행이 있어도 검증은 그대로 — 자식 카테고리는 여전히 거절한다")
    void upsertStillValidatesCategoryPolicy() {
        User u = user(USER_ID);
        ExpenseCategory parent = category(10L, u, null);
        ExpenseCategory child = category(11L, u, parent);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(u));
        given(expenseCategoryRepository.findById(11L)).willReturn(Optional.of(child));

        assertThatThrownBy(() -> sut.createBudget(command(11L)))
                .isInstanceOf(InvalidValueException.class);
        // 검증이 조회보다 먼저다 — 잘못된 요청으로 DB 를 긁지 않는다.
        verify(expenseBudgetRepository, never()).findByUserAndCategory(any(), any(), any(), any());
    }
}
