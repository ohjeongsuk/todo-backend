# EC2 배포 가이드

`todo-backend`를 Amazon Linux EC2(t3.micro)에 systemd 서비스로 배포한다.

- 상위 계획과 결정 근거: `todo-project/docs/ROADMAP.md` Phase 11
- 절대 규칙: `todo-project/CLAUDE.md`

---

## 1. 구성 요소

| 파일 | 배치 위치 | git | 역할 |
|---|---|---|---|
| `todolist.service` | `/etc/systemd/system/` | 추적 | systemd 유닛 |
| `todolist.conf` | `/etc/todolist/` | **추적** | 비-비밀 설정 (DB 호스트·CORS·JVM 옵션) |
| `todolist.env` | `/etc/todolist/` | **제외** | 비밀 6개. 로컬 원본은 `todo-project/` 루트에 둔다 (저장소 밖) |
| `install.sh` | — | 추적 | 최초 1회 설치 |
| `redeploy.sh` | `/etc/todolist/` | 추적 | 재배포 (실패 시 자동 롤백) |
| jar | `/etc/todolist/todolist.jar` | 제외 | 애플리케이션 |

**비밀과 비-비밀을 파일로 나눈 이유**: systemd는 `EnvironmentFile=`을 여러 번 선언할 수 있어
두 파일이 런타임에 합쳐진다. 덕분에 **설정 변경 이력은 git에 남기면서 비밀은 서버에만** 둘 수 있다.

---

## 2. 최초 배포

### 2-1. 사전 준비 (AWS 콘솔)

- **IAM 역할을 EC2 인스턴스에 부착** — `s3:GetObject` / `PutObject` / `DeleteObject`
  (리소스 `arn:aws:s3:::<버킷>/*`). 코드가 `DefaultCredentialsProvider`를 쓰므로 이것만 하면
  액세스 키를 서버에 두지 않아도 된다 (`CLAUDE.md` 절대 규칙 9).
- **S3 버킷 CORS** — 본문 이미지 첨부는 브라우저가 presigned URL 로 S3 에 **직접 PUT** 한다.
  이때 `Content-Type` 이 서명에 포함되어(`SignedHeaders=content-type;host`) 브라우저가 PUT 앞에
  프리플라이트(OPTIONS)를 보내는데, 여기에 응답할 수 있는 것은 **버킷 자신의 CORS 설정뿐**이다.
  백엔드의 `cors.allowed-origin` 은 `/api/**` 전용이라 아무 영향이 없다.
  설정이 없으면 업로드가 `net::ERR_FAILED` 로 전부 실패한다.

  S3 콘솔 → 버킷 → **권한** 탭 → 맨 아래 **CORS** → 편집 → 아래 JSON 붙여넣기 → 저장.
  (새 콘솔은 JSON 만 받는다. 최상위가 `[ ... ]` 여야 한다. 반영은 수 초 내이고 재배포는 필요 없다.)

  ```json
  [
    {
      "AllowedOrigins": [
        "https://main.d1prwks7k0qzzg.amplifyapp.com",
        "http://localhost:3000"
      ],
      "AllowedMethods": ["PUT", "GET", "HEAD"],
      "AllowedHeaders": ["Content-Type"],
      "ExposeHeaders": [],
      "MaxAgeSeconds": 3000
    }
  ]
  ```

  - `AllowedOrigins` 는 **문자열 정확 일치**다. 트레일링 슬래시를 붙이면 다른 origin 으로
    취급되어 실패한다. Amplify 도메인이 바뀌면 `todolist.conf` 의 `CORS_ALLOWED_ORIGIN` 과
    **여기를 함께** 고쳐야 한다.
  - `http://localhost:3000` 을 빼면 안 된다. `application-local.properties` 의
    `app.storage.type=s3` 때문에 **로컬 개발도 같은 버킷에 브라우저에서 PUT 한다.**
    빠뜨리면 운영만 고쳐지고 로컬은 그대로 깨진다.
  - `AllowedHeaders` 의 `Content-Type` 이 이 설정의 핵심이다. 이것만 빠져도 프리플라이트가 403 이다.
  - `DELETE` 는 넣지 않는다. 첨부 삭제는 브라우저가 아니라 백엔드 SDK 가 수행한다.
  - `main` 외 브랜치를 Amplify 에 배포하게 되면 브랜치마다 origin 이 새로 생긴다. 그때
    `"https://*.d1prwks7k0qzzg.amplifyapp.com"` 한 줄로 대체한다 (origin 당 `*` 하나까지 허용).
