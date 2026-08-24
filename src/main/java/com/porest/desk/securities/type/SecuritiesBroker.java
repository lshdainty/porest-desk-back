package com.porest.desk.securities.type;

import com.porest.core.exception.InvalidValueException;
import com.porest.desk.common.exception.DeskErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * 연동 가능한 증권사.
 *
 * <p><b>증권사별로 다른 것은 코드가 아니라 여기 데이터로 둔다.</b> 표시명·발급처·키 라벨을
 * 상수로 들고 있으면 화면은 목록을 렌더만 하면 되고, 증권사를 늘리는 일이 <b>여기 한 줄 +
 * 서버 배포</b>로 끝난다. {@code SmsCardIssuer} 가 카드사를 다루는 방식과 같다.
 *
 * <p>키 라벨이 왜 데이터인가 — 같은 자리에 들어가는 값을 회사마다 다르게 부른다.
 * 토스는 {@code client_id}/{@code client_secret}, 나무는 {@code appkey}/{@code appsecretkey} 다.
 * 화면에 "Client ID" 라고 박아 두면 나무 사용자는 자기 발급 화면에 없는 이름을 찾게 된다.
 *
 * <p>프로토콜 차이(폼 vs 쿼리, GET vs POST, 봉투 모양)는 데이터로 담을 수 없다 —
 * 그건 {@code client} 패키지의 브로커별 구현이 맡는다.
 */
@Getter
@RequiredArgsConstructor
public enum SecuritiesBroker {

    TOSS("토스증권", "https://developers.tossinvest.com", "Client ID", "Client Secret"),
    NAMU("나무증권", "https://www.nhplug.com", "App Key", "App Secret");

    private final String displayName;
    /** 사용자가 본인 키를 발급받는 곳. 화면이 안내 링크로 쓴다. */
    private final String issueUrl;
    /** 입력 폼 첫째 칸 라벨 — 발급 화면에 적힌 이름 그대로여야 사용자가 찾는다. */
    private final String keyLabel;
    /** 입력 폼 둘째 칸 라벨. */
    private final String secretLabel;

    /**
     * 코드 문자열 → 증권사. 대소문자를 가리지 않는다.
     *
     * <p>모르는 코드는 {@code SECURITIES_BROKER_UNSUPPORTED} 로 거절한다 — 조용히 기본값으로
     * 떨어뜨리면 엉뚱한 증권사 키로 등록된다.
     */
    public static SecuritiesBroker from(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_BROKER_UNSUPPORTED);
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidValueException(DeskErrorCode.SECURITIES_BROKER_UNSUPPORTED, e);
        }
    }
}
