#!/usr/bin/env bash
# ==============================================================================
# todolist 백엔드 최초 설치 스크립트 (Amazon Linux / t3.micro)
#
# 사용법
#   sudo ~/install.sh --no-start   # 최초 1회는 이걸 쓴다 (아래 설명 참고)
#   sudo ~/install.sh              # 설치 후 바로 기동까지
#
# --- 최초 1회에 --no-start 가 필요한 이유 ---
# 운영 프로파일은 ddl-auto=validate 라서 RDS 에 스키마가 먼저 있어야 기동된다.
# 그런데 스키마를 넣으려면 psql 이 필요하고, psql 은 이 스크립트가 설치한다.
# 그래서 순서가 이렇게 된다:
#   1) sudo ./install.sh --no-start   (설치만)
#   2) psql 로 RDS 에 DB 생성 + schema.sql 적용
#   3) sudo systemctl start todolist
#
# 사전 준비: 홈 디렉터리에 아래 3개 파일이 올라와 있어야 한다 (WinSCP, 바이너리 모드)
#   todo-backend-0.0.1-SNAPSHOT.jar
#   todolist.conf
#   todolist.env
# ==============================================================================
set -euo pipefail

APP_NAME="todolist"
APP_USER="todolist"
INSTALL_DIR="/etc/todolist"
JAR_SRC_NAME="todo-backend-0.0.1-SNAPSHOT.jar"
JAR_DEST="${INSTALL_DIR}/todolist.jar"
SERVICE_SRC_NAME="todolist.service"
SERVICE_DEST="/etc/systemd/system/todolist.service"
SWAP_FILE="/swapfile"
SWAP_SIZE="2G"
HEALTH_TIMEOUT=90

# sudo 로 실행하면 SUDO_USER 에 원래 계정이 담긴다. 그 홈 디렉터리에서 파일을 찾는다.
UPLOAD_USER="${SUDO_USER:-ec2-user}"
UPLOAD_DIR="/home/${UPLOAD_USER}"

START_SERVICE=1

log()  { printf '\n\033[1;34m[install]\033[0m %s\n' "$*"; }
warn() { printf '\n\033[1;33m[경고]\033[0m %s\n' "$*"; }
die()  { printf '\n\033[1;31m[실패]\033[0m %s\n' "$*" >&2; exit 1; }

# ------------------------------------------------------------------------------
# 0. 인자 파싱 · 사전 점검
# ------------------------------------------------------------------------------
for arg in "$@"; do
    case "$arg" in
        --no-start) START_SERVICE=0 ;;
        *) die "알 수 없는 옵션: ${arg} (사용 가능: --no-start)" ;;
    esac
done

[[ $EUID -eq 0 ]] || die "root 권한이 필요하다. 'sudo $0 $*' 로 실행할 것."

log "업로드 파일 확인 (${UPLOAD_DIR})"
for f in "${JAR_SRC_NAME}" "todolist.conf" "todolist.env"; do
    [[ -f "${UPLOAD_DIR}/${f}" ]] || die "${UPLOAD_DIR}/${f} 가 없다. WinSCP 로 먼저 업로드할 것."
done
# jar 이 텍스트 모드로 전송돼 깨지는 사고가 잦다. 크기로 1차 확인한다.
jar_size=$(stat -c%s "${UPLOAD_DIR}/${JAR_SRC_NAME}")
(( jar_size > 1000000 )) || die "jar 크기가 ${jar_size} 바이트로 비정상이다. WinSCP 전송 모드를 '바이너리'로 바꿔 다시 올릴 것."

# 유닛 파일은 스크립트와 같은 위치에 두는 것을 기본으로 하되, 홈 디렉터리도 찾아본다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if   [[ -f "${SCRIPT_DIR}/${SERVICE_SRC_NAME}" ]]; then SERVICE_SRC="${SCRIPT_DIR}/${SERVICE_SRC_NAME}"
elif [[ -f "${UPLOAD_DIR}/${SERVICE_SRC_NAME}" ]]; then SERVICE_SRC="${UPLOAD_DIR}/${SERVICE_SRC_NAME}"
else die "${SERVICE_SRC_NAME} 를 찾을 수 없다. 스크립트와 같은 디렉터리 또는 ${UPLOAD_DIR} 에 둘 것."
fi

# ------------------------------------------------------------------------------
# 1. 패키지 설치 (JDK 21, psql)
# ------------------------------------------------------------------------------
log "JDK 21 설치"
if java -version 2>&1 | grep -q '"21'; then
    echo "  이미 설치돼 있다: $(java -version 2>&1 | head -1)"
else
    dnf install -y java-21-amazon-corretto-headless
fi

log "PostgreSQL 클라이언트 설치 (RDS 스키마 적용용)"
if command -v psql >/dev/null 2>&1; then
    echo "  이미 설치돼 있다: $(psql --version)"
else
    # Amazon Linux 버전에 따라 제공되는 패키지명이 다르다. 있는 것을 쓴다.
    dnf install -y postgresql16 || dnf install -y postgresql15 || \
        warn "psql 설치에 실패했다. RDS 스키마 적용은 다른 경로로 해야 한다."
fi

# ------------------------------------------------------------------------------
# 2. 스왑 2GB
#
# t3.micro 는 RAM 1GB 이고 Amazon Linux 는 기본 스왑이 없다. JVM 힙 512MB 에
# 메타스페이스·코드캐시·스레드 스택이 더해지면 물리 메모리에 근접한다. 스왑이 없으면
# 커널 OOM Killer 가 자바 프로세스를 조용히 죽인다 (로그에 흔적이 거의 남지 않는다).
# ------------------------------------------------------------------------------
log "스왑 확인"
if swapon --show --noheadings | grep -q .; then
    echo "  이미 활성화돼 있다:"
    swapon --show
