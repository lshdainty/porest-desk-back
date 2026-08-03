package com.porest.desk.calendar.client;

import com.porest.desk.calendar.config.HolidayApiClientConfig;
import com.porest.desk.calendar.config.HolidayProperties;
import com.porest.desk.calendar.repository.HolidayRepository;
import com.porest.desk.calendar.service.HolidaySyncServiceImpl;
import com.porest.desk.calendar.service.HolidayYearSynchronizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.porest.core.time.ServiceClock;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공휴일 동기화 빈 배선 테스트.
 *
 * <p>클라이언트 단위 테스트는 의존성을 손으로 넣어 만들기 때문에 컨테이너가 실제로 그 빈을
 * 만들 수 있는지는 검증하지 못한다. 실제로 이 애플리케이션에는 Jackson 2 의 ObjectMapper 빈이
 * 없어서(Spring Boot 4 는 Jackson 3 을 쓴다) 생성자로 주입받으려던 코드가 기동 단계에서 죽었는데,
 * 단위 테스트는 전부 통과했다. 그 구멍을 막는 테스트다.
 *
 * <p>DB·Redis 없이 도는 슬라이스라 전체 컨텍스트를 띄우지 않는다.
 */
class HolidayClientWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HolidayApiClientConfig.class)
        .withBean(HolidayProperties.class)
        .withBean(HolidayRepository.class, () -> Mockito.mock(HolidayRepository.class))
        .withBean(KasiHolidayClient.class)
        .withBean(HolidaysKrClient.class)
        .withBean(HolidayYearSynchronizer.class)
        .withBean(ServiceClock.class, () -> new ServiceClock("Asia/Seoul"))
        .withBean(HolidaySyncServiceImpl.class);

    @Test
    @DisplayName("공휴일 동기화 빈이 컨테이너에서 생성된다 — 주입 못 하는 의존성이 없어야 한다")
    void contextLoads() {
        runner.run(context -> assertThat(context)
            .hasNotFailed()
            .hasSingleBean(KasiHolidayClient.class)
            .hasSingleBean(HolidaysKrClient.class)
            .hasSingleBean(HolidayYearSynchronizer.class)
            .hasSingleBean(HolidaySyncServiceImpl.class));
    }

    @Test
    @DisplayName("두 소스가 KASI → 폴백 순서로 주입된다")
    void providersAreOrdered() {
        runner.run(context -> {
            HolidayYearSynchronizer synchronizer = context.getBean(HolidayYearSynchronizer.class);
            assertThat(synchronizer).isNotNull();
            // 순서 보장이 깨지면 인증키가 있어도 폴백이 먼저 쓰여 KASI 데이터가 적재되지 않는다.
            assertThat(context.getBeanNamesForType(HolidayProvider.class))
                .containsExactly("kasiHolidayClient", "holidaysKrClient");
        });
    }
}