- **버킷 퍼블릭 차단 확인** — 배포 전 필수. 퍼블릭 액세스 차단 4개가 모두 ON 이고 버킷 정책에
  `Principal: "*"` Statement 가 없어야 한다 (`CLAUDE.md` 2장: 버킷은 퍼블릭으로 열지 않는다).
  조회는 presigned GET 으로 충분하므로 퍼블릭 읽기는 필요 없다.
  현재 미충족 상태이며 경위는 `docs/CHECKLIST.md` 16-1.6 에 기록돼 있다.
- **EC2 보안그룹** — 22는 본인 IP. 1단계에서는 8080도 본인 IP로만. (2단계에서 80/443 공개, 8080 폐쇄)
- **RDS 보안그룹** — 5432 인바운드를 EC2 보안그룹에서만 허용.
- **DB 스키마** — `todolist_db` 생성 후 `db/schema.sql` 적용.
  운영 프로파일은 `ddl-auto=validate`라 **스키마가 없으면 기동하지 않는다.**
  ```bash
  psql -h <RDS엔드포인트> -U postgres -d postgres -c "CREATE DATABASE todolist_db;"
  psql -h <RDS엔드포인트> -U postgres -d todolist_db -f db/schema.sql
  ```

### 2-2. 설정 파일 준비 (로컬)

`todolist.env.example`을 복사해 `todolist.env`를 만들고 값 6개를 채운다.
시크릿은 개발용과 분리해 새로 만든다 — 한쪽이 유출돼도 다른 쪽 토큰을 위조할 수 없게 한다.

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # STORAGE_SIGNING_SECRET (JWT_SECRET과 반드시 다른 값)
```

`todolist.conf`는 DB 호스트·CORS·프론트 URL이 자기 환경과 맞는지 확인한다.

### 2-3. 빌드 및 업로드

```powershell
.\mvnw.cmd clean package
```

아래 **6개 파일**을 **WinSCP 바이너리 모드**로 `/home/ec2-user/`에 올린다.

```
todo-backend/target/todo-backend-0.0.1-SNAPSHOT.jar
todo-backend/deploy/todolist.service   <- 빠뜨리기 쉽다. 없으면 install.sh가 중단된다
todo-backend/deploy/todolist.conf
todo-backend/deploy/install.sh
todo-backend/deploy/redeploy.sh
todolist.env                            <- 저장소 밖. 아래 주의 참고
```

> ⚠️ **`todolist.env`만 위치가 다르다.** 실제 비밀이 든 파일이라 `todo-project/` 루트
> (`todo-backend/`의 상위)에 두고 두 저장소 어디에도 들어가지 않게 했다.
> 여기 `deploy/`에는 `todolist.env.example`(값이 `CHANGE_ME`인 예시)만 있다.

> ⚠️ **텍스트 모드로 올리면 jar이 깨진다.** 스크립트가 크기와 zip 무결성으로 걸러내지만,
> 애초에 바이너리 모드인지 확인하는 편이 빠르다.
>
> ⚠️ `/etc/todolist`에는 WinSCP로 직접 못 쓴다(`ec2-user` 권한). 홈에 올리면 스크립트가 sudo로 옮긴다.

### 2-4. 설치

```bash
chmod +x ~/install.sh ~/redeploy.sh
sudo ~/install.sh
```

스키마를 아직 적용하지 않았다면 `--no-start`를 붙인다:

```bash
sudo ~/install.sh --no-start   # 설치만 (psql도 이때 깔린다)
# ... 스키마 적용 ...
sudo systemctl start todolist
```

> **`--no-start`가 있는 이유 (닭-달걀)**: prod는 `validate`라 스키마가 먼저 있어야 뜨는데,
> 스키마를 넣을 `psql`은 이 스크립트가 설치한다. 그래서 설치와 기동을 끊을 수단이 필요하다.

`install.sh`가 하는 일: JDK 21 · psql 설치 → **스왑 2GB** → `todolist` 시스템 계정 →
`/etc/todolist` 배치(권한 포함) → 유닛 등록·`enable` → 기동 → 헬스체크(최대 90초).

### 2-5. 확인

```bash
sudo systemctl status todolist
sudo journalctl -u todolist | grep -i profile     # "prod" 여야 한다
curl -s localhost:8080/actuator/health            # {"status":"UP"}
curl -s localhost:8080/api/health
free -h                                            # Swap 2.0Gi
```

브라우저: `http://<EC2-공인IP>:8080/swagger-ui/index.html`

