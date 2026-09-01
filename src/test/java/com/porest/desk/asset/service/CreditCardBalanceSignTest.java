package com.porest.desk.asset.service;

import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetBalanceHistory;
import com.porest.desk.asset.repository.AssetBalanceHistoryRepository;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.expense.type.ExpenseType;
import com.porest.desk.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;

/**
 * 신용카드 잔액 부호 규약 — <b>미결제 사용액은 음수</b>.
 *
 * <p>잔액을 움직이는 경로가 이미 전부 이 규약을 따른다: 결제 −amount / 환불 +amount /
 * 대금 상환 +원금 → 0 으로 수렴. 유일하게 규약을 안 지키던 게 사용자가 직접 넣는 절대
 * 잔액이었다. 화면이 "현재 사용액"을 묻고 사용자는 양수를 치므로 저장할 때 뒤집는다.
 *
 * <p>부호가 섞이면 조용히 어긋난다 — 순자산은 {@code Math.abs()} 로 더해 우연히 맞고,
 * 부호를 그대로 더하는 화면 합계에서만 틀린 값이 나온다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("신용카드 잔액 부호 규약")
class CreditCardBalanceSignTest {

    @Mock private AssetBalanceHistoryRepository repository;
    @Mock private com.porest.core.time.UserClock userClock;
    @Mock private com.porest.desk.asset.repository.AssetRepository assetRepository;

    @InjectMocks private AssetBalanceHistoryService sut;

    private User user;

    private static final LocalDateTime AT = LocalDateTime.of(2026, 8, 5, 12, 0);

    @BeforeEach
    void setUp() {
        user = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(user, "rowId", 1L);
    }

    private Asset asset(AssetType type, Long initial) {
        Asset a = Asset.createAsset(user, "카드", type, initial, "KRW",
            null, null, null, null, 0, YNType.Y, null, null, null, null);
        ReflectionTestUtils.setField(a, "rowId", 9L);
        return a;
    }

    private long savedAmount() {
        ArgumentCaptor<AssetBalanceHistory> c = ArgumentCaptor.forClass(AssetBalanceHistory.class);
        then(repository).should().save(c.capture());
        return c.getValue().getAmount();
    }

    @Nested
    @DisplayName("신용카드 — 사용액은 음수로 저장된다")
    class CreditCard {

        @Test
        @DisplayName("사용액 356,800 을 양수로 입력해도 −356,800 으로 저장")
        void positiveInputBecomesNegative() {
            sut.recordManual(asset(AssetType.CREDIT_CARD, 0L), 356_800L, AT);
            assertThat(savedAmount()).isEqualTo(-356_800L);
        }

        @Test
        @DisplayName("음수로 입력하면 그대로 −356,800")
        void negativeInputStays() {
            sut.recordManual(asset(AssetType.CREDIT_CARD, 0L), -356_800L, AT);
            assertThat(savedAmount()).isEqualTo(-356_800L);
        }

        @Test
        @DisplayName("자산 등록 시 초기 사용액도 음수로")
        void initNormalized() {
            sut.recordInit(asset(AssetType.CREDIT_CARD, 100_000_000L), AT);
            assertThat(savedAmount()).isEqualTo(-100_000_000L);
        }

        @Test
        @DisplayName("사용액 0 은 그대로 0 — −0 이 되지 않는다")
        void zeroStaysZero() {
            sut.recordManual(asset(AssetType.CREDIT_CARD, 0L), 0L, AT);
            assertThat(savedAmount()).isZero();
        }
    }

    @Nested
    @DisplayName("대출 — 잔액(빚)은 음수로 저장된다")
    class Loan {

        @Test
        @DisplayName("대출 잔액 5,000,000 을 양수로 입력해도 −5,000,000 으로 저장")
        void initNormalized() {
            sut.recordInit(asset(AssetType.LOAN, 5_000_000L), AT);
            assertThat(savedAmount()).isEqualTo(-5_000_000L);
        }

        @Test
        @DisplayName("수동 잔액 수정도 음수로 — 상환 이체(+원금)가 빚을 줄이는 방향이 된다")
        void manualNormalized() {
            sut.recordManual(asset(AssetType.LOAN, 0L), 4_500_000L, AT);
            assertThat(savedAmount()).isEqualTo(-4_500_000L);
        }

        @Test
        @DisplayName("음수로 입력하면 그대로")
        void negativeInputStays() {
            sut.recordManual(asset(AssetType.LOAN, 0L), -4_500_000L, AT);
            assertThat(savedAmount()).isEqualTo(-4_500_000L);
        }
    }

    @Nested
    @DisplayName("INIT 앵커 시각 — 분 시작으로 내린다")
    class InitAnchorMinute {

        @Test
        @DisplayName("21:47:37.386 에 만들어도 앵커는 21:47:00.0 — 같은 분의 거래(:00)가 빠지지 않는다")
        void initEffectiveTruncatedToMinute() {
            sut.recordInit(asset(AssetType.BANK_ACCOUNT, 500_000L),
                LocalDateTime.of(2026, 9, 1, 21, 47, 37, 386_000_000));
            ArgumentCaptor<AssetBalanceHistory> c = ArgumentCaptor.forClass(AssetBalanceHistory.class);
            then(repository).should().save(c.capture());
            assertThat(c.getValue().getEffectiveAt())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 21, 47, 0, 0));
        }

        @Test
        @DisplayName("MANUAL 앵커는 내리지 않는다 — 같은 분의 앞선 거래를 이중 계상하지 않기 위해")
        void manualEffectiveKept() {
            LocalDateTime at = LocalDateTime.of(2026, 9, 1, 21, 47, 37, 386_000_000);
            sut.recordManual(asset(AssetType.BANK_ACCOUNT, 0L), 1_000_000L, at);
            ArgumentCaptor<AssetBalanceHistory> c = ArgumentCaptor.forClass(AssetBalanceHistory.class);
            then(repository).should().save(c.capture());
            assertThat(c.getValue().getEffectiveAt()).isEqualTo(at);
        }
    }

    @Nested
    @DisplayName("다른 자산은 부호를 건드리지 않는다")
    class OtherTypes {

        @Test
        @DisplayName("통장 — 입력한 그대로")
        void bankAccount() {
            sut.recordManual(asset(AssetType.BANK_ACCOUNT, 0L), 2_500_000L, AT);
            assertThat(savedAmount()).isEqualTo(2_500_000L);
        }

        @Test
        @DisplayName("마이너스 통장 — 음수도 그대로")
        void negativeBankAccount() {
            sut.recordManual(asset(AssetType.BANK_ACCOUNT, 0L), -300_000L, AT);
            assertThat(savedAmount()).isEqualTo(-300_000L);
        }

        @Test
        @DisplayName("대출 — 음수 그대로 (부호를 강제하지 않는다)")
        void loan() {
            sut.recordManual(asset(AssetType.LOAN, 0L), -85_000_000L, AT);
            assertThat(savedAmount()).isEqualTo(-85_000_000L);
        }

        @Test
        @DisplayName("체크카드 — 자체 잔액이 없어 규약 대상이 아니다")
        void checkCard() {
            sut.recordManual(asset(AssetType.CHECK_CARD, 0L), 50_000L, AT);
            assertThat(savedAmount()).isEqualTo(50_000L);
        }
    }

    @Nested
    @DisplayName("거래 flow 는 종전대로 — 규약이 이미 지켜지고 있다")
    class Flows {

        @Test
        @DisplayName("카드로 결제하면 −amount")
        void spendIsNegative() {
            sut.recordExpense(asset(AssetType.CREDIT_CARD, 0L), 100L,
                ExpenseType.EXPENSE, 356_800L, AT);
            assertThat(savedAmount()).isEqualTo(-356_800L);
        }

        @Test
        @DisplayName("환불(INCOME)은 +amount — 부채가 줄어든다")
        void refundIsPositive() {
            sut.recordExpense(asset(AssetType.CREDIT_CARD, 0L), 100L,
                ExpenseType.INCOME, 50_000L, AT);
            assertThat(savedAmount()).isEqualTo(50_000L);
        }
    }
}
