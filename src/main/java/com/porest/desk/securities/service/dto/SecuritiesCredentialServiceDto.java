package com.porest.desk.securities.service.dto;

import com.porest.desk.securities.domain.UserSecuritiesCredential;
import com.porest.desk.securities.type.SecuritiesBroker;

import java.time.LocalDateTime;

public final class SecuritiesCredentialServiceDto {

    private SecuritiesCredentialServiceDto() {
    }

    /**
     * 증권사 한 곳의 연결 상태. <b>미연결 증권사도 포함해</b> 전체 목록으로 내려간다 —
     * 화면이 "무엇을 연결할 수 있는지" 를 서버에서 받아 그리게 하려는 것이다.
     * 증권사가 늘어도 앱·웹 배포 없이 목록에 나타난다.
     *
     * <p>표시명·발급처·키 라벨을 같이 싣는 이유 — 같은 자리를 회사마다 다르게 부른다
     * (토스 Client ID / 나무 App Key). 화면에 이름을 박아 두면 사용자가 자기 발급 화면에
     * 없는 이름을 찾게 된다.
     */
    public record BrokerConnection(
        SecuritiesBroker broker,
        String displayName,
        String issueUrl,
        String keyLabel,
        String secretLabel,
        boolean connected,
        boolean verified,
        LocalDateTime verifiedAt,
        boolean primary
    ) {
        /** 아직 키를 등록하지 않은 증권사. */
        public static BrokerConnection notConnected(SecuritiesBroker broker) {
            return new BrokerConnection(broker, broker.getDisplayName(), broker.getIssueUrl(),
                broker.getKeyLabel(), broker.getSecretLabel(), false, false, null, false);
        }

        public static BrokerConnection of(UserSecuritiesCredential cred) {
            SecuritiesBroker broker = cred.getBroker();
            return new BrokerConnection(broker, broker.getDisplayName(), broker.getIssueUrl(),
                broker.getKeyLabel(), broker.getSecretLabel(),
                true, true, cred.getVerifiedAt(), cred.isPrimarySource());
        }
    }
}
