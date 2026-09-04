@../CLAUDE.md

---

이 파일은 `todo-backend` 저장소 전용 보충 정보다. 공통 규칙은 위 임포트된 루트 `CLAUDE.md`를 따른다.

## 로컬 실행

```
cp src/main/resources/application-local.properties.example src/main/resources/application-local.properties
# application-local.properties 에 실제 DB 비밀번호 입력 후
./mvnw spring-boot:run
```

`application.properties`의 `spring.profiles.active` 기본값이 `local`이므로 프로파일 옵션 없이 실행하면 된다.

## 설정 파일 구조

설정 파일은 **`.properties` 형식만** 쓴다. `.yml`은 쓰지 않는다.

- `application.properties` — 프로파일 공통 설정 (비밀 없음, git 추적)
- `application-local.properties` — 로컬 전용, 실제 비밀번호 포함 (git 추적 제외)
- `application-local.properties.example` — 로컬 설정 예시 (git 추적)
- `application-prod.properties` — 운영 전용, 값은 전부 환경변수로 주입 (git 추적)
- `src/test/resources/application-test.properties` — 테스트 전용 (git 추적 제외) + `.example`

### `.properties` 작성 시 반드시 지킬 것

1. **값에는 ASCII 만 쓴다.** Spring Boot 는 `.properties` 를 **ISO-8859-1** 로 읽는다
   (`[encoding=utf-8]` 속성은 `spring.config.import` 전용이라 여기엔 적용되지 않는다).
   한글은 주석에만 둔다 — 주석 줄은 파서가 통째로 버리므로 안전하다.
2. **`#` 은 줄 맨 앞에서만 주석이다.** YAML 과 달리 값 뒤에 붙이면 값의 일부가 된다.
   `jwt.access-token-expiration=1800000 # 30분` 은 값이 `1800000 # 30분` 이 되어 파싱에 실패한다.
   인라인 주석은 앞줄로 올린다.
