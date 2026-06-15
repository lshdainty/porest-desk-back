package com.porest.desk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// JPA Auditing 은 JpaAuditingConfig 로 분리(웹 슬라이스 테스트 호환). 스케줄링은 유지.
@SpringBootApplication(scanBasePackages = {"com.porest.desk", "com.porest.core"})
@EnableScheduling
public class DeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeskApplication.class, args);
    }
}
