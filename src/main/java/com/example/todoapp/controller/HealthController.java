package com.example.todoapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.todoapp.dto.HealthResponse;
import com.example.todoapp.exception.ApiResponse;

/**
 * 경량 라이브니스(liveness) 엔드포인트. 프로세스가 요청을 처리할 수 있는지만 확인한다.
 *
 * <p><b>{@code /actuator/health}와 역할이 다르다.</b> 이쪽은 DB를 조회하지 않아 부하가 사실상 없으므로
 * 외부 모니터링의 상시 폴링에 적합하다. 반면 {@code /actuator/health}는 DataSource까지 확인하는 레디니스
 * (readiness) 판정이라 배포 스크립트({@code deploy/redeploy.sh})의 기동 성공 판정에 쓴다.
 *
 * <p>두 경로 모두 인증 없이 열려 있어야 하므로 {@code SecurityConfig}의 permitAll 목록에 포함돼 있다.
 * 내부 상태를 노출하지 않도록 응답은 상태 문자열 하나로 제한한다.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.success(HealthResponse.up());
    }
}
