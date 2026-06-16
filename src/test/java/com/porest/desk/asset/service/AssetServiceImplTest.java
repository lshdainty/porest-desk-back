package com.porest.desk.asset.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.asset.domain.Asset;
import com.porest.desk.asset.domain.AssetTransfer;
import com.porest.desk.asset.repository.AssetRepository;
import com.porest.desk.asset.repository.AssetTransferRepository;
import com.porest.desk.asset.service.dto.AssetServiceDto;
import com.porest.desk.card.repository.CardCatalogRepository;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 자산 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 자산/이체는 조회·수정·삭제·이체할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceImplTest {

    @Mock private AssetRepository assetRepository;
    @Mock private AssetTransferRepository assetTransferRepository;
    @Mock private UserRepository userRepository;
    @Mock private CardCatalogRepository cardCatalogRepository;
    @Mock private AssetBalanceHistoryService balanceHistoryService;

    @InjectMocks private AssetServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private Asset assetOwnedBy(long ownerRowId) {
        Asset a = mock(Asset.class);
        given(a.getUser()).willReturn(user(ownerRowId));
        return a;
    }

    @Test
    @DisplayName("getAsset — 남의 자산은 조회 불가")
    void getRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.getAsset(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("updateAsset — 남의 자산은 수정 불가")
    void updateRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.updateAsset(5L, USER_ID, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteAsset — 남의 자산은 삭제 불가")
    void deleteRejectsOthers() {
        Asset asset = assetOwnedBy(999L);
        given(assetRepository.findById(5L)).willReturn(Optional.of(asset));

        assertThatThrownBy(() -> sut.deleteAsset(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 출금 자산이 남의 것이면 이체 불가")
    void transferRejectsOthersFromAsset() {
        Asset fromAsset = assetOwnedBy(999L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 입금 자산이 남의 것이면 이체 불가")
    void transferRejectsOthersToAsset() {
        Asset fromAsset = assetOwnedBy(USER_ID);
        Asset toAsset = assetOwnedBy(999L);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));
        given(assetRepository.findById(10L)).willReturn(Optional.of(fromAsset));
        given(assetRepository.findById(11L)).willReturn(Optional.of(toAsset));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 11L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("createTransfer — 같은 자산으로의 이체는 불가")
    void transferRejectsSameAsset() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(USER_ID)));

        var cmd = new AssetServiceDto.CreateTransferCommand(
                USER_ID, 10L, 10L, 50_000L, 0L, "이체", LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> sut.createTransfer(cmd))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    @DisplayName("deleteTransfer — 남의 이체는 삭제 불가")
    void deleteTransferRejectsOthers() {
        AssetTransfer transfer = mock(AssetTransfer.class);
        given(transfer.getUser()).willReturn(user(999L));
        given(assetTransferRepository.findById(7L)).willReturn(Optional.of(transfer));

        assertThatThrownBy(() -> sut.deleteTransfer(7L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }
}
