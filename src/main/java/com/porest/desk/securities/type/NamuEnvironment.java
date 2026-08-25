package com.porest.desk.securities.type;

import java.net.URI;
import java.util.Set;

/**
 * 나무증권(NH PLUG) 연동 환경.
 *
 * <p><b>도메인과 계좌구분은 한 몸이다.</b> 나무는 계좌를 {@code acct_type} 으로 나누고
 * (01·02 운영 · 03 모의투자), <b>그 구분이 그 계좌를 쓸 수 있는 도메인을 결정한다.</b>
 * 운영 도메인에 모의투자 계좌를 보내면 {@code rsp_cd=11165}("계좌번호를 잘못 입력하셨습니다"),
 * 반대로 보내면 {@code 11512}("데이터가 존재하지 않습니다")로 거절당한다 — 실제로 그렇게 터졌다.
 *
 * <p>그래서 도메인과 계좌구분을 <b>따로 설정하지 않는다.</b> 이 enum 하나가 둘 다 들고 있고
 * 설정에는 환경 이름만 둔다({@code app.namu.environment}). 둘을 각각 설정으로 두면 한쪽만
 * 바꾼 순간 같은 사고가 재현되는데, 기동은 멀쩡하고 잔고 조회에서만 조용히 터진다.
 *
 * <p><b>토큰 발급은 여기 없다.</b> {@code /oauth2/token} 은 스펙상 모의투자 미제공이라
 * 환경과 무관하게 항상 운영 도메인에서 받는다 — {@code app.namu.auth-base-url} 참고.
 * 발급받은 토큰은 양쪽 환경에 그대로 쓴다.
 *
 * @see <a href="https://www.nhplug.com/llms-full.txt">NH PLUG OpenAPI 전문</a>
 */
public enum NamuEnvironment {

    /** 운영 — 실계좌. {@code acct_type} 01(일반)·02(주문대리인). */
    LIVE("https://api.nhplug.com:8443", Set.of("01", "02")),

    /** 모의투자 — 교육이수·개발검증용. {@code acct_type} 03. */
    MOCK("https://moapi.nhplug.com:8443", Set.of("03"));

    private final String defaultBaseUrl;
    private final Set<String> accountTypes;

    NamuEnvironment(String defaultBaseUrl, Set<String> accountTypes) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.accountTypes = accountTypes;
    }

    /** 이 환경의 조회 base URL. 포트(8443)까지 포함한다. */
    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    /** 이 환경에서 조회에 쓸 수 있는 {@code acct_type} 집합. */
    public Set<String> getAccountTypes() {
        return accountTypes;
    }

    /** 이 계좌구분을 이 환경에서 쓸 수 있는가. 값이 비면 알 수 없으므로 거절한다. */
    public boolean accepts(String accountType) {
        return accountType != null && accountTypes.contains(accountType.trim());
    }

    /**
     * base URL 의 호스트로 환경을 되짚는다. 나무 호스트가 아니면(로컬 스텁·프록시) {@code null}.
     *
     * <p>설정한 환경과 실제 도메인이 어긋났는지 기동 시 검사하는 데 쓴다.
     */
    public static NamuEnvironment ofBaseUrl(String baseUrl) {
        String host = host(baseUrl);
        if (host == null) {
            return null;
        }
        for (NamuEnvironment env : values()) {
            if (host.equalsIgnoreCase(host(env.defaultBaseUrl))) {
                return env;
            }
        }
        return null;
    }

    /** URL 의 호스트. 파싱이 안 되면 null — 검사 자체를 건너뛰게 한다(기동을 막지 않는다). */
    public static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