else
    echo "  ${SWAP_SIZE} 스왑 파일 생성"
    fallocate -l "${SWAP_SIZE}" "${SWAP_FILE}" || dd if=/dev/zero of="${SWAP_FILE}" bs=1M count=2048
    chmod 600 "${SWAP_FILE}"
    mkswap "${SWAP_FILE}"
    swapon "${SWAP_FILE}"
    # 재부팅 후에도 유지되도록 등록한다 (중복 등록 방지)
    grep -q "^${SWAP_FILE}" /etc/fstab || echo "${SWAP_FILE} none swap sw 0 0" >> /etc/fstab
fi

# ------------------------------------------------------------------------------
# 3. 서비스 전용 계정
# ------------------------------------------------------------------------------
log "서비스 계정 확인 (${APP_USER})"
if id -u "${APP_USER}" >/dev/null 2>&1; then
    echo "  이미 존재한다."
else
    # 로그인 불가·홈 없음. 이 계정으로는 SSH 접속이 되지 않는다.
    useradd --system --no-create-home --shell /sbin/nologin "${APP_USER}"
    echo "  생성 완료."
fi

# ------------------------------------------------------------------------------
# 4. 파일 배치
# ------------------------------------------------------------------------------
log "설치 디렉터리 구성 (${INSTALL_DIR})"
install -d -o root -g root -m 755 "${INSTALL_DIR}"
install -d -o root -g root -m 750 "${INSTALL_DIR}/backup"

# jar: 서비스 계정이 읽어야 하므로 그룹 읽기를 준다.
install -o root -g "${APP_USER}" -m 640 "${UPLOAD_DIR}/${JAR_SRC_NAME}" "${JAR_DEST}"

# conf/env: systemd 가 root 로 읽어 환경변수로 넘긴다. 서비스 계정에게 읽기 권한이
# 필요 없으므로 비밀이 든 env 는 600 root:root 로 최대한 좁게 잠근다.
install -o root -g "${APP_USER}" -m 640 "${UPLOAD_DIR}/todolist.conf" "${INSTALL_DIR}/todolist.conf"
install -o root -g root          -m 600 "${UPLOAD_DIR}/todolist.env"  "${INSTALL_DIR}/todolist.env"

# Windows 에서 편집한 파일이 CRLF 로 올라오면 값 끝에 \r 이 붙어
# DB_HOST 등이 조용히 깨진다. 눈에 보이지 않는 사고라 방어적으로 제거한다.
sed -i 's/\r$//' "${INSTALL_DIR}/todolist.conf" "${INSTALL_DIR}/todolist.env"

log "systemd 유닛 등록"
install -o root -g root -m 644 "${SERVICE_SRC}" "${SERVICE_DEST}"
sed -i 's/\r$//' "${SERVICE_DEST}"
systemctl daemon-reload
systemctl enable "${APP_NAME}"

# 재배포 스크립트도 함께 두면 이후 운용이 편하다.
if [[ -f "${SCRIPT_DIR}/redeploy.sh" ]]; then
    install -o root -g root -m 755 "${SCRIPT_DIR}/redeploy.sh" "${INSTALL_DIR}/redeploy.sh"
    echo "  재배포 스크립트: ${INSTALL_DIR}/redeploy.sh"
fi

# ------------------------------------------------------------------------------
# 5. 기동
# ------------------------------------------------------------------------------
SERVER_PORT="$(grep -E '^SERVER_PORT=' "${INSTALL_DIR}/todolist.conf" | cut -d= -f2- | tr -d '[:space:]')"
SERVER_PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${SERVER_PORT}/actuator/health"

if [[ ${START_SERVICE} -eq 0 ]]; then
    log "설치 완료 (--no-start 이므로 기동하지 않았다)"
    cat <<EOF

다음 순서로 진행할 것:

  1) RDS 에 데이터베이스와 스키마를 적용한다
       export PGHOST=\$(grep '^DB_HOST=' ${INSTALL_DIR}/todolist.conf | cut -d= -f2-)
       export PGUSER=\$(grep '^DB_USERNAME=' ${INSTALL_DIR}/todolist.env | cut -d= -f2-)
       psql -d postgres -c "CREATE DATABASE todolist_db;"
       psql -d todolist_db -f ${UPLOAD_DIR}/schema.sql
       psql -d todolist_db -c "\\dt"

  2) 서비스를 기동한다
       sudo systemctl start ${APP_NAME}

  3) 확인
       sudo systemctl status ${APP_NAME}
       curl -s ${HEALTH_URL}
       sudo journalctl -u ${APP_NAME} -n 100 --no-pager

EOF
    exit 0
fi

log "서비스 기동"
systemctl restart "${APP_NAME}"

log "헬스체크 대기 (최대 ${HEALTH_TIMEOUT}초): ${HEALTH_URL}"
deadline=$(( SECONDS + HEALTH_TIMEOUT ))
while (( SECONDS < deadline )); do
    if curl -fsS --max-time 3 "${HEALTH_URL}" 2>/dev/null | grep -q '"status":"UP"'; then
        log "기동 성공. ${APP_NAME} 이 정상 동작 중이다."
        systemctl status "${APP_NAME}" --no-pager -l | head -20
        exit 0
    fi
    sleep 3
done

warn "제한 시간 안에 헬스체크가 UP 이 되지 않았다. 최근 로그:"
journalctl -u "${APP_NAME}" -n 50 --no-pager
die "기동 실패. 위 로그를 확인할 것. (스키마 미적용이면 ddl-auto=validate 오류가 보인다.)"
