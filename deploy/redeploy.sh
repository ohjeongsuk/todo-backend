#!/usr/bin/env bash
# ==============================================================================
# todolist 백엔드 재배포 스크립트
#
# 사용법
#   1) 로컬에서 빌드:  .\mvnw.cmd clean package
#   2) WinSCP(바이너리 모드)로 target\todo-backend-0.0.1-SNAPSHOT.jar 을
#      ec2-user 홈 디렉터리에 업로드
#   3) sudo ~/redeploy.sh        (또는 sudo /etc/todolist/redeploy.sh)
#
# 동작
#   업로드 검증 -> 현재 jar 백업 -> 교체 -> 기동 -> 헬스체크
#   헬스체크가 실패하면 백업 jar 로 자동 롤백하고 다시 기동한다.
#
# 설정만 바꾼 경우(todolist.env)에는 이 스크립트가 아니라 그 파일을 갱신한 뒤
# 'sudo systemctl restart todolist' 만 하면 된다.
# ==============================================================================
set -euo pipefail

APP_NAME="todolist"
APP_USER="todolist"
INSTALL_DIR="/etc/todolist"
BACKUP_DIR="${INSTALL_DIR}/backup"
JAR_SRC_NAME="todo-backend-0.0.1-SNAPSHOT.jar"
JAR_DEST="${INSTALL_DIR}/todolist.jar"
KEEP_BACKUPS=3
HEALTH_TIMEOUT=90

UPLOAD_USER="${SUDO_USER:-ec2-user}"
UPLOAD_DIR="/home/${UPLOAD_USER}"
JAR_SRC="${UPLOAD_DIR}/${JAR_SRC_NAME}"

log()  { printf '\n\033[1;34m[redeploy]\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33m[경고]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[실패]\033[0m %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "root 권한이 필요하다. 'sudo $0' 로 실행할 것."
[[ -f "${JAR_DEST}" ]] || die "${JAR_DEST} 가 없다. 최초 설치는 install.sh 로 할 것."

SERVER_PORT="$(grep -E '^SERVER_PORT=' "${INSTALL_DIR}/todolist.env" | cut -d= -f2- | tr -d '[:space:]')"
SERVER_PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${SERVER_PORT}/actuator/health"

# ------------------------------------------------------------------------------
# 헬스체크: 제한 시간까지 폴링한다.
# /api/health 가 아니라 /actuator/health 를 쓰는 이유는 DataSource 상태까지 확인해
# "떴지만 DB 에 못 붙은" 상태를 성공으로 오판하지 않기 위해서다.
# ------------------------------------------------------------------------------
wait_for_health() {
    local deadline=$(( SECONDS + HEALTH_TIMEOUT ))
    while (( SECONDS < deadline )); do
        if curl -fsS --max-time 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 3
    done
    return 1
}

# ------------------------------------------------------------------------------
# 1. 업로드된 jar 검증
#
# 교체하기 전에 확인한다. 텍스트 모드 전송으로 깨진 jar 을 배포한 뒤 롤백하는 것보다
# 아예 시작하지 않는 편이 낫다.
# ------------------------------------------------------------------------------
log "업로드 jar 검증: ${JAR_SRC}"
[[ -f "${JAR_SRC}" ]] || die "${JAR_SRC} 가 없다. 빌드한 jar 을 홈 디렉터리에 업로드할 것."

jar_size=$(stat -c%s "${JAR_SRC}")
(( jar_size > 1000000 )) || die "jar 크기가 ${jar_size} 바이트로 비정상이다. WinSCP 전송 모드를 '바이너리'로 확인할 것."

# jar 은 zip 형식이다. 목록을 읽을 수 있어야 온전한 파일이다.
if command -v unzip >/dev/null 2>&1; then
    unzip -tq "${JAR_SRC}" >/dev/null 2>&1 || die "jar 이 손상됐다(zip 무결성 검사 실패). 다시 업로드할 것."
else
    head -c 2 "${JAR_SRC}" | grep -q 'PK' || die "jar 형식이 아니다. 다시 업로드할 것."
fi
echo "  정상 ($(( jar_size / 1024 / 1024 ))MB)"

# ------------------------------------------------------------------------------
# 2. 현재 jar 백업
# ------------------------------------------------------------------------------
install -d -o root -g root -m 750 "${BACKUP_DIR}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_JAR="${BACKUP_DIR}/todolist-${TIMESTAMP}.jar"

log "현재 jar 백업: ${BACKUP_JAR}"
cp -p "${JAR_DEST}" "${BACKUP_JAR}"

# 오래된 백업 정리 (최근 ${KEEP_BACKUPS}개만 남긴다). 디스크가 8GB 수준이라 방치하면 찬다.
mapfile -t old_backups < <(ls -1t "${BACKUP_DIR}"/todolist-*.jar 2>/dev/null | tail -n +$(( KEEP_BACKUPS + 1 )))
if (( ${#old_backups[@]} > 0 )); then
    echo "  오래된 백업 ${#old_backups[@]}개 삭제"
    rm -f "${old_backups[@]}"
fi

# ------------------------------------------------------------------------------
# 3. 교체 및 기동
# ------------------------------------------------------------------------------
log "서비스 정지"
systemctl stop "${APP_NAME}"

log "jar 교체"
install -o root -g "${APP_USER}" -m 640 "${JAR_SRC}" "${JAR_DEST}"

log "서비스 기동"
systemctl start "${APP_NAME}"

# ------------------------------------------------------------------------------
# 4. 헬스체크 · 실패 시 롤백
# ------------------------------------------------------------------------------
log "헬스체크 대기 (최대 ${HEALTH_TIMEOUT}초): ${HEALTH_URL}"
if wait_for_health; then
    log "재배포 성공. 배포된 jar: ${JAR_SRC_NAME} ($(date '+%Y-%m-%d %H:%M:%S'))"
    echo "  백업 보관: ${BACKUP_JAR}"
    exit 0
fi

warn "헬스체크 실패. 새 jar 의 최근 로그:"
journalctl -u "${APP_NAME}" -n 50 --no-pager

warn "백업으로 롤백한다: ${BACKUP_JAR}"
systemctl stop "${APP_NAME}" || true
install -o root -g "${APP_USER}" -m 640 "${BACKUP_JAR}" "${JAR_DEST}"
systemctl start "${APP_NAME}"

if wait_for_health; then
    warn "롤백 완료. 이전 버전으로 정상 동작 중이다. 새 jar 은 배포되지 않았다."
else
    # 롤백해도 안 뜨면 jar 문제가 아니다 (RDS 장애, 설정 오류, 스키마 불일치 등).
    warn "롤백 후에도 기동하지 않는다. jar 이 아니라 환경 문제일 가능성이 높다:"
    warn "  - RDS 접속 가능 여부 (보안그룹·자격증명)"
    warn "  - todolist.env 값"
    warn "  - 스키마 불일치 (ddl-auto=validate)"
    journalctl -u "${APP_NAME}" -n 50 --no-pager
fi
exit 1
