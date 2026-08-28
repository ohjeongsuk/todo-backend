package com.example;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * {@code @EnableJpaAuditing}은 반드시 메인 애플리케이션 클래스에 붙인다. {@code @Configuration} 클래스에
 * 두면 {@code @DataJpaTest} 슬라이스 테스트가 이를 로드하지 못해 {@code created_at}이 null로 남는다.
 */
@SpringBootApplication
@EnableJpaAuditing
public class TodoBackendApplication {

    static {
        // JVM 기본 타임존을 UTC로 고정한다. LocalDateTime.now()로 채워지는 Auditing 값이
        // 로컬(KST) 환경과 운영(EC2) 환경에서 다르게 저장되는 것을 막는다.
        // hibernate.jdbc.time_zone: UTC 만으로는 JDBC 전송 시점 변환만 보정될 뿐,
        // LocalDateTime.now() 자체가 KST 벽시계 값을 만드는 것은 막지 못한다.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(TodoBackendApplication.class, args);
    }
}
