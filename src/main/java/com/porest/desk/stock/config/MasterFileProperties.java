package com.porest.desk.stock.config;

import com.porest.desk.stock.type.MasterSource;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 종목 마스터파일 동기화 설정. {@code app.stock.master.*} 를 바인딩한다.
 *
 * <p>마스터파일은 두 소스 모두 인증 없는 공개 다운로드라 API 키가 필요 없다.
 * 소스가 늘어도 {@code base-url} 에 항목 하나만 더하면 된다 — 소스마다 설정 클래스를
 * 만들면 타임아웃·스케줄 같은 공통값이 갈라진다.
 *
 * @see <a href="https://github.com/koreainvestment/open-trading-api/tree/main/stocks_info">KIS 종목정보 파일 명세</a>
 * @see <a href="https://www.nhplug.com/llms.txt">NH PLUG 종목마스터 안내</a>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.stock.master")
public class MasterFileProperties {

    /** 소스별 다운로드 base URL ({baseUrl}/{파일명}) */
    private Map<MasterSource, String> baseUrl = new EnumMap<>(MasterSource.class);

    private int connectTimeout = 5000;

    /** zip·고정폭 파일이 최대 수 MB 라 다운로드 여유를 둔다. */
    private int readTimeout = 30000;

    private Sync sync = new Sync();
    private Backfill backfill = new Backfill();

    /** 해당 소스의 base URL. 미설정이면 null — 호출부가 그 소스를 건너뛴다. */
    public String baseUrlOf(MasterSource source) {
        String url = baseUrl.get(source);
        return url == null || url.isBlank() ? null : url;
    }

    /** 정기 동기화. */
    @Getter
    @Setter
    public static class Sync {
        private boolean enabled = true;

        /**
         * 동기화 주기. 기본 매일 07:00.
         * <p>신규상장·상장폐지 반영이 목적이라 장중 갱신이 필요 없다. 미국장 마감(한국 새벽)과
         * 국내장 개장 사이에 돌려 하루치 변경을 아침에 한 번에 반영한다.
         */
        private String cron = "0 0 7 * * *";
    }

    /** 기동 시 1회 초기 적재. */
    @Getter
    @Setter
    public static class Backfill {
        /**
         * 마스터 테이블이 비어 있을 때만 기동 시 적재한다. 공개 파일 다운로드가 전부라
         * 공휴일 백필과 달리 외부 API 한도 걱정이 없어 기본으로 켠다.
         */
        private boolean enabled = true;
    }
}
