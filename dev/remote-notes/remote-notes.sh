#!/usr/bin/env bash
# Control one remote-notes release. The script lives in <release>/bin.
# Password is read from <install>/mysql.env and is never printed.

set -euo pipefail

BIN_DIR="$(cd "$(dirname "$0")" && pwd -P)"
RELEASE="$(cd "${BIN_DIR}/.." && pwd -P)"
ROOT="$(cd "${RELEASE}/../.." && pwd -P)"
PID_FILE="${ROOT}/remote-notes.pid"
LOG_DIR="${ROOT}/logs"
CONSOLE_LOG="${LOG_DIR}/console.log"
ENV_FILE="${ROOT}/mysql.env"
JAVA_BIN="${SF_REMOTE_JAVA:-/home/william-pc/softwares/infinity-sql-spark412-2.4.10-codex/jdk17/bin/java}"
PORT=19110

mkdir -p "${LOG_DIR}" "${RELEASE}/logs" "${RELEASE}/config"

read_password() {
  python3 - "${ENV_FILE}" <<'PY'
import sys
path = sys.argv[1]
password = None
with open(path, "r") as handle:
    for line in handle:
        line = line.strip()
        if line.startswith("SF_REMOTE_MYSQL_PASSWORD="):
            password = line.split("=", 1)[1]
            break
if password is None or password == "":
    sys.exit("mysql.env is missing SF_REMOTE_MYSQL_PASSWORD")
if "\n" in password or "\r" in password:
    sys.exit("mysql password contains a newline")
sys.stdout.write(password)
PY
}

render_config() {
  local password
  password="$(read_password)"
  SF_NOTES_PASSWORD="${password}" python3 - "${RELEASE}" <<'PY'
import os, sys
release = sys.argv[1]
password = os.environ["SF_NOTES_PASSWORD"]
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
template = open(os.path.join(release, "config", "application.yml.template"), "r").read()
if "@PASSWORD@" not in template:
    sys.exit("application.yml.template has no @PASSWORD@ marker")
rendered = template.replace("@PASSWORD@", escaped)
target = os.path.join(release, "config", "application.yml")
with open(target, "w") as handle:
    handle.write(rendered)
os.chmod(target, 0o600)
PY
  unset password
}

apply_schema() {
  local password cnf
  password="$(read_password)"
  cnf="$(mktemp "${RELEASE}/config/.client.XXXXXX")"
  chmod 600 "${cnf}"
  SF_NOTES_PASSWORD="${password}" SF_NOTES_CNF="${cnf}" python3 - <<'PY'
import os
password = os.environ["SF_NOTES_PASSWORD"]
escaped = password.replace("\\", "\\\\").replace('"', '\\"')
with open(os.environ["SF_NOTES_CNF"], "w") as handle:
    handle.write("[client]\n")
    handle.write("protocol=tcp\n")
    handle.write("host=127.0.0.1\n")
    handle.write("port=3306\n")
    handle.write("user=sf_e2e\n")
    handle.write('password="%s"\n' % escaped)
    handle.write("database=sf_serviceframework_e2e\n")
os.chmod(os.environ["SF_NOTES_CNF"], 0o600)
PY
  unset password
  mysql --defaults-extra-file="${cnf}" < "${RELEASE}/schema.sql"
  rm -f "${cnf}"
}

pid_alive() {
  local pid="${1:-}"
  [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null
}

stop_process() {
  if [ ! -f "${PID_FILE}" ]; then
    return 0
  fi
  local pid
  pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if ! pid_alive "${pid}"; then
    rm -f "${PID_FILE}"
    return 0
  fi
  kill "${pid}" 2>/dev/null || true
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    if ! pid_alive "${pid}"; then
      rm -f "${PID_FILE}"
      return 0
    fi
    sleep 1
  done
  kill -9 "${pid}" 2>/dev/null || true
  sleep 1
  rm -f "${PID_FILE}"
}

port_open() {
  python3 - "${PORT}" <<'PY'
import socket, sys
port = int(sys.argv[1])
sock = socket.socket()
sock.settimeout(0.5)
try:
    sock.connect(("127.0.0.1", port))
except Exception:
    sys.exit(1)
finally:
    sock.close()
PY
}

start_process() {
  if [ ! -x "${JAVA_BIN}" ]; then
    echo "JDK not found: ${JAVA_BIN}" >&2
    exit 1
  fi
  render_config
  if [ -f "${PID_FILE}" ] && pid_alive "$(cat "${PID_FILE}")"; then
    echo "already running: $(cat "${PID_FILE}")" >&2
    exit 1
  fi
  if port_open; then
    echo "port ${PORT} is already in use" >&2
    exit 1
  fi
  cd "${RELEASE}"
  nohup "${JAVA_BIN}" -Xms256m -Xmx512m -Dfile.encoding=UTF-8 \
    -cp "${RELEASE}/lib/*:${RELEASE}/remote-notes.jar" \
    net.csdn.remotenotes.RemoteNotes >> "${CONSOLE_LOG}" 2>&1 &
  echo $! > "${PID_FILE}"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60; do
    if python3 - "${PORT}" <<'PY'
import sys, urllib.request
port = sys.argv[1]
try:
    urllib.request.urlopen("http://127.0.0.1:%s/health" % port, timeout=2).read()
except Exception:
    sys.exit(1)
PY
    then
      echo "started pid $(cat "${PID_FILE}") release $(basename "${RELEASE}")"
      return 0
    fi
    if ! pid_alive "$(cat "${PID_FILE}")"; then
      echo "process exited during startup" >&2
      exit 1
    fi
    sleep 1
  done
  echo "health check timed out" >&2
  exit 1
}

case "${1:-}" in
  render-config) render_config ;;
  apply-schema) apply_schema ;;
  start) start_process ;;
  stop) stop_process ;;
  restart)
    stop_process
    start_process
    ;;
  *)
    echo "usage: remote-notes.sh render-config|apply-schema|start|stop|restart" >&2
    exit 2
    ;;
esac
