package com.porest.desk.securities.service;

import com.porest.desk.securities.service.dto.SecuritiesCredentialServiceDto.BrokerConnection;
import com.porest.desk.securities.type.SecuritiesBroker;

import java.util.List;
import java.util.Optional;

/**
 * 사용자별 증권사 크리덴셜 등록/조회/해제, 그리고 <b>기본 시세 소스</b> 지정.
 *
 * <p>키는 등록 시 해당 증권사 토큰발급으로 즉시 검증한 뒤 AES-GCM 으로 암호화해 저장한다.
 * 평문·암호문은 어떤 응답에도 나가지 않는다.
 */
public interface SecuritiesCredentialService {

    /** 등록(또는 재등록). 검증 실패 시 {@code SECURITIES_CREDENTIAL_INVALID}. */
    void register(Long userRowId, SecuritiesBroker broker, String apiKey, String apiSecret);

    /** 전 증권사 연결 상태 — 미연결 증권사도 포함한다. 시크릿 미반환. */
    List<BrokerConnection> getConnections(Long userRowId);

    /** 연결 해제(소프트 삭제) + 토큰캐시 무효화. 기본 소스였다면 남은 연결로 승계한다. */
    void disconnect(Long userRowId, SecuritiesBroker broker);

    /** 기본 시세 소스 지정 — 가계부 자산 평가가 이 증권사 시세를 쓴다. */
    void setPrimary(Long userRowId, SecuritiesBroker broker);

    /** 자산 평가에 쓸 증권사. 연결이 하나도 없으면 비어 있다. */
    Optional<SecuritiesBroker> getPrimaryBroker(Long userRowId);

    /** 해당 증권사가 사용 가능한 상태인가. */
    boolean isConnected(Long userRowId, SecuritiesBroker broker);

    /** 증권사를 가리지 않고 하나라도 연결돼 있는가 — 기능 게이트·메뉴 노출 판정용. */
    boolean hasAnyConnection(Long userRowId);
}
