package com.example.todoapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화. 이 저장소에서 스케줄링을 쓰는 첫 기능이 첨부 고아 파일 정리다 (PRD F-51).
 *
 * <p><b>단일 인스턴스를 전제로 한다.</b> EC2 한 대에 배포하는 구성이라 문제되지 않지만, 인스턴스를 늘리면 모든
 * 인스턴스가 같은 배치를 돌려 같은 파일을 중복 삭제하려 든다. 그 시점에는 분산 락이나 별도 배치 노드가 필요하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
