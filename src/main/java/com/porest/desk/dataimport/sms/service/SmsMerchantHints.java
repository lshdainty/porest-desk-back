package com.porest.desk.dataimport.sms.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 가맹점명 → 카테고리 이름 힌트 사전.
 *
 * <p>추론의 <b>2순위</b>다. 1순위는 같은 가맹점의 내 지난 거래 카테고리 —
 * 같은 스타벅스라도 누구는 "카페", 누구는 "간식" 에 넣고 그 답이 늘 옳다.
 * 이 사전은 그 이력이 아직 없을 때만 쓴다.
 *
 * <p>여기서 나온 이름으로 카테고리를 <b>만들지는 않는다</b>. 이름이 같은 기존
 * 카테고리를 찾아 붙일 뿐이고, 없으면 비워 사용자가 고르게 한다.
 * 미리보기(parse)가 데이터를 만들면 확인만 하고 닫아도 찌꺼기가 남는다.
 *
 * <p>넓히기보다 정확도를 우선한다 — 틀린 카테고리가 자동으로 붙으면
 * 사용자는 매번 고쳐야 하고, 그건 비어 있는 것보다 나쁘다.
 */
public final class SmsMerchantHints {

    private SmsMerchantHints() {
    }

    /**
     * 가맹점 부분 문자열 → 카테고리 이름 후보(앞이 우선).
     *
     * <p>후보를 여러 개 두는 이유 — 사용자마다 쓰는 이름이 다르다("카페" vs "커피").
     * 앞에서부터 <b>기존에 있는 카테고리</b>를 찾아 첫 히트를 쓴다.
     */
    private static final Map<String, List<String>> HINTS = new LinkedHashMap<>();

    static {
        // 카페 — 상호에 지점명이 붙어 오므로 브랜드 토큰만 본다.
        put(List.of("스타벅스", "STARBUCKS", "투썸", "이디야", "메가커피", "메가엠지씨",
            "빽다방", "커피빈", "폴바셋", "블루보틀", "할리스", "탐앤탐스", "컴포즈"),
            List.of("카페", "커피", "간식"));
        // 편의점
        put(List.of("GS25", "CU ", "씨유", "세븐일레븐", "7-ELEVEN", "이마트24", "미니스톱"),
            List.of("편의점", "생활", "식비"));
        // 마트·생필품
        put(List.of("이마트", "홈플러스", "롯데마트", "코스트코", "COSTCO", "하나로마트", "노브랜드"),
            List.of("마트", "장보기", "생활"));
        // 배달·외식 플랫폼
        put(List.of("배달의민족", "배민", "요기요", "쿠팡이츠", "땡겨요"),
            List.of("배달", "외식", "식비"));
        // 교통
        put(List.of("카카오택시", "카카오T", "카카오 T", "티머니", "TMONEY", "지하철", "버스",
            "코레일", "SRT", "택시"),
            List.of("교통", "교통비"));
        // 주유
        put(List.of("SK에너지", "GS칼텍스", "S-OIL", "에쓰오일", "현대오일뱅크", "주유소"),
            List.of("주유", "차량", "교통"));
        // 온라인 쇼핑
        put(List.of("쿠팡", "COUPANG", "네이버페이", "11번가", "G마켓", "지마켓", "옥션",
            "SSG", "무신사", "올리브영", "다이소"),
            List.of("쇼핑", "생활"));
        // 문화·구독
        put(List.of("CGV", "롯데시네마", "메가박스", "넷플릭스", "NETFLIX", "왓챠", "스포티파이",
            "SPOTIFY", "유튜브", "YOUTUBE", "애플", "APPLE.COM", "구글", "GOOGLE"),
            List.of("문화생활", "구독", "여가"));
        // 의료
        put(List.of("약국", "의원", "병원", "치과", "한의원", "메디컬"),
            List.of("의료", "병원", "건강"));
        // 통신
        put(List.of("SKT", "KT", "LGU+", "LG유플러스", "알뜰폰"),
            List.of("통신", "통신비"));
    }

    private static void put(List<String> merchantTokens, List<String> categoryNames) {
        for (String token : merchantTokens) {
            HINTS.put(token.toLowerCase(Locale.ROOT), categoryNames);
        }
    }

    /**
     * 가맹점명에 맞는 카테고리 이름 후보 — 없으면 빈 목록.
     *
     * <p>가장 <b>긴</b> 토큰이 이긴다. "GS25" 와 "GS칼텍스" 처럼 앞부분을 공유하는
     * 브랜드가 있어 짧은 토큰을 먼저 채택하면 편의점 결제가 주유로 새어 나간다.
     */
    public static List<String> categoryNamesFor(String merchant) {
        if (merchant == null || merchant.isBlank()) return List.of();
        String lower = merchant.toLowerCase(Locale.ROOT);

        List<String> best = List.of();
        int bestLength = 0;
        for (Map.Entry<String, List<String>> entry : HINTS.entrySet()) {
            String token = entry.getKey();
            if (token.length() > bestLength && lower.contains(token.trim())) {
                best = entry.getValue();
                bestLength = token.length();
            }
        }
        return best;
    }
}
