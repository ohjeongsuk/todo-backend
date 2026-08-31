-- Todo 제목 대소문자 무시 검색(LOWER(title) LIKE '%keyword%') 성능 확보용 함수 기반 인덱스.
-- JPA @Table(indexes=...)로는 함수 표현식 인덱스를 만들 수 없어 수동으로 관리한다(ROADMAP Phase 4).
-- 로컬 todolist_db, todolist_db_test 양쪽에 각각 실행한다.
--   psql -h localhost -U postgres -d todolist_db -f db/add-title-index.sql
--   psql -h localhost -U postgres -d todolist_db_test -f db/add-title-index.sql
CREATE INDEX IF NOT EXISTS idx_todos_title_lower ON todos (LOWER(title));
