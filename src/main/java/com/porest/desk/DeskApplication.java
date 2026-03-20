package com.porest.desk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.porest.desk", "com.porest.core"})
@EnableJpaAuditing
@EnableScheduling
public class DeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeskApplication.class, args);
    }
}
