@../CLAUDE.md

---

이 파일은 `todo-backend` 저장소 전용 보충 정보다. 공통 규칙은 위 임포트된 루트 `CLAUDE.md`를 따른다.

## 로컬 실행

```
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# application-local.yml 에 실제 DB 비밀번호 입력 후
./mvnw spring-boot:run
```

`application.yml`의 `spring.profiles.active` 기본값이 `local`이므로 프로파일 옵션 없이 실행하면 된다.

## 설정 파일 구조

- `application.yml` — 프로파일 공통 설정 (비밀 없음, git 추적)
- `application-local.yml` — 로컬 전용, 실제 비밀번호 포함 (git 추적 제외)
- `application-local.yml.example` — 로컬 설정 예시 (git 추적)
- `application-prod.yml` — 운영 전용, 값은 전부 환경변수로 주입 (git 추적)
