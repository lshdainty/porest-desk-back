package com.porest.desk.card.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.service.AssetService;
import com.porest.desk.asset.type.AssetType;
import com.porest.desk.card.repository.CardBillingRepository;
import com.porest.desk.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 카드 결제 서비스 회귀 방지 단위 테스트 — 소유권 / 신용카드 검증 / 결제 자산 필수.
 */
@ExtendWith(MockitoExtension.class)
class CardPaymentServiceImplTest {

    @Mock private CardBillingRepository cardBillingRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private AssetService assetService;
    @Mock private EntityManager entityManager;

    @InjectMocks private CardPaymentServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    @Test
    @DisplayName("getCardBilling — 남의 카드는 조회 불가")
    void getBillingRejectsOthers() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(999L));
        given(assetRepository.findById(5L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.getCardBilling(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("payCard — 신용카드가 아니면 결제 불가")
    void payRejectsNonCreditCard() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(USER_ID));
        given(card.getAssetType()).willReturn(AssetType.BANK_ACCOUNT);
        given(assetRepository.findById(5L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.payCard(5L, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("payCard — 결제 자산이 없으면 결제 불가")
    void payRejectsWhenNoPaymentAsset() {
        Asset card = mock(Asset.class);
        given(card.getUser()).willReturn(user(USER_ID));
        given(card.getAssetType()).willReturn(AssetType.CREDIT_CARD);
        given(card.getPaymentAsset()).willReturn(null);
        given(assetRepository.findById(5L)).willReturn(Optional.of(card));

        assertThatThrownBy(() -> sut.payCard(5L, USER_ID))
                .isInstanceOf(InvalidValueException.class);
    }
}
