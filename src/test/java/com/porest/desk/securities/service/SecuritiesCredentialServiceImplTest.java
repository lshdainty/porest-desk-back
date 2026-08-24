package com.porest.desk.securities.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.type.YNType;
import com.porest.desk.common.crypto.AesGcmCipher;
import com.porest.desk.securities.client.BrokerTokenManager;
import com.porest.desk.securities.client.BrokerTokenManagers;
import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.repository.UserSecuritiesCredentialRepository;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 증권사 크리덴셜 등록·해제·기본 소스 지정.
 *
 * <p>등록 검증(토큰이 실제로 나오는가)은 {@code BrokerTokenManager} 가 하므로 여기서는
 * <b>그 결과로 무엇이 저장·변경되는가</b>만 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecuritiesCredentialServiceImplTest {

    @Mock private UserSecuritiesCredentialRepository credentialRepository;
    @Mock private AesGcmCipher cipher;
    @Mock private BrokerTokenManagers tokenManagers;
    @Mock private BrokerTokenManager tossManager;
    @Mock private BrokerTokenManager namuManager;
    @InjectMocks private SecuritiesCredentialServiceImpl sut;

    private static final long USER = 1L;

    @BeforeEach
    void setUp() {
        given(tokenManagers.of(SecuritiesBroker.TOSS)).willReturn(tossManager);
        given(tokenManagers.of(SecuritiesBroker.NAMU)).willReturn(namuManager);
        given(cipher.encrypt("key")).willReturn("keyEnc");
        given(cipher.encrypt("secret")).willReturn("secretEnc");
    }

    private static UserSecuritiesCredential cred(SecuritiesBroker broker, boolean primary) {
        UserSecuritiesCredential c =
            UserSecuritiesCredential.verified(USER, broker, "keyEnc", "secretEnc", LocalDateTime.now());
        c.markPrimary(primary);
        return c;
    }

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("검증 통과하면 암호문으로 저장하고 토큰캐시를 비운다")
        void success() {
            given(credentialRepository.findByUserRowIdAndBroker(USER, SecuritiesBroker.NAMU))
                .willReturn(Optional.empty());
            given(credentialRepository.save(any(UserSecuritiesCredential.class)))
                .willAnswer(inv -> inv.getArgument(0));
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of());

            sut.register(USER, SecuritiesBroker.NAMU, "key", "secret");

            verify(namuManager).verify("key", "secret");
            verify(credentialRepository).save(any(UserSecuritiesCredential.class));
            verify(namuManager).invalidate(USER);
        }

        @Test
        @DisplayName("검증이 실패하면 저장하지 않는다 — 저장한 뒤 틀린 키였다고 알면 늦다")
        void invalidCredentialIsNotStored() {
            willThrow(new ExternalServiceException(
                    com.porest.desk.common.exception.DeskErrorCode.SECURITIES_CREDENTIAL_INVALID))
                .given(namuManager).verify("key", "secret");

            assertThatThrownBy(() -> sut.register(USER, SecuritiesBroker.NAMU, "key", "secret"))
                .isInstanceOf(ExternalServiceException.class);

            verify(credentialRepository, never()).save(any());
        }

        @Test
        @DisplayName("첫 연결은 기본 시세 소스가 된다 — 고를 게 하나뿐이라 묻지 않는다")
        void firstConnectionBecomesPrimary() {
            UserSecuritiesCredential saved = cred(SecuritiesBroker.TOSS, false);
            given(credentialRepository.findByUserRowIdAndBroker(USER, SecuritiesBroker.TOSS))
                .willReturn(Optional.empty());
            given(credentialRepository.save(any(UserSecuritiesCredential.class))).willReturn(saved);
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of(saved));

            sut.register(USER, SecuritiesBroker.TOSS, "key", "secret");

            assertThat(saved.isPrimarySource()).isTrue();
        }

        @Test
        @DisplayName("이미 기본 소스가 있으면 두 번째 연결은 기본이 되지 않는다")
        void secondConnectionDoesNotStealPrimary() {
            UserSecuritiesCredential toss = cred(SecuritiesBroker.TOSS, true);
            UserSecuritiesCredential namu = cred(SecuritiesBroker.NAMU, false);
            given(credentialRepository.findByUserRowIdAndBroker(USER, SecuritiesBroker.NAMU))
                .willReturn(Optional.empty());
            given(credentialRepository.save(any(UserSecuritiesCredential.class))).willReturn(namu);
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of(toss, namu));

            sut.register(USER, SecuritiesBroker.NAMU, "key", "secret");

            assertThat(namu.isPrimarySource()).isFalse();
            assertThat(toss.isPrimarySource()).isTrue();
        }
    }

    @Nested
    @DisplayName("해제")
    class Disconnect {

        @Test
        @DisplayName("소프트 삭제하고 토큰캐시를 비운다")
        void softDeletes() {
            UserSecuritiesCredential toss = cred(SecuritiesBroker.TOSS, false);
            given(credentialRepository.findByUserRowIdAndBroker(USER, SecuritiesBroker.TOSS))
                .willReturn(Optional.of(toss));
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of());

            sut.disconnect(USER, SecuritiesBroker.TOSS);

            assertThat(toss.getIsDeleted()).isEqualTo(YNType.Y);
            verify(tossManager).invalidate(USER);
        }

        @Test
        @DisplayName("기본 소스를 끊으면 남은 연결이 승계한다 — 안 그러면 자산 평가가 통째로 멈춘다")
        void primaryIsHandedOver() {
            UserSecuritiesCredential toss = cred(SecuritiesBroker.TOSS, true);
            UserSecuritiesCredential namu = cred(SecuritiesBroker.NAMU, false);
            given(credentialRepository.findByUserRowIdAndBroker(USER, SecuritiesBroker.TOSS))
                .willReturn(Optional.of(toss));
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of(namu));

            sut.disconnect(USER, SecuritiesBroker.TOSS);

            assertThat(namu.isPrimarySource()).isTrue();
        }
    }

    @Nested
    @DisplayName("기본 시세 소스")
    class Primary {

        @Test
        @DisplayName("지정하면 나머지는 해제된다 — 최대 1건 Y 는 DB 가 아니라 서비스가 지킨다")
        void onlyOneStaysPrimary() {
            UserSecuritiesCredential toss = cred(SecuritiesBroker.TOSS, true);
            UserSecuritiesCredential namu = cred(SecuritiesBroker.NAMU, false);
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of(toss, namu));

            sut.setPrimary(USER, SecuritiesBroker.NAMU);

            assertThat(namu.isPrimarySource()).isTrue();
            assertThat(toss.isPrimarySource()).isFalse();
        }

        @Test
        @DisplayName("지정이 없어도 연결이 있으면 하나를 고른다 — 마이그레이션 직후 평가가 멈추지 않게")
        void fallsBackToFirstActive() {
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of(cred(SecuritiesBroker.NAMU, false)));

            assertThat(sut.getPrimaryBroker(USER)).contains(SecuritiesBroker.NAMU);
        }

        @Test
        @DisplayName("연결이 없으면 비어 있다")
        void emptyWhenNothingConnected() {
            given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
                .willReturn(List.of());

            assertThat(sut.getPrimaryBroker(USER)).isEmpty();
        }
    }

    @Test
    @DisplayName("연결 목록은 미연결 증권사도 포함한다 — 화면이 '무엇을 연결할 수 있는지'를 서버에서 받는다")
    void connectionsIncludeUnconnectedBrokers() {
        given(credentialRepository.findAllByUserRowIdAndIsDeletedAndIsVerified(USER, YNType.N, YNType.Y))
            .willReturn(List.of(cred(SecuritiesBroker.TOSS, true)));

        List<BrokerConnection> connections = sut.getConnections(USER);

        assertThat(connections).hasSameSizeAs(SecuritiesBroker.values());
        assertThat(connections).anySatisfy(c -> {
            assertThat(c.broker()).isEqualTo(SecuritiesBroker.NAMU);
            assertThat(c.connected()).isFalse();
            // 미연결이어도 화면이 폼을 그릴 수 있게 라벨·발급처가 실려 온다.
            assertThat(c.keyLabel()).isEqualTo(SecuritiesBroker.NAMU.getKeyLabel());
            assertThat(c.issueUrl()).isEqualTo(SecuritiesBroker.NAMU.getIssueUrl());
        });
    }
}
