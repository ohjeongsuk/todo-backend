package com.example.todoapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 클라이언트 빈. {@code app.storage.type=s3}일 때만 등록된다 (PRD F-50).
 *
 * <p>자격증명은 명시하지 않는다. SDK 기본값인 {@code DefaultCredentialsProvider} 체인이 자동으로 처리한다 — 로컬
 * 시험은 환경변수(AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY), EC2 배포는 IAM Role. 코드에 분기를 두지 않는
 * 것이 이 자동 선택의 목적이다 (CLAUDE.md 절대 규칙 9).
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${app.storage.s3.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${app.storage.s3.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
