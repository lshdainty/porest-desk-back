package com.porest.desk.security.session.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.porest.desk.security.session.support.UserAgentParser.DeviceKind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "로그인된 기기" 목록에 뜰 이름이라, 실제 UA 문자열로 건다.
 *
 * <p>UA 는 호환성 때문에 서로를 포함한다 — Edge 가 Chrome 을, Chrome 이 Safari 를 담는다.
 * 순서를 잘못 두면 전부 Safari 로 보인다. 그래서 "무엇으로 안 보이는지" 도 함께 확인한다.
 */
class UserAgentParserTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        // ── 데스크톱 브라우저
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 | Windows · Chrome",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 Edg/126.0 | Windows · Edge",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15 | Mac · Safari",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:127.0) Gecko/20100101 Firefox/127.0 | Windows · Firefox",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Whale/3.26 Safari/537.36 | Windows · Whale",

        // ── 모바일 브라우저
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1 | iPhone · Safari",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/126.0 Mobile/15E148 Safari/604.1 | iPhone · Chrome",
        "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/604.1 | iPad · Safari",
        "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/25.0 Chrome/121.0 Mobile Safari/537.36 | Android · Samsung Internet",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36 | Android · Chrome",

        // ── 앱이 보내는 형태
        "Porest/1.2.3 (Android 14) | Android · Porest 앱",
        "Porest/1.2.3 (iOS 17.5) | iOS · Porest 앱",
        // 앱이 UA 를 안 보내던 시절의 dart:io 기본값 — 옛 세션이 남아 있다
        "Dart/3.9 (dart:io) | Porest 앱",
    })
    @DisplayName("실제 UA 를 사람이 읽을 이름으로 줄인다")
    void parses(String ua, String expected) {
        assertThat(UserAgentParser.parse(ua)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Edge 를 Chrome 으로 보지 않는다 — Edge UA 가 Chrome 을 함께 담는다")
    void edgeIsNotChrome() {
        String edge = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/126.0 Safari/537.36 Edg/126.0";
        assertThat(UserAgentParser.parse(edge)).doesNotContain("Chrome");
    }

    @Test
    @DisplayName("iPhone 을 Mac 으로 보지 않는다 — iOS UA 에 'like Mac OS X' 가 들어간다")
    void iphoneIsNotMac() {
        String iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1";
        assertThat(UserAgentParser.parse(iphone)).startsWith("iPhone");
    }

    @Test
    @DisplayName("Android 를 Linux 로 보지 않는다 — Android UA 가 'Linux' 를 담는다")
    void androidIsNotLinux() {
        String android = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36";
        assertThat(UserAgentParser.parse(android)).startsWith("Android");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curl/8.4.0", "PostmanRuntime/7.39.0"})
    @DisplayName("못 알아보면 null — 화면에서 '알 수 없는 기기' 로 표시한다")
    void unknownIsNull(String ua) {
        assertThat(UserAgentParser.parse(ua)).isNull();
    }

    @Test
    @DisplayName("iOS 크롬을 앱으로 보지 않는다 — CriOS 에 'ios' 가 들어간다")
    void criosIsNotApp() {
        String crios = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) CriOS/126.0 Mobile Safari/604.1";
        assertThat(UserAgentParser.parse(crios)).isEqualTo("iPhone · Chrome");
    }

    @Test
    @DisplayName("한쪽만 알아내면 그것만 쓴다")
    void partial() {
        assertThat(UserAgentParser.parse("Mozilla/5.0 (Windows NT 10.0)")).isEqualTo("Windows");
    }

    // ── 기기 형태(아이콘용) ──────────────────────────────────────────────

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
        "iPhone · Safari      | MOBILE",
        "Android · Chrome     | MOBILE",
        "iOS · Porest 앱      | MOBILE",
        "Android · Porest 앱  | MOBILE",
        "iPad · Safari        | TABLET",
        "Windows · Chrome     | DESKTOP",
        "Mac · Safari         | DESKTOP",
        "ChromeOS · Chrome    | DESKTOP",
        "Linux · Firefox      | DESKTOP",
        // 기기 없이 클라이언트만 알아낸 이름 — 형태를 알 수 없다
        "Chrome               | UNKNOWN",
        "Porest 앱            | UNKNOWN",
    })
    @DisplayName("기기 이름에서 형태를 되짚는다 — 화면이 이름을 다시 뜯지 않게")
    void kindOf_derivesFromLabel(String label, DeviceKind expected) {
        assertThat(UserAgentParser.kindOf(label.trim())).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("이름이 없으면 UNKNOWN — 못 알아본 UA 로 만들어진 세션")
    void kindOf_noLabel_isUnknown(String label) {
        assertThat(UserAgentParser.kindOf(label)).isEqualTo(DeviceKind.UNKNOWN);
    }

    @Test
    @DisplayName("parse 가 만든 이름을 kindOf 가 되짚는다 — 두 함수가 같은 구분자를 본다")
    void kindOf_roundTripsParseOutput() {
        String ua = "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 Version/17.5 Safari/604.1";
        // 이 왕복이 깨지면 아이콘이 전부 '알 수 없음' 으로 바뀐다 — 구분자를 상수로 묶은 이유다.
        assertThat(UserAgentParser.kindOf(UserAgentParser.parse(ua))).isEqualTo(DeviceKind.TABLET);
    }
}
