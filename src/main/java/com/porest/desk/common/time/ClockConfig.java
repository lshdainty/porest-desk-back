package com.porest.desk.common.time;

import com.porest.core.time.ServiceClock;
import com.porest.core.time.UserClock;
import com.porest.core.time.UserZoneProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * core 시각 유틸 빈 등록.
 *
 * <p>core 클래스에는 Spring 어노테이션이 없다(컴포넌트 스캔 범위에 {@code com.porest.core} 가
 * 들어오는지에 의존하지 않기 위해). 그래서 사용하는 쪽에서 명시 등록한다.
 */
@Configuration
public class ClockConfig {

    /** 배치·스케줄러가 쓰는 서비스 운영 기준 타임존. */
    @Bean
    public ServiceClock serviceClock(@Value("${app.scheduler.zone:Asia/Seoul}") String zone) {
        return new ServiceClock(zone);
    }

    /** 사용자 화면의 "오늘·지금" 판단. 타임존 조회는 desk 구현({@link DeskUserZoneProvider})에 위임. */
    @Bean
    public UserClock userClock(UserZoneProvider userZoneProvider, ServiceClock serviceClock) {
        return new UserClock(userZoneProvider, serviceClock);
    }
}
