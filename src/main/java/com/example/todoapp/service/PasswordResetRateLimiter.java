package com.example.todoapp.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 재설정 요청 빈도를 제한한다 (PRD NF-30: 동일 이메일/IP 10분 3회).
 *
 * <p>이메일과 IP를 <b>각각</b> 센다. 이메일만 세면 한 IP에서 여러 이메일을 훑는 시도를 막지 못하고, IP만
 * 세면 공유 IP 뒤의 정상 사용자가 남의 요청 때문에 막힌다.
 *
 * <p><b>메모리에만 저장한다.</b> 재시작하면 카운트가 사라지고 인스턴스 간에도 공유되지 않는다. 배포 형태가
 * EC2 단일 인스턴스(ROADMAP Phase 11)라 지금은 충분하다. 다중 인스턴스로 확장하면 공유 저장소가 필요하다.
 */
@Component
public class PasswordResetRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 3;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    /**
     * 요청 하나를 기록하고 한도를 넘었는지 판정한다.
     *
     * @return 한도를 넘어 거절해야 하면 {@code true}
     */
    public boolean isLimitExceeded(String email, String clientIp) {
        // 두 키를 모두 기록해야 한다. 단축 평가(||)를 쓰면 앞에서 막혔을 때 뒤가 기록되지 않는다.
        boolean emailExceeded = record("email:" + email);
        boolean ipExceeded = record("ip:" + clientIp);
        return emailExceeded || ipExceeded;
    }

    private boolean record(String key) {
        Instant now = Instant.now();
        Deque<Instant> timestamps = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        // 같은 키에 대한 동시 요청이 Deque를 깨뜨리지 않도록 키 단위로 잠근다.
        synchronized (timestamps) {
            evictExpired(timestamps, now);
            timestamps.addLast(now);
            return timestamps.size() > MAX_ATTEMPTS;
        }
    }

    private void evictExpired(Deque<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.removeFirst();
        }
    }

    /** 오래된 키를 주기적으로 비운다. 없으면 요청이 온 적 있는 모든 이메일·IP가 영구히 쌓인다. */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void evictIdleKeys() {
        Instant now = Instant.now();
        attempts.forEach(
                (key, timestamps) -> {
                    synchronized (timestamps) {
                        evictExpired(timestamps, now);
                        if (timestamps.isEmpty()) {
                            attempts.remove(key, timestamps);
                        }
                    }
                });
    }
}
