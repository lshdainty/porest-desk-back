package com.porest.desk.dataimport.sms.service;

import java.util.List;

/**
 * 결제 문자를 보낸 카드사(발신 기관).
 *
 * <p>문자 본문에 카드사 이름이 어떻게 적히는지는 회사·상품마다 흔들린다
 * ("KB국민카드", "국민카드", "KB카드"). 그래서 표시명 하나로 매칭하지 않고
 * 별칭 목록을 둔다 — 별칭이 본문에 포함되면 그 카드사로 본다.
 *
 * <p>별칭은 <b>긴 것부터</b> 검사한다. "NH농협카드" 를 "농협" 으로 먼저 잡으면
 * 표시명이 갈리진 않지만, "KB국민" 과 "국민" 처럼 한쪽이 다른 쪽의 부분 문자열인
 * 조합에서 잘못된 카드사로 새는 경우가 생긴다.
 *
 * <p>여기 없는 카드사의 문자는 파싱 자체는 되지만({@code null} issuer) 신뢰도가 내려가
 * 사용자 확인을 거친다. 목록을 늘리는 것으로 대응한다 — 서버 배포만으로 끝난다.
 */
public enum SmsCardIssuer {
    KB("KB국민카드", List.of("KB국민카드", "국민카드", "KB카드", "KB국민")),
    SHINHAN("신한카드", List.of("신한카드", "신한체크", "신한")),
    SAMSUNG("삼성카드", List.of("삼성카드", "삼성체크", "삼성")),
    HYUNDAI("현대카드", List.of("현대카드", "현대체크", "현대")),
    LOTTE("롯데카드", List.of("롯데카드", "롯데체크", "롯데")),
    WOORI("우리카드", List.of("우리카드", "우리체크", "우리BC", "우리")),
    HANA("하나카드", List.of("하나카드", "하나체크", "하나")),
    NH("NH농협카드", List.of("NH농협카드", "농협카드", "NH채움", "NH농협", "농협")),
    BC("BC카드", List.of("BC카드", "비씨카드", "BC글로벌", "비씨")),
    CITI("씨티카드", List.of("씨티카드", "citi카드", "씨티")),
    KAKAO("카카오뱅크", List.of("카카오뱅크", "카카오페이", "카뱅")),
    TOSS("토스뱅크", List.of("토스뱅크", "토스페이", "토스"));

    private final String displayName;
    private final List<String> aliases;

    SmsCardIssuer(String displayName, List<String> aliases) {
        this.displayName = displayName;
        this.aliases = aliases;
    }

    public String displayName() {
        return displayName;
    }

    public List<String> aliases() {
        return aliases;
    }

    /**
     * 본문에서 카드사를 찾는다 — 못 찾으면 null.
     *
     * <p>별칭이 긴 것부터 전 카드사를 훑는다. enum 선언 순서대로 각자의 별칭을 보면
     * 앞선 카드사의 짧은 별칭이 뒤 카드사의 긴 별칭을 가로챈다
     * (예: "우리" 가 "우리BC" 보다 먼저 걸리는 문제는 없지만,
     * 다른 카드사끼리는 선언 순서에 결과가 좌우된다).
     */
    public static SmsCardIssuer detect(String text) {
        if (text == null || text.isBlank()) return null;

        SmsCardIssuer best = null;
        int bestLength = 0;
        for (SmsCardIssuer issuer : values()) {
            for (String alias : issuer.aliases) {
                if (alias.length() > bestLength && containsIgnoreCase(text, alias)) {
                    best = issuer;
                    bestLength = alias.length();
                }
            }
        }
        return best;
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        return text.toLowerCase().contains(keyword.toLowerCase());
    }
}
