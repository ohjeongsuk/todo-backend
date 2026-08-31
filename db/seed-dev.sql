-- 개발용 시드: 테스트 계정 1개 + Todo 100건 (우선순위·완료·마감일 혼합).
-- 페이지네이션·필터·정렬을 눈으로 확인하는 용도다(성능 측정은 seed-perf.sql 사용).
--
-- 사전 준비: 아래 이메일로 회원가입 API를 한 번 호출해 계정을 만든 뒤,
-- 그 계정의 실제 BCrypt 해시로 users.password 값을 대체해야 한다(평문 저장 금지, CLAUDE.md 절대 규칙 7).
--   psql -h localhost -U postgres -d todolist_db -f db/seed-dev.sql
INSERT INTO users (email, password, nickname, provider, created_at, updated_at)
VALUES (
    'seed-dev@example.com',
    '$2a$10$.8wM9Y4UhDm7ry.m31idw.i5l.adSd4AJxo0hmgyW6fMRVgnsFTaa', -- 'test1234'의 BCrypt 해시 (실제 발급값 재사용)
    'SeedDevUser',
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
    '개발 시드 Todo ' || gs,
    '<p>개발용 시드 데이터 ' || gs || '번째 항목입니다.</p>',
    (ARRAY['LOW', 'MEDIUM', 'HIGH'])[1 + (gs % 3)],
    CASE WHEN gs % 4 = 0 THEN NULL ELSE (CURRENT_DATE + ((gs % 30) || ' days')::interval)::date END,
    (gs % 3 = 0),
    CASE WHEN gs % 3 = 0 THEN now() - ((gs % 10) || ' days')::interval ELSE NULL END,
    now() - (gs || ' minutes')::interval,
    now() - (gs || ' minutes')::interval
FROM generate_series(1, 100) AS gs
CROSS JOIN (SELECT id FROM users WHERE email = 'seed-dev@example.com') AS u;
