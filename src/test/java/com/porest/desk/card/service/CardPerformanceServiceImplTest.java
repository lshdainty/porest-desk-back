package com.porest.desk.card.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.core.exception.ForbiddenException;
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

    @InjectMocks private CardPerformanceServiceImpl sut;

    private static final long USER_ID = 1L;
    private static final long ASSET_ID = 10L;
    private static final YearMonth YM = YearMonth.of(2026, 6);

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
    private void givenMonthlyExpenseSum(long sum) {
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
}
