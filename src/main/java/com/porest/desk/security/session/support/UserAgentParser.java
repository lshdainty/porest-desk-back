package com.porest.desk.security.session.support;

/**
 * User-Agent 를 "로그인된 기기" 목록에 쓸 짧은 이름으로 줄인다.
 *
 * <p>예전에는 UA 원문을 200자로 자르기만 했다. 사용자에게
 * {@code Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 ...} 를
 * 보여주는 셈이라, 내 기기인지 남의 기기인지 알아볼 수가 없었다.
 *
 * <p>결과는 {@code 기기 · 브라우저} 형태다 — {@code iPhone · Safari}, {@code Windows · Chrome}.
 * 둘 중 하나만 알아내면 그것만 쓴다.
 *
 * <p><b>라이브러리를 쓰지 않는 이유.</b> ua-parser 류는 정규식 수백 개를 들고 오는데,
 * 우리가 필요한 건 우리 사용자가 실제로 쓰는 몇 가지뿐이다. 못 알아본 UA 는
 * {@code null} 로 두고 화면에서 "알 수 없는 기기" 로 표시하면 되므로, 커버리지를
 * 넓히려고 의존성을 늘릴 이유가 없다.
 *
 * <p><b>순서가 중요하다.</b> UA 문자열은 호환성 때문에 서로를 포함한다 —
 * Edge 는 {@code Chrome} 을, Chrome 은 {@code Safari} 를 UA 에 함께 넣는다.
 * 좁은 것부터 본다.
 */
public final class UserAgentParser {

    private UserAgentParser() {
    }

    /** DB 컬럼(device_label)이 200이지만 이 형식은 훨씬 짧다. 방어적으로만 둔다. */
    private static final int MAX = 60;

    /**
     * @param userAgent 요청 헤더 원문. {@code null} · 공백 허용
     * @return {@code iPhone · Safari} 같은 짧은 이름, 못 알아보면 {@code null}
     */
    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        // 한 번만 소문자로 만들어 두고 그걸로 본다 — 매 비교마다 만들면 낭비다.
        String ua = userAgent.trim().toLowerCase();

        String device = device(ua);
        String client = client(ua);

        String label;
        if (device != null && client != null) {
            label = device + " · " + client;
        } else if (device != null) {
            label = device;
        } else {
            label = client;
        }
        if (label == null) {
            return null;
        }
        return label.length() > MAX ? label.substring(0, MAX) : label;
    }

    /** 기기·운영체제. 인자는 소문자로 정규화된 UA 다. */
    private static String device(String ua) {
        // iPad 가 iPhone 보다 앞이어야 한다 — iPadOS UA 가 둘 다 담는 경우가 있다.
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("android")) return "Android";
        if (ua.contains("windows")) return "Windows";
        // "mac os x" 는 iOS UA 에도 들어간다 — 위에서 iPhone·iPad 를 먼저 걸러야 한다.
        if (ua.contains("macintosh") || ua.contains("mac os x")) return "Mac";
        if (ua.contains("cros")) return "ChromeOS";
        if (ua.contains("linux")) return "Linux";
        return null;
    }

    /**
     * 브라우저 또는 앱.
     *
     * <p>Flutter 앱은 User-Agent 를 따로 세팅하지 않아 {@code dart:io} 기본값
     * ({@code Dart/3.9 (dart:io)})이 나간다. 그래서 앱 세션은 기기를 알 수 없고
     * "Porest 앱" 으로만 보인다 — 앱이 제대로 된 UA 를 보내면 그때 기기까지 나온다.
     */
    private static String client(String ua) {
        if (ua.contains("dart/") || ua.contains("dart:io")) return "Porest 앱";
        if (ua.contains("edg/") || ua.contains("edge/")) return "Edge";
        if (ua.contains("whale/")) return "Whale";
        if (ua.contains("samsungbrowser/")) return "Samsung Internet";
        if (ua.contains("opr/") || ua.contains("opera")) return "Opera";
        if (ua.contains("fxios/") || ua.contains("firefox/")) return "Firefox";
        // Chrome UA 는 Safari 를 함께 담지만 그 반대는 아니다. Chrome 을 먼저 본다.
        if (ua.contains("crios/") || ua.contains("chrome/")) return "Chrome";
        if (ua.contains("safari/")) return "Safari";
        return null;
    }
}
