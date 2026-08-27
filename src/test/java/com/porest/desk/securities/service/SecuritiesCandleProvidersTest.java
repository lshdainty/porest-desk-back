package com.porest.desk.securities.service;

import com.porest.core.exception.ExternalServiceException;
import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.securities.service.dto.CandlePage;
import com.porest.desk.securities.service.dto.CandleQuery;
import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 캔들 제공자 고르기.
 *
 * <p>시세({@link SecuritiesPriceProviders})와 <b>규칙이 다르다</b> — 기본 소스가 캔들을
 * 못 주면 연결된 다른 증권사로 넘어간다. 차트는 계좌와 무관한 시장 데이터라 어디서 받아도
 * 같은 봉이고, 여기서 기본 소스를 고집하면 <b>증권사를 잘못 골랐다는 이유만으로</b>
 * 차트가 통째로 사라진다.
 *
 * <p>거절이 둘인 것도 여기서 지킨다. "연결이 없다"(403)와 "연결은 있는데 캔들이 없다"(409)를
 * 한 코드로 뭉치면 <b>이미 연결한 사용자에게 "연결하세요" 가 뜬다</b> — 고칠 방법이 없는 안내다.
 */
@ExtendWith(MockitoExtension.class)
class SecuritiesCandleProvidersTest {

    private static final long USER = 1L;

    @Mock private SecuritiesCredentialService credentialService;

    private static SecuritiesCandleProvider providerOf(SecuritiesBroker broker) {
        return new SecuritiesCandleProvider() {
            @Override
            public SecuritiesBroker broker() {
                return broker;
            }

            @Override
            public CandlePage getCandles(Long userRowId, CandleQuery query) {
                return CandlePage.empty();
            }
        };
    }

    private static BrokerConnection connection(SecuritiesBroker broker, boolean connected, boolean primary) {
        return new BrokerConnection(broker, broker.getDisplayName(), broker.getIssueUrl(),
            broker.getKeyLabel(), broker.getSecretLabel(), connected, connected, null, primary);
    }

    private SecuritiesCandleProviders sut(SecuritiesCandleProvider... providers) {
        return new SecuritiesCandleProviders(List.of(providers), credentialService);
    }

    @Test
    @DisplayName("기본 소스가 캔들을 주면 그걸 쓴다")
    void primaryWins() {
        given(credentialService.getConnections(USER)).willReturn(List.of(
            connection(SecuritiesBroker.TOSS, true, false),
            connection(SecuritiesBroker.NAMU, true, true)));

        SecuritiesCandleProviders sut = sut(
            providerOf(SecuritiesBroker.TOSS), providerOf(SecuritiesBroker.NAMU));

        assertThat(sut.forUser(USER).broker()).isEqualTo(SecuritiesBroker.NAMU);
    }

    @Test
    @DisplayName("기본 소스가 캔들을 안 주면 연결된 다른 증권사로 넘어간다 — 차트는 계좌와 무관하다")
    void fallsBackToAnotherConnectedBroker() {
        given(credentialService.getConnections(USER)).willReturn(List.of(
            connection(SecuritiesBroker.TOSS, true, false),
            connection(SecuritiesBroker.NAMU, true, true)));

        // 나무가 기본 소스지만 캔들 제공자는 토스뿐이다.
        SecuritiesCandleProviders sut = sut(providerOf(SecuritiesBroker.TOSS));

        assertThat(sut.forUser(USER).broker()).isEqualTo(SecuritiesBroker.TOSS);
    }

    @Test
    @DisplayName("연결하지 않은 증권사로는 넘어가지 않는다 — 남의 키가 없으면 어차피 실패한다")
    void ignoresDisconnectedBrokers() {
        given(credentialService.getConnections(USER)).willReturn(List.of(
            connection(SecuritiesBroker.TOSS, false, false),
            connection(SecuritiesBroker.NAMU, true, true)));

        SecuritiesCandleProviders sut = sut(providerOf(SecuritiesBroker.TOSS));

        assertThatThrownBy(() -> sut.forUser(USER))
            .isInstanceOf(InvalidValueException.class)
            .extracting(e -> ((InvalidValueException) e).getErrorCode())
            .isEqualTo(DeskErrorCode.SECURITIES_CANDLE_UNSUPPORTED);
    }

    @Test
    @DisplayName("연결이 하나도 없으면 '연결하세요'(403) — 캔들 미지원(409)과 뜻이 다르다")
    void noConnectionIsADifferentError() {
        given(credentialService.getConnections(USER)).willReturn(List.of(
            connection(SecuritiesBroker.TOSS, false, false),
            connection(SecuritiesBroker.NAMU, false, false)));

        SecuritiesCandleProviders sut = sut(providerOf(SecuritiesBroker.TOSS));

        assertThatThrownBy(() -> sut.forUser(USER))
            .isInstanceOf(ExternalServiceException.class)
            .extracting(e -> ((ExternalServiceException) e).getErrorCode())
            .isEqualTo(DeskErrorCode.SECURITIES_CREDENTIAL_REQUIRED);
    }

    @Test
    @DisplayName("기본 소스가 지정되지 않았어도 연결된 곳에서 받는다")
    void worksWithoutPrimary() {
        given(credentialService.getConnections(USER)).willReturn(List.of(
            connection(SecuritiesBroker.TOSS, true, false)));

        assertThat(sut(providerOf(SecuritiesBroker.TOSS)).forUser(USER).broker())
            .isEqualTo(SecuritiesBroker.TOSS);
    }

    @Test
    @DisplayName("전 증권사 커버리지를 강제하지 않는다 — 미지원은 정상이고, 기동은 로그만 남긴다")
    void startupDoesNotRequireEveryBroker() {
        SecuritiesCandleProviders sut = sut(providerOf(SecuritiesBroker.TOSS));

        // 시세 쪽 verifyEveryBrokerCovered() 와 달리 여기서는 터지지 않아야 한다.
        sut.logCandleCoverage();
    }
}
