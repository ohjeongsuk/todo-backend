package com.example.todoapp.dto;

/**
 * 경량 헬스체크 응답. 프로세스 생존만 알리므로 필드는 상태 문자열 하나뿐이다.
 *
 * <p>DB 상태까지 포함한 상세 판정은 {@code /actuator/health}가 담당한다.
 */
public record HealthResponse(String status) {

    /** Actuator의 상태 표기({@code UP})와 문자열을 맞춘다. 두 엔드포인트를 같은 방식으로 파싱할 수 있다. */
    public static HealthResponse up() {
        return new HealthResponse("UP");
    }
}
