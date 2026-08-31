-- 성능 측정 전용 시드: 같은 계정에 Todo 10,000건.
-- ROADMAP Phase 4 DoD "키워드 검색 포함 목록 조회가 워밍업 후 3회 측정 중앙값 500ms 이내"를
-- 실측하기 위한 데이터다. 제목 5건 중 1건꼴로 검색 키워드 '테스트'를 포함시켜
-- LOWER(title) LIKE '%키워드%' 경로가 실제로 인덱스(idx_todos_title_lower)를 타는지 검증한다.
--   psql -h localhost -U postgres -d todolist_db -f db/seed-perf.sql
INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES (
    'seed-perf@example.com',
    '$2a$10$.8wM9Y4UhDm7ry.m31idw.i5l.adSd4AJxo0hmgyW6fMRVgnsFTaa', -- 'test1234'의 BCrypt 해시 (실제 발급값 재사용)
    'SeedPerfUser',
    'LOCAL',
    now(),
    now()
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO todos (
    user_id, title, content, priority, due_date, completed, completed_at, created_at, updated_at
)
SELECT
    u.id,
    CASE
        WHEN gs % 5 = 0 THEN '성능 테스트 항목 ' || gs
        ELSE '일반 할 일 ' || gs
    END,
    '<p>성능 측정용 더미 본문 ' || gs || '</p>',
    (ARRAY['LOW', 'MEDIUM', 'HIGH'])[1 + (gs % 3)],
    (CURRENT_DATE + ((gs % 60) || ' days')::interval)::date,
    (gs % 4 = 0),
    CASE WHEN gs % 4 = 0 THEN now() - ((gs % 20) || ' days')::interval ELSE NULL END,
    now() - (gs || ' seconds')::interval,
    now() - (gs || ' seconds')::interval
FROM generate_series(1, 10000) AS gs
CROSS JOIN (SELECT id FROM users WHERE email = 'seed-perf@example.com') AS u;
