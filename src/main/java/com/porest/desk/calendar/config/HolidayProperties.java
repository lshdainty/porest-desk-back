package com.porest.desk.calendar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 공휴일 자동 동기화 설정. {@code app.holiday.*} 프로퍼티를 바인딩한다.
 *
 * @see <a href="https://www.data.go.kr/data/15012690/openapi.do">한국천문연구원_특일 정보</a>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.holiday")
public class HolidayProperties {

    private Kasi kasi = new Kasi();
    private Fallback fallback = new Fallback();
    private Sync sync = new Sync();
    private Backfill backfill = new Backfill();

    /** 한국천문연구원 특일 정보 API(공공데이터포털). 1차 소스. */
    @Getter
    @Setter
    public static class Kasi {
        /** 특일정보 서비스 base URL */
        private String baseUrl = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";

        /**
         * 공공데이터포털 인증키.
         * <p><b>디코딩 키</b>를 넣는다. 포털이 함께 주는 인코딩 키(%2B·%3D 가 섞인 값)를 넣으면
         * 요청 시 이중 인코딩돼 인증에 실패한다.
         */
        private String serviceKey;

        private int connectTimeout = 5000;
        private int readTimeout = 10000;

        /** 인증키가 주입됐는지. 미설정이면 KASI 호출을 건너뛰고 폴백만 쓴다. */
        public boolean isConfigured() {
            return serviceKey != null && !serviceKey.isBlank();
        }
    }

    /** 우주항공청 월력요항 기반 정적 데이터(holidays-kr). KASI 실패 시 폴백. */
    @Getter
    @Setter
    public static class Fallback {
        /** 폴백 사용 여부 */
        private boolean enabled = true;

        /** 연도별 JSON 을 내려주는 base URL ({baseUrl}/{year}.json) */
        private String baseUrl = "https://holidays.hyunbin.page";

        private int connectTimeout = 5000;
        private int readTimeout = 10000;
    }

    /** 정기 동기화. */
    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = true;

        /**
         * 동기화 주기. 기본 매일 12:00.
         * <p>임시공휴일은 지정에서 시행까지 2주 남짓인 사례가 있어(2025-01-27) 연 1회로는 못 따라간다.
         * 하루 1회여도 조회 연도 수만큼(기본 2콜) 쓰므로 개발계정 일 10,000건 한도에 한참 못 미친다.
         */
        private String cron = "0 0 12 * * *";

        /** 매 동기화에서 당해 연도 기준 몇 년 뒤까지 볼지. 기본 1 = 당해 + 익년. */
        private int lookaheadYears = 1;
    }

    /** 기동 시 1회 과거 연도 백필. 초기 적재용이라 평소에는 꺼 둔다. */
    @Getter
    @Setter
    public static class Backfill {
        private boolean enabled = false;

        /** 백필 시작 연도. 소스에 데이터가 없는 연도는 0건으로 조용히 넘어간다. */
        private int startYear = 2004;
    }
}