---

## 3. 재배포

```powershell
.\mvnw.cmd clean package
```
→ jar을 WinSCP로 홈에 올린 뒤:
```bash
sudo ~/redeploy.sh
```

동작: **업로드 jar 검증 → 현재 jar 백업 → 교체 → 기동 → 헬스체크.**
헬스체크가 실패하면 **백업으로 자동 롤백**하고 다시 띄운 뒤 종료코드 1을 반환한다.
백업은 `/etc/todolist/backup/`에 최근 3개만 남는다.

**설정만 바꿀 때는 재배포가 아니다.** 파일을 고치고 재시작만 하면 된다:
```bash
sudo vi /etc/todolist/todolist.conf
sudo systemctl restart todolist
```

---

## 4. 헬스체크 엔드포인트 2종

| 경로 | 판정 범위 | 용도 |
|---|---|---|
| `/actuator/health` | 프로세스 + **DataSource** | 배포 스크립트의 기동 성공/롤백 판정, nginx 레디니스 |
| `/api/health` | 프로세스만 (DB 미조회) | 외부 모니터링 상시 폴링 (부하 없음) |

배포 판정에 `/actuator/health`를 쓰는 이유는 **"떴지만 DB에 못 붙은" 상태를 성공으로 오판하지
않기 위해서**다. 노출은 `health` 하나로 제한돼 있다 — `env`·`beans`·`configprops`가 열리면
환경변수와 빈 구성이 그대로 새어나간다.

---

## 5. HTTPS 전환 (2단계)

도메인을 확보한 뒤 `ROADMAP.md` 11-3을 따른다. 백엔드 쪽에서 할 일은 세 가지뿐이고
**jar 재빌드는 필요 없다.**

1. `todolist.conf`에서 `REFRESH_COOKIE_SAME_SITE`·`REFRESH_COOKIE_SECURE` **두 줄을 지운다**
   → `application-prod.properties`의 기본값 `None`/`true`로 복귀 (cross-site 쿠키에 필수)
2. `application-prod.properties`의 `server.forward-headers-strategy=framework` 주석 해제
   → 이게 없으면 nginx 뒤에서 Spring이 모든 요청을 `http`로 인식해 구글 OAuth 콜백이 깨진다
3. 보안그룹에서 80/443을 열고 8080을 닫는다

---

## 6. 문제 해결

| 증상 | 확인할 것 |
|---|---|
| 기동 실패, 로그에 `Schema-validation` | 스키마 미적용. `db/schema.sql`을 `todolist_db`에 적용했는지 |
| 기동 실패, `NoSuchBeanDefinitionException` | 필수 환경변수 누락. `todolist.env`의 6개가 모두 채워졌는지 |
| 로그에 `profile is active: "local"` | `todolist.conf`의 `SPRING_PROFILES_ACTIVE=prod` 누락 |
| DB 접속 실패 | RDS 보안그룹이 EC2를 허용하는지. `DB_HOST`에 `jdbc:`나 포트가 섞이지 않았는지 |
| 값이 이상하게 잘림 | 설정 파일이 CRLF로 올라갔을 수 있다. `install.sh`가 제거하지만 재확인 |
| 프로세스가 조용히 사라짐 | OOM Killer 의심. `free -h`로 스왑, `dmesg | grep -i oom` 확인 |
| S3 업로드 실패 | IAM 역할이 인스턴스에 부착됐는지, 정책 리소스 ARN이 맞는지 |

로그: `sudo journalctl -u todolist -f` / 최근 100줄 `sudo journalctl -u todolist -n 100 --no-pager`

---

## 7. 알려진 임시 상태

- **비밀번호 재설정 메일이 실제로 발송되지 않는다.** 운영에서도 콘솔 로그 방식이 뜬다
  (`LocalPasswordResetMailSender`). 재설정 링크가 journald에 평문으로 남으므로
  **서버 로그 열람 권한이 곧 임의 계정의 재설정 권한**이 된다.
  실사용자 공개 전 반드시 해소한다 (`ROADMAP.md` 11-1-1).
- **1단계(HTTP)에서는 구글 로그인과 프론트엔드 연동이 동작하지 않는다.** 정상이다.
  구글은 HTTPS 리다이렉트 URI를 요구하고, HTTPS 페이지에서 HTTP API 호출은 차단된다.
