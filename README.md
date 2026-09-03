# todo-backend

Todo List 서비스의 백엔드. Spring Boot 4 + PostgreSQL.

문서 저장소(`todo-project`)와 함께 클론했다고 가정한다. 프로젝트 전체 구조와 규칙은 [`../CLAUDE.md`](../CLAUDE.md)를 참고한다.

## 기술 스택

| 항목 | 버전/선택 |
|---|---|
| Java | JDK 21 |
| 프레임워크 | Spring Boot 4.1 |
| 빌드 | Maven (`./mvnw` 래퍼) |
| 보안 | Spring Security |
| ORM | Spring Data JPA / Hibernate |
| DB | PostgreSQL |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| 검증 | Jakarta Bean Validation |
| HTML Sanitize | Jsoup |

## 로컬 실행

### 1. PostgreSQL 준비

`todolist_db`(개발용)와 `todolist_db_test`(테스트용) 두 데이터베이스가 필요하다. 둘 다 소문자다.

```sql
CREATE DATABASE todolist_db;
CREATE DATABASE todolist_db_test;
```

### 2. 로컬 설정 파일 작성

`application-local.yml`은 실제 시크릿을 담으므로 저장소에 커밋되지 않는다(`.gitignore`). 예시 파일을 복사해 채운다.

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

채워야 할 값(전부 로컬 전용, 운영값과 다름):

- `spring.datasource.username` / `password` — 로컬 PostgreSQL 접속 정보
- `spring.security.oauth2.client.registration.google.client-id` / `client-secret` — Google Cloud Console에서 발급받은 **로컬용** OAuth 클라이언트 값. 리다이렉트 URI로 `http://localhost:8080/login/oauth2/code/google`을 등록해야 한다
- `jwt.secret` — JWT 서명 키. 로컬은 임의 문자열이면 된다(운영은 충분히 무작위한 값을 환경변수로 주입)

### 3. 실행

```bash
./mvnw spring-boot:run
```

`application.yml`의 기본 활성 프로파일이 `local`이므로 별도 플래그 없이 `application-local.yml`이 적용된다. 기동 후:

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 테스트

```bash
./mvnw test
```

통합 테스트(컨트롤러)와 Repository 단위 테스트를 포함한다. `todolist_db_test`를 사용하므로 1단계의 두 DB가 모두 준비되어 있어야 한다.

## 코드 품질

```bash
./mvnw spotless:check   # 포맷 검사
./mvnw spotless:apply   # 포맷 자동 적용
```

Spotless 포맷 위반이 있으면 `./mvnw compile`이 실패한다. 자세한 도구 설정 근거는 [`../docs/DEV_TOOLS.md`](../docs/DEV_TOOLS.md)를 참고한다.

## 패키지 구조

기능별(package-by-feature)로 나뉜다: `auth`, `user`, `todo`, `global`. 계층은 `controller → service → repository`이며 컨트롤러가 리포지토리를 직접 호출하지 않는다.

## Docker

이 프로젝트는 Docker를 사용하지 않는다. 로컬 PostgreSQL을 직접 설치해 사용한다.
