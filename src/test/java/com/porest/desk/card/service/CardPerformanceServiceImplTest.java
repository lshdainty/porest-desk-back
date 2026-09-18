package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.type.YNType;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.card.domain.CardCatalog;
import com.porest.desk.card.service.dto.CardPerformanceServiceDto;
import com.porest.desk.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 카드 전월 실적 계산 로직 단위 테스트.
 *
 * <p>DB·컨텍스트 없이 {@link CardPerformanceServiceImpl} 의 실적 달성률·달성 여부·잔여 금액
 * 계산과 소유권 가드만 검증한다. 월 지출 합계는 {@link EntityManager} 쿼리 체인을 mock 으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class CardPerformanceServiceImplTest {

    @Mock private AssetRepository assetRepository;
    @Mock private EntityManager entityManager;
    // 실적도 "지금까지" 만 센다(D1) — 시각을 고정해야 상한이 결정된다.
    // 날짜 의존 테스트는 now()±N일 금지(메모리 desk-card-billing-money-cap).
    @Spy private UserClock userClock =
        new UserClock(rowId -> null, new ServiceClock("Asia/Seoul"));

    @InjectMocks private CardPerformanceServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long ASSET_ID = 10L;
    private static final YearMonth YM = YearMonth.of(2026, 6);
    /** YM 달 중간 — 말일·1일 경계에서 흔들리지 않게 고정한다. */
    private static final java.time.LocalDateTime NOW =
        java.time.LocalDate.of(2026, 6, 15).atTime(9, 0);

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private CardPerformanceServiceDto.PerformanceQuery query() {
        return new CardPerformanceServiceDto.PerformanceQuery(USER_ID, ASSET_ID, YM);
    }

    /** getUser=소유자, getCardCatalog=전달값 만 stub 한 Asset mock. */
    private Asset assetWithCatalog(CardCatalog catalog) {
        Asset asset = mock(Asset.class);
        given(asset.getUser()).willReturn(user(USER_ID));
        given(asset.getCardCatalog()).willReturn(catalog);
        return asset;
    }

    /** 월 지출 합계 쿼리(sumExpenseAmount) 결과를 sum 으로 고정한다. */
    private void givenNow() {
        org.mockito.Mockito.doReturn(NOW).when(userClock).now(USER_ID);
    }

    private void givenMonthlyExpenseSum(long sum) {
        givenNow();
        @SuppressWarnings("unchecked")
        TypedQuery<Long> typedQuery = mock(TypedQuery.class);
        given(entityManager.createQuery(anyString(), eq(Long.class))).willReturn(typedQuery);
        given(typedQuery.setParameter(anyString(), any())).willReturn(typedQuery);
        given(typedQuery.getSingleResult()).willReturn(sum);
    }

    private CardCatalog requiredCatalog(int requiredAmount, String requiredText) {
        CardCatalog catalog = mock(CardCatalog.class);
        given(catalog.getPerformanceIsRequired()).willReturn(YNType.Y);
        given(catalog.getPerformanceRequiredAmount()).willReturn(requiredAmount);
        given(catalog.getPerformanceRequiredText()).willReturn(requiredText);
        return catalog;
    }

    @Nested
    @DisplayName("getPerformance")
    class GetPerformance {

        @Test
        @DisplayName("실적 미달 — 달성률<1, 미달성, 잔여=필요-현재")
        void notAchievedWhenBelowRequired() {
            Asset asset = assetWithCatalog(requiredCatalog(300_000, "전월 30만원 이상"));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            givenMonthlyExpenseSum(200_000L);

            var info = sut.getPerformance(query());

            assertThat(info.assetRowId()).isEqualTo(ASSET_ID);
            assertThat(info.yearMonth()).isEqualTo(YM);
            assertThat(info.requiredAmount()).isEqualTo(300_000);
            assertThat(info.requiredText()).isEqualTo("전월 30만원 이상");
            assertThat(info.isRequired()).isTrue();
            assertThat(info.currentAmount()).isEqualTo(200_000L);
            assertThat(info.achievementRate()).isCloseTo(200_000.0 / 300_000.0, within(1e-9));
            assertThat(info.isAchieved()).isFalse();
            assertThat(info.remainingAmount()).isEqualTo(100_000L);
        }

        @Test
        @DisplayName("실적 초과 — 달성률은 1.0 으로 상한, 잔여=0")
        void achievedAndRateCappedWhenOverRequired() {
            Asset asset = assetWithCatalog(requiredCatalog(300_000, "전월 30만원 이상"));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            givenMonthlyExpenseSum(350_000L);

            var info = sut.getPerformance(query());

            assertThat(info.currentAmount()).isEqualTo(350_000L);
            assertThat(info.achievementRate()).isEqualTo(1.0);
            assertThat(info.isAchieved()).isTrue();
            assertThat(info.remainingAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("실적 정확히 충족(경계) — 달성, 달성률 1.0, 잔여 0")
        void achievedWhenExactlyEqualsRequired() {
            Asset asset = assetWithCatalog(requiredCatalog(300_000, "전월 30만원 이상"));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            givenMonthlyExpenseSum(300_000L);

            var info = sut.getPerformance(query());

            assertThat(info.currentAmount()).isEqualTo(300_000L);
            assertThat(info.achievementRate()).isEqualTo(1.0);
            assertThat(info.isAchieved()).isTrue();
            assertThat(info.remainingAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("실적 조건 없는 카드 — isRequired=false, 달성률 1.0, 항상 달성, 잔여 0")
        void notRequiredCatalog() {
            CardCatalog catalog = mock(CardCatalog.class);
            given(catalog.getPerformanceIsRequired()).willReturn(YNType.N);
            Asset asset = assetWithCatalog(catalog);
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
            givenMonthlyExpenseSum(50_000L);

            var info = sut.getPerformance(query());

            assertThat(info.isRequired()).isFalse();
            assertThat(info.requiredAmount()).isEqualTo(0);
            assertThat(info.requiredText()).isNull();
            assertThat(info.currentAmount()).isEqualTo(50_000L);
            assertThat(info.achievementRate()).isEqualTo(1.0);
            assertThat(info.isAchieved()).isTrue();
            assertThat(info.remainingAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("카탈로그 연결 없는 자산 — notApplicable 로 조회 없이 반환")
        void notApplicableWhenNoCatalog() {
            Asset asset = assetWithCatalog(null);
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));

            var info = sut.getPerformance(query());

            assertThat(info.assetRowId()).isEqualTo(ASSET_ID);
            assertThat(info.yearMonth()).isEqualTo(YM);
            assertThat(info.isRequired()).isFalse();
            assertThat(info.requiredAmount()).isEqualTo(0);
            assertThat(info.requiredText()).isNull();
            assertThat(info.currentAmount()).isEqualTo(0L);
            assertThat(info.achievementRate()).isEqualTo(0.0);
            assertThat(info.isAchieved()).isTrue();
            assertThat(info.remainingAmount()).isEqualTo(0L);
        }

        @Test
        @DisplayName("존재하지 않는 자산 — EntityNotFoundException")
        void throwsWhenAssetNotFound() {
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getPerformance(query()))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("타 사용자 자산 — ForbiddenException")
        void throwsWhenNotOwner() {
            Asset asset = mock(Asset.class);
            given(asset.getUser()).willReturn(user(999L));
            given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));

            assertThatThrownBy(() -> sut.getPerformance(query()))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    /**
     * 실적에서 환불·취소를 <b>뺀다</b>.
     *
     * <p>종전엔 지출만 더했다. 산 것을 되돌려도 실적이 그대로 남아 "이번 달 실적 달성" 이
     * 켜졌고, 사용자는 그 표시를 믿고 어느 카드를 쓸지 정한다. 같은 화면의 청구 예정액은
     * 처음부터 환불을 빼고 있어 두 숫자가 서로 어긋나 있었다(2026-09-18 사용자 제보).
     *
     * <p>합계 자체는 mock 이라 값으로는 못 본다 — 그래서 <b>질의가 무엇을 세는지</b>를 본다.
     * 되돌아가면(=`SUM(e.amount)` 로) 여기서 걸린다.
     */
    @Test
    @DisplayName("실적 합계는 환불을 빼고 센다 — 청구 예정액과 같은 규칙")
    void performanceNetsRefunds() {
        Asset asset = assetWithCatalog(requiredCatalog(300_000, "30만원"));
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));

        @SuppressWarnings("unchecked")
        TypedQuery<Long> typedQuery = mock(TypedQuery.class);
        org.mockito.ArgumentCaptor<String> jpql = org.mockito.ArgumentCaptor.forClass(String.class);
        given(entityManager.createQuery(jpql.capture(), eq(Long.class))).willReturn(typedQuery);
        given(typedQuery.setParameter(anyString(), any())).willReturn(typedQuery);
        given(typedQuery.getSingleResult()).willReturn(100_000L);

        sut.getPerformance(new CardPerformanceServiceDto.PerformanceQuery(USER_ID, ASSET_ID, YM));

        String q = jpql.getValue().replaceAll("\\s+", " ");
        assertThat(q)
                .as("지출만 더하고 있으면(SUM(e.amount)) 환불이 안 빠진다")
                .contains("CASE WHEN e.expenseType = :expenseType THEN e.amount ELSE -e.amount END");
        assertThat(q)
                .as("타입으로 걸러 버리면 환불 행이 아예 안 보인다")
                .doesNotContain("AND e.expenseType = :expenseType");
        assertThat(q)
                .as("할부는 실적에서 뺀다(D2)")
                .contains("(e.installmentMonths IS NULL OR e.installmentMonths <= 1)");
    }

    /**
     * 실적도 <b>아직 오지 않은 거래를 세지 않는다</b>(D1).
     *
     * <p>실적은 예측이 아니라 <b>달성도</b>다. 반복 거래가 미리 만들어 둔 거래로 "이번 달
     * 실적 달성" 이 먼저 켜지면 사용자는 없는 혜택을 믿는다.
     *
     * <p>합계가 mock 이라 상한 파라미터를 본다 — 달 말일이 아니라 지금이어야 한다.
     */
    @Test
    @DisplayName("실적은 지금까지만 센다 — 상한이 달 말일이 아니다")
    void performanceCountsUntilNow() {
        Asset asset = assetWithCatalog(requiredCatalog(300_000, "30만원"));
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
        givenNow();

        @SuppressWarnings("unchecked")
        TypedQuery<Long> typedQuery = mock(TypedQuery.class);
        org.mockito.ArgumentCaptor<Object> values = org.mockito.ArgumentCaptor.forClass(Object.class);
        given(entityManager.createQuery(anyString(), eq(Long.class))).willReturn(typedQuery);
        given(typedQuery.setParameter(anyString(), values.capture())).willReturn(typedQuery);
        given(typedQuery.getSingleResult()).willReturn(100_000L);

        sut.getPerformance(new CardPerformanceServiceDto.PerformanceQuery(USER_ID, ASSET_ID, YM));

        assertThat(values.getAllValues())
                .as("상한이 지금(%s)이어야 한다 — 달 말일이면 미래 거래까지 센다", NOW)
                .contains(NOW)
                .doesNotContain(YM.atEndOfMonth().atTime(java.time.LocalTime.MAX));
    }

    /** 조회한 달이 통째로 미래면 셀 것이 없다 — 질의를 하지 않는다. */
    @Test
    @DisplayName("통째로 미래인 달은 0 이다")
    void futureMonthIsZero() {
        Asset asset = assetWithCatalog(requiredCatalog(300_000, "30만원"));
        given(assetRepository.findById(ASSET_ID)).willReturn(Optional.of(asset));
        org.mockito.Mockito.doReturn(NOW).when(userClock).now(USER_ID);

        CardPerformanceServiceDto.PerformanceInfo info = sut.getPerformance(
                new CardPerformanceServiceDto.PerformanceQuery(USER_ID, ASSET_ID, YearMonth.of(2026, 12)));

        assertThat(info.currentAmount()).isZero();
        assertThat(info.isAchieved()).isFalse();
    }
}
