#!/usr/bin/env bash
# Isolated loopback MySQL 8.0 and MongoDB 4.4 for later ServiceFramework checks.
# Owns only the pid it records. Does not read developer databases or default ports.
set -euo pipefail
set +x

MYSQL_VERSION=8.0.46
MYSQL_ARCHIVE=mysql-8.0.46-macos15-x86_64.tar.gz
MYSQL_URL="https://dev.mysql.com/get/Downloads/MySQL-8.0/${MYSQL_ARCHIVE}"
MYSQL_MD5=a86ee80b624c8574d56ebf0e5e75f8c3
MYSQL_SHA256=8590bc6c3203fe17f51f757dc9132fb755d1b73cbdad9d12a67f85025a3c3b8f
MYSQL_TOP="mysql-${MYSQL_VERSION}-macos15-x86_64"

MONGO_VERSION=4.4.29
MONGO_ARCHIVE="mongodb-macos-x86_64-${MONGO_VERSION}.tgz"
MONGO_URL="https://fastdl.mongodb.org/osx/${MONGO_ARCHIVE}"
MONGO_MD5=711f0d25e5ec95e17085ddb9176510e4
MONGO_SHA256=bf4ab974dec29e70a8b4049260375fc6c671e676f139b55678e5a41c9851aa50
MONGO_TOP="mongodb-macos-x86_64-${MONGO_VERSION}"

CACHE="${SF_COMPAT_CACHE:-${HOME}/Library/Caches/serviceframework-compat-services}"
DOWNLOADS="${CACHE}/downloads"
DIST="${CACHE}/dist"
INSTANCES="${CACHE}/instances"
ACTIVE_FILE="${CACHE}/active-instance"
MYSQL_HOME="${DIST}/${MYSQL_TOP}"
MONGO_HOME="${DIST}/${MONGO_TOP}"
DB_NAME=sf_compat
REPLSET=sfcompat

log() {
  printf '[compat-services] %s\n' "$*" >&2
}

die() {
  log "$*"
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

under_instances() {
  local path="$1"
  case "$path" in
    "${INSTANCES}/"*)
      [ "$path" != "${INSTANCES}" ] && [ "$path" != "${INSTANCES}/" ]
      ;;
    *)
      return 1
      ;;
  esac
}

chmod_private() {
  chmod 600 "$1"
}

ensure_layout() {
  mkdir -p "$DOWNLOADS" "$DIST" "$INSTANCES"
  chmod 700 "$CACHE" "$DOWNLOADS" "$DIST" "$INSTANCES" 2>/dev/null || true
}

file_md5() {
  md5 -q "$1"
}

file_sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

verify_archive() {
  local file="$1" expect_md5="$2" expect_sha="$3" got_md5 got_sha
  got_md5="$(file_md5 "$file")"
  got_sha="$(file_sha256 "$file")"
  if [ "$got_md5" != "$expect_md5" ] || [ "$got_sha" != "$expect_sha" ]; then
    die "checksum mismatch for $(basename "$file")"
  fi
}

ensure_archive() {
  local url="$1" name="$2" expect_md5="$3" expect_sha="$4"
  local dest="${DOWNLOADS}/${name}"
  if [ -f "$dest" ]; then
    verify_archive "$dest" "$expect_md5" "$expect_sha"
    return 0
  fi
  log "downloading ${name}"
  curl -fL --retry 3 --retry-delay 2 -o "${dest}.partial" "$url"
  verify_archive "${dest}.partial" "$expect_md5" "$expect_sha"
  mv "${dest}.partial" "$dest"
  chmod 644 "$dest"
}

ensure_tree() {
  local archive="$1" top="$2" marker="$3"
  local dest="${DIST}/${top}"
  if [ -e "${dest}/${marker}" ]; then
    return 0
  fi
  tar -xzf "${DOWNLOADS}/${archive}" -C "$DIST"
  [ -e "${dest}/${marker}" ] || die "archive ${archive} did not contain ${top}/${marker}"
}

ensure_binaries() {
  ensure_layout
  ensure_archive "$MYSQL_URL" "$MYSQL_ARCHIVE" "$MYSQL_MD5" "$MYSQL_SHA256"
  ensure_archive "$MONGO_URL" "$MONGO_ARCHIVE" "$MONGO_MD5" "$MONGO_SHA256"
  ensure_tree "$MYSQL_ARCHIVE" "$MYSQL_TOP" "bin/mysqld"
  ensure_tree "$MONGO_ARCHIVE" "$MONGO_TOP" "bin/mongod"
  [ -x "${MYSQL_HOME}/bin/mysql" ] || die "mysql client missing"
  [ -x "${MONGO_HOME}/bin/mongo" ] || die "mongo shell missing"
}

active_instance() {
  if [ -f "$ACTIVE_FILE" ]; then
    local path
    path="$(sed -n '1p' "$ACTIVE_FILE")"
    if under_instances "$path" && [ -d "$path" ]; then
      printf '%s\n' "$path"
      return 0
    fi
  fi
  return 1
}

require_instance() {
  active_instance || die "no active instance; run start first"
}

allocate_port() {
  python3 - <<'PY'
import socket
forbidden = {3306, 27017, 33060, 33062}
for _ in range(100):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    finally:
        sock.close()
    if port not in forbidden and port > 1024:
        print(port)
        raise SystemExit(0)
raise SystemExit("no free loopback port")
PY
}

new_instance() {
  local id path
  id="$(date +%Y%m%dT%H%M%S)-$$"
  path="${INSTANCES}/${id}"
  under_instances "$path" || die "refusing instance path"
  mkdir -m 700 "$path" "$path/mysql-data" "$path/mysql-tmp" "$path/mongo-data" "$path/mongo-client-home"
  printf '%s\n' "$path" >"$ACTIVE_FILE"
  chmod 600 "$ACTIVE_FILE"
  printf '%s\n' "$path"
}

env_file_of() {
  printf '%s\n' "$1/env"
}

manifest_of() {
  printf '%s\n' "$1/manifest.json"
}

load_env_var() {
  local key="$1" file="$2" line val
  line="$(grep -E "^${key}=" "$file" | tail -n 1 || true)"
  [ -n "$line" ] || return 1
  val="${line#*=}"
  printf '%s' "$val"
}

write_env_file() {
  local file="$1"
  umask 077
  cat >"$file"
  chmod_private "$file"
}

generate_token() {
  python3 - <<'PY'
import re, secrets, sys
value = secrets.token_urlsafe(24)
if not re.fullmatch(r"[A-Za-z0-9_-]{16,}", value):
    sys.exit("token generator produced an unexpected value")
print(value)
PY
}

generate_user() {
  python3 - <<'PY'
import secrets
print("u" + secrets.token_hex(4))
PY
}

write_credentials() {
  local inst="$1" env_file user mysql_pass mongo_pass admin_pass
  env_file="$(env_file_of "$inst")"
  if [ -f "$env_file" ]; then
    return 0
  fi
  user="$(generate_user)"
  mysql_pass="$(generate_token)"
  mongo_pass="$(generate_token)"
  admin_pass="$(generate_token)"
  write_env_file "$env_file" <<EOF
SF_COMPAT_MYSQL_HOST=127.0.0.1
SF_COMPAT_MYSQL_PORT=
SF_COMPAT_MYSQL_USER=${user}
SF_COMPAT_MYSQL_PASSWORD=${mysql_pass}
SF_COMPAT_MYSQL_ADMIN_PASSWORD=${admin_pass}
SF_COMPAT_MYSQL_DATABASE=${DB_NAME}
SF_COMPAT_MYSQL_SOCKET=${inst}/mysql.sock
SF_COMPAT_MYSQL_CLIENT_CNF=${inst}/mysql-client.cnf
SF_COMPAT_MYSQL_ADMIN_CNF=${inst}/mysql-admin.cnf
SF_COMPAT_MONGO_HOST=127.0.0.1
SF_COMPAT_MONGO_PORT=
SF_COMPAT_MONGO_USER=${user}
SF_COMPAT_MONGO_PASSWORD=${mongo_pass}
SF_COMPAT_MONGO_AUTH_DB=admin
SF_COMPAT_MONGO_DATABASE=${DB_NAME}
SF_COMPAT_MONGO_REPLSET=${REPLSET}
EOF
}

set_env_port() {
  local env_file="$1" key="$2" port="$3"
  python3 - "$env_file" "$key" "$port" <<'PY'
import sys
path, key, port = sys.argv[1:]
lines = open(path).read().splitlines()
out = []
found = False
for line in lines:
    if line.startswith(key + "="):
        out.append(key + "=" + port)
        found = True
    else:
        out.append(line)
if not found:
    out.append(key + "=" + port)
open(path, "w").write("\n".join(out) + "\n")
PY
  chmod_private "$env_file"
}

assert_saved_credential() {
  local value="$1" label="$2"
  printf '%s' "$value" | grep -E '^[A-Za-z0-9_-]{8,}$' >/dev/null || die "${label} is missing or unusable"
}

client_mysql() {
  local inst="$1"
  shift
  MYSQL_HISTFILE=/dev/null HOME="${inst}/mongo-client-home" \
    "${MYSQL_HOME}/bin/mysql" --defaults-file="${inst}/mysql-client.cnf" "$@"
}

admin_mysql() {
  local inst="$1"
  shift
  MYSQL_HISTFILE=/dev/null HOME="${inst}/mongo-client-home" \
    "${MYSQL_HOME}/bin/mysql" --defaults-file="${inst}/mysql-admin.cnf" "$@"
}

mysql_socket_for() {
  local inst="$1" id dir sock
  id="$(basename "$inst")"
  case "$id" in
    ""|*[!A-Za-z0-9_-]*) die "unexpected instance id" ;;
  esac
  dir="/tmp/sfcs-${id}"
  if [ -L "$dir" ] || { [ -e "$dir" ] && [ ! -d "$dir" ]; }; then
    die "refusing unsafe mysql socket directory"
  fi
  if [ ! -d "$dir" ]; then
    mkdir -m 700 "$dir" || die "cannot create mysql socket directory"
  fi
  chmod 700 "$dir" || die "cannot restrict mysql socket directory"
  python3 - "$dir" <<'PY' || die "mysql socket directory is not private"
import os, sys
path = sys.argv[1]
st = os.lstat(path)
if not os.path.isdir(path) or os.path.islink(path):
    sys.exit(1)
if st.st_uid != os.getuid():
    sys.exit(1)
if st.st_mode & 0o077:
    sys.exit(1)
PY
  sock="${dir}/mysql.sock"
  python3 - "$sock" <<'PY' || die "mysql socket path is too long"
import sys
if len(sys.argv[1].encode()) > 100:
    sys.exit(1)
PY
  printf '%s\n' "$sock"
}

write_mysql_cnf() {
  local inst="$1" port="$2" kind="$3" user="$4" password="$5" socket="$6" dest
  if [ "$kind" = "admin" ]; then
    dest="${inst}/mysql-admin.cnf"
  else
    dest="${inst}/mysql-client.cnf"
  fi
  umask 077
  if [ "$kind" = "admin" ]; then
    cat >"$dest" <<EOF
[client]
socket=${socket}
user=${user}
password=${password}
EOF
  else
    cat >"$dest" <<EOF
[client]
protocol=TCP
host=127.0.0.1
port=${port}
user=${user}
password=${password}
database=${DB_NAME}
EOF
  fi
  chmod_private "$dest"
}

write_server_cnf() {
  local inst="$1" port="$2" socket="$3"
  cat >"${inst}/mysql-server.cnf" <<EOF
[mysqld]
basedir=${MYSQL_HOME}
datadir=${inst}/mysql-data
tmpdir=${inst}/mysql-tmp
bind-address=127.0.0.1
port=${port}
socket=${socket}
pid-file=${inst}/mysql.pid
mysqlx=0
default-authentication-plugin=mysql_native_password
authentication-policy=mysql_native_password
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
innodb-buffer-pool-size=64M
performance-schema=OFF
skip-name-resolve
disable-log-bin
secure-file-priv=${inst}/mysql-tmp
log-error=${inst}/mysql-error.log
max-connections=50
EOF
  chmod 600 "${inst}/mysql-server.cnf"
}

process_lstart() {
  ps -ww -p "$1" -o lstart= 2>/dev/null | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

process_command() {
  ps -ww -p "$1" -o command= 2>/dev/null || true
}

write_proc_meta() {
  local meta="$1" pid="$2" bin="$3" datadir="$4" started i
  i=0
  started=""
  while [ "$i" -lt 20 ]; do
    started="$(process_lstart "$pid" || true)"
    [ -n "$started" ] && break
    sleep 0.1
    i=$((i + 1))
  done
  [ -n "$started" ] || return 1
  cat >"$meta" <<EOF
pid=${pid}
bin=${bin}
datadir=${datadir}
lstart=${started}
EOF
  chmod 600 "$meta"
}

process_matches_meta() {
  local meta="$1" pid bin datadir started cmd current
  [ -f "$meta" ] || return 1
  pid="$(load_env_var pid "$meta")" || return 1
  bin="$(load_env_var bin "$meta")" || return 1
  datadir="$(load_env_var datadir "$meta")" || return 1
  started="$(load_env_var lstart "$meta")" || return 1
  [ "$pid" -gt 1 ] 2>/dev/null || return 1
  current="$(process_lstart "$pid")"
  [ "$current" = "$started" ] || return 1
  cmd="$(process_command "$pid")"
  case "$cmd" in
    *"$bin"*) ;;
    *) return 1 ;;
  esac
  case "$cmd" in
    *"$datadir"*) ;;
    *) return 1 ;;
  esac
  printf '%s\n' "$pid"
}

stop_owned() {
  local meta="$1" pid i
  if ! pid="$(process_matches_meta "$meta")"; then
    return 0
  fi
  kill -TERM "$pid" 2>/dev/null || true
  i=0
  while [ "$i" -lt 40 ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      return 0
    fi
    sleep 0.5
    i=$((i + 1))
  done
  if pid="$(process_matches_meta "$meta")"; then
    kill -KILL "$pid" 2>/dev/null || true
    sleep 0.5
  fi
  if kill -0 "$pid" 2>/dev/null && process_matches_meta "$meta" >/dev/null; then
    die "owned process ${pid} did not exit"
  fi
}

assert_loopback() {
  local pid="$1" port="$2" lines
  lines="$(lsof -nP -a -p "$pid" -iTCP -sTCP:LISTEN 2>/dev/null || true)"
  [ -n "$lines" ] || die "pid ${pid} is not listening"
  printf '%s\n' "$lines" | grep -E '127\.0\.0\.1:'"${port}"' ' >/dev/null \
    || die "pid ${pid} is not bound to 127.0.0.1:${port}"
  if printf '%s\n' "$lines" | grep -E '[*][:]|0\.0\.0\.0:|\[::\]:' >/dev/null; then
    die "pid ${pid} has a non-loopback listener"
  fi
  if printf '%s\n' "$lines" | grep -E ':(3306|27017) ' >/dev/null; then
    die "pid ${pid} is listening on a default database port"
  fi
}

wipe_unmarked_datadir() {
  local path="$1" marker="$2"
  under_instances "$path" || die "refusing to clean ${path}"
  [ ! -f "$marker" ] || die "refusing to clean marked datadir ${path}"
  [ -d "$path" ] || die "missing datadir ${path}"
  find "$path" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
}

start_mysqld() {
  local inst="$1" port="$2" pid
  if pid="$(process_matches_meta "${inst}/mysql.pid.meta" 2>/dev/null || true)" && [ -n "${pid:-}" ]; then
    printf '%s\n' "$pid"
    return 0
  fi
  : >"${inst}/mysql-error.log"
  nohup "${MYSQL_HOME}/bin/mysqld" --defaults-file="${inst}/mysql-server.cnf" \
    --datadir="${inst}/mysql-data" >>"${inst}/mysql-error.log" 2>&1 &
  pid=$!
  disown "$pid" 2>/dev/null || true
  if ! write_proc_meta "${inst}/mysql.pid.meta" "$pid" "${MYSQL_HOME}/bin/mysqld" "${inst}/mysql-data"; then
    kill -TERM "$pid" 2>/dev/null || true
    die "mysql process disappeared before it could be recorded"
  fi
  printf '%s\n' "$pid" >"${inst}/mysql.pid"
  chmod 600 "${inst}/mysql.pid"
  printf '%s\n' "$pid"
}

wait_mysql() {
  local inst="$1" pid="$2" i
  i=0
  while [ "$i" -lt 60 ]; do
    if MYSQL_HISTFILE=/dev/null "${MYSQL_HOME}/bin/mysqladmin" \
      --defaults-file="${inst}/mysql-admin.cnf" --silent ping >/dev/null 2>&1; then
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      return 1
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

bootstrap_mysql() {
  local inst="$1" port="$2" env_file user app_pass admin_pass sql
  env_file="$(env_file_of "$inst")"
  user="$(load_env_var SF_COMPAT_MYSQL_USER "$env_file")"
  app_pass="$(load_env_var SF_COMPAT_MYSQL_PASSWORD "$env_file")"
  admin_pass="$(load_env_var SF_COMPAT_MYSQL_ADMIN_PASSWORD "$env_file")"
  assert_saved_credential "$user" "mysql user"
  assert_saved_credential "$app_pass" "mysql password"
  assert_saved_credential "$admin_pass" "mysql admin password"
  sql="${inst}/mysql-bootstrap.sql"
  umask 077
  cat >"$sql" <<EOF
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER '${user}'@'127.0.0.1' IDENTIFIED WITH mysql_native_password BY '${app_pass}';
CREATE USER '${user}'@'localhost' IDENTIFIED WITH mysql_native_password BY '${app_pass}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${user}'@'127.0.0.1';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${user}'@'localhost';
ALTER USER 'root'@'localhost' IDENTIFIED WITH mysql_native_password BY '${admin_pass}';
DELETE FROM mysql.user WHERE User='';
FLUSH PRIVILEGES;
EOF
  chmod_private "$sql"
  if ! admin_mysql "$inst" --batch <"$sql" >"${inst}/mysql-bootstrap.out" 2>"${inst}/mysql-bootstrap.err"; then
    rm -f "$sql"
    die "mysql bootstrap failed; details kept in the instance directory"
  fi
  rm -f "$sql"
  write_mysql_cnf "$inst" "$port" admin root "$admin_pass" "$(load_env_var SF_COMPAT_MYSQL_SOCKET "$(env_file_of "$inst")")"
  write_mysql_cnf "$inst" "$port" client "$user" "$app_pass" "$(load_env_var SF_COMPAT_MYSQL_SOCKET "$(env_file_of "$inst")")"
  : >"${inst}/mysql.bootstrapped"
  chmod 600 "${inst}/mysql.bootstrapped"
}

prepare_mysql() {
  local inst="$1" port="${2:-}" pid socket
  port="${port:-$(load_env_var SF_COMPAT_MYSQL_PORT "$(env_file_of "$inst")" || true)}"
  if [ -z "$port" ]; then
    port="$(allocate_port)"
    set_env_port "$(env_file_of "$inst")" SF_COMPAT_MYSQL_PORT "$port"
  fi
  case "$port" in
    3306|27017|33060|33062) die "refusing default port ${port}" ;;
  esac
  socket="$(mysql_socket_for "$inst")"
  set_env_port "$(env_file_of "$inst")" SF_COMPAT_MYSQL_SOCKET "$socket"
  if ! process_matches_meta "${inst}/mysql.pid.meta" >/dev/null 2>&1; then
    rm -f "$socket"
  fi
  write_server_cnf "$inst" "$port" "$socket"
  if [ ! -f "${inst}/mysql.bootstrapped" ]; then
    wipe_unmarked_datadir "${inst}/mysql-data" "${inst}/mysql.bootstrapped"
    write_mysql_cnf "$inst" "$port" admin root "" "$socket"
    log "initializing mysql data directory"
    if ! "${MYSQL_HOME}/bin/mysqld" --defaults-file="${inst}/mysql-server.cnf" \
      --initialize-insecure --datadir="${inst}/mysql-data" \
      >"${inst}/mysql-init.out" 2>"${inst}/mysql-init.err"; then
      die "mysql initialize failed; see mysql-init.err in the instance directory"
    fi
  else
    local env_file user app_pass admin_pass
    env_file="$(env_file_of "$inst")"
    user="$(load_env_var SF_COMPAT_MYSQL_USER "$env_file")"
    app_pass="$(load_env_var SF_COMPAT_MYSQL_PASSWORD "$env_file")"
    admin_pass="$(load_env_var SF_COMPAT_MYSQL_ADMIN_PASSWORD "$env_file")"
    write_mysql_cnf "$inst" "$port" admin root "$admin_pass" "$socket"
    write_mysql_cnf "$inst" "$port" client "$user" "$app_pass" "$socket"
  fi
  pid="$(start_mysqld "$inst" "$port")"
  if ! wait_mysql "$inst" "$pid"; then
    stop_owned "${inst}/mysql.pid.meta" || true
    die "mysql did not become ready"
  fi
  assert_loopback "$pid" "$port"
  if [ ! -f "${inst}/mysql.bootstrapped" ]; then
    bootstrap_mysql "$inst" "$port"
    if ! wait_mysql "$inst" "$pid"; then
      stop_owned "${inst}/mysql.pid.meta" || true
      die "mysql rejected the bootstrapped account"
    fi
  fi
}

mongo_client() {
  local inst="$1" port="$2"
  shift 2
  HOME="${inst}/mongo-client-home" \
    "${MONGO_HOME}/bin/mongo" --quiet --norc --host 127.0.0.1 --port "$port" "$@"
}

cleanup_mongo_history() {
  rm -f "$1/mongo-client-home/.dbshell" "$1/mongo-client-home/.mongorc.js"
}

mongo_eval_file() {
  local inst="$1" port="$2" js="$3" outfile="$4" env_file user pass
  env_file="$(env_file_of "$inst")"
  if [ -f "${inst}/mongo.bootstrapped" ]; then
    user="$(load_env_var SF_COMPAT_MONGO_USER "$env_file")"
    pass="$(load_env_var SF_COMPAT_MONGO_PASSWORD "$env_file")"
    assert_saved_credential "$user" "mongo user"
    assert_saved_credential "$pass" "mongo password"
    {
      printf 'if (!db.auth("%s", "%s")) { quit(1); }\n' "$user" "$pass"
      cat "$js"
    } | mongo_client "$inst" "$port" admin >"$outfile" 2>>"${inst}/mongo-client.err"
  else
    mongo_client "$inst" "$port" admin <"$js" >"$outfile" 2>>"${inst}/mongo-client.err"
  fi
  local status=$?
  cleanup_mongo_history "$inst"
  return "$status"
}

ensure_mongo_key() {
  local key="$1"
  umask 077
  python3 - "$key" <<'PY'
import base64, pathlib, re, secrets, sys
path = pathlib.Path(sys.argv[1])
current = path.read_text() if path.exists() else ""
body = current.strip()
if not re.fullmatch(r"[A-Za-z0-9+/=]+", body) or not (6 <= len(body) <= 1024):
    path.write_text(base64.b64encode(secrets.token_bytes(48)).decode() + "\n")
PY
  chmod 600 "$key" || die "cannot restrict mongo key file"
  python3 - "$key" <<'PY' || die "mongo key file is not private"
import os, sys
st = os.lstat(sys.argv[1])
if not os.path.isfile(sys.argv[1]) or os.path.islink(sys.argv[1]):
    sys.exit(1)
if st.st_uid != os.getuid() or st.st_mode & 0o077:
    sys.exit(1)
size = st.st_size
if size < 6 or size > 1024:
    sys.exit(1)
PY
}

start_mongod() {
  local inst="$1" port="$2" auth_flag="$3" pid key
  if pid="$(process_matches_meta "${inst}/mongo.pid.meta" 2>/dev/null || true)" && [ -n "${pid:-}" ]; then
    printf '%s\n' "$pid"
    return 0
  fi
  key="${inst}/mongo.key"
  if [ -n "$auth_flag" ]; then
    ensure_mongo_key "$key"
    nohup "${MONGO_HOME}/bin/mongod" \
      --bind_ip 127.0.0.1 \
      --port "$port" \
      --dbpath "${inst}/mongo-data" \
      --pidfilepath "${inst}/mongo.pid" \
      --logpath "${inst}/mongo.log" \
      --logappend \
      --nounixsocket \
      --replSet "$REPLSET" \
      --oplogSize 128 \
      --wiredTigerCacheSizeGB 0.25 \
      --auth \
      --keyFile "$key" \
      >>"${inst}/mongo-supervisor.log" 2>&1 &
  else
    nohup "${MONGO_HOME}/bin/mongod" \
      --bind_ip 127.0.0.1 \
      --port "$port" \
      --dbpath "${inst}/mongo-data" \
      --pidfilepath "${inst}/mongo.pid" \
      --logpath "${inst}/mongo.log" \
      --logappend \
      --nounixsocket \
      --replSet "$REPLSET" \
      --oplogSize 128 \
      --wiredTigerCacheSizeGB 0.25 \
      >>"${inst}/mongo-supervisor.log" 2>&1 &
  fi
  pid=$!
  disown "$pid" 2>/dev/null || true
  if ! write_proc_meta "${inst}/mongo.pid.meta" "$pid" "${MONGO_HOME}/bin/mongod" "${inst}/mongo-data"; then
    kill -TERM "$pid" 2>/dev/null || true
    die "mongo process disappeared before it could be recorded"
  fi
  chmod 600 "${inst}/mongo.pid" 2>/dev/null || true
  printf '%s\n' "$pid"
}

wait_mongo_ping() {
  local inst="$1" port="$2" pid="$3" i js
  js="${inst}/mongo-ping.js"
  printf '%s\n' 'var p = db.runCommand({ping: 1}); if (!p || p.ok != 1) quit(2);' >"$js"
  i=0
  while [ "$i" -lt 40 ]; do
    if [ -f "${inst}/mongo.bootstrapped" ]; then
      if mongo_eval_file "$inst" "$port" "$js" "${inst}/mongo-ping.out"; then
        return 0
      fi
    elif mongo_client "$inst" "$port" admin <"$js" >"${inst}/mongo-ping.out" 2>>"${inst}/mongo-client.err"; then
      cleanup_mongo_history "$inst"
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      return 1
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

bootstrap_mongo() {
  local inst="$1" port="$2" env_file user pass js
  env_file="$(env_file_of "$inst")"
  user="$(load_env_var SF_COMPAT_MONGO_USER "$env_file")"
  pass="$(load_env_var SF_COMPAT_MONGO_PASSWORD "$env_file")"
  assert_saved_credential "$user" "mongo user"
  assert_saved_credential "$pass" "mongo password"
  js="${inst}/mongo-init.js"
  umask 077
  cat >"$js" <<EOF
var result = rs.initiate({_id: "${REPLSET}", members: [{_id: 0, host: "127.0.0.1:${port}"}]});
if (!result.ok && result.codeName != "AlreadyInitialized") {
  quit(2);
}
var ready = false;
for (var i = 0; i < 30; i++) {
  var state = db.isMaster();
  if (state.ismaster) { ready = true; break; }
  sleep(1000);
}
if (!ready) quit(3);
db.getSiblingDB("admin").createUser({
  user: "${user}",
  pwd: "${pass}",
  roles: [
    {role: "readWrite", db: "${DB_NAME}"},
    {role: "dbAdmin", db: "${DB_NAME}"}
  ]
});
EOF
  chmod_private "$js"
  if ! mongo_client "$inst" "$port" admin <"$js" >"${inst}/mongo-init.out" 2>"${inst}/mongo-init.err"; then
    cleanup_mongo_history "$inst"
    rm -f "$js"
    die "mongo bootstrap failed; details kept in the instance directory"
  fi
  cleanup_mongo_history "$inst"
  rm -f "$js"
  : >"${inst}/mongo.bootstrapped"
  chmod 600 "${inst}/mongo.bootstrapped"
}

prepare_mongo() {
  local inst="$1" port pid auth_flag
  port="${port:-$(load_env_var SF_COMPAT_MONGO_PORT "$(env_file_of "$inst")" || true)}"
  if [ -z "$port" ]; then
    port="$(allocate_port)"
    set_env_port "$(env_file_of "$inst")" SF_COMPAT_MONGO_PORT "$port"
  fi
  case "$port" in
    3306|27017|33060|33062) die "refusing default port ${port}" ;;
  esac
  auth_flag=""
  if [ -f "${inst}/mongo.bootstrapped" ]; then
    auth_flag="--auth"
  else
    wipe_unmarked_datadir "${inst}/mongo-data" "${inst}/mongo.bootstrapped"
  fi
  pid="$(start_mongod "$inst" "$port" "$auth_flag")"
  if ! wait_mongo_ping "$inst" "$port" "$pid"; then
    stop_owned "${inst}/mongo.pid.meta" || true
    die "mongo did not become ready"
  fi
  assert_loopback "$pid" "$port"
  if [ ! -f "${inst}/mongo.bootstrapped" ]; then
    bootstrap_mongo "$inst" "$port"
    stop_owned "${inst}/mongo.pid.meta"
    pid="$(start_mongod "$inst" "$port" "--auth")"
    if ! wait_mongo_ping "$inst" "$port" "$pid"; then
      stop_owned "${inst}/mongo.pid.meta" || true
      die "mongo did not accept the bootstrapped account"
    fi
    assert_loopback "$pid" "$port"
  fi
}

write_manifest() {
  local inst="$1"
  python3 - "$inst" "$(manifest_of "$inst")" "$(env_file_of "$inst")" "$MYSQL_HOME" "$MONGO_HOME" "$MYSQL_VERSION" "$MONGO_VERSION" <<'PY'
import json, sys
inst, manifest, env_file, mysql_home, mongo_home, mysql_version, mongo_version = sys.argv[1:]
def port(name):
    for line in open(env_file):
        if line.startswith(name + "="):
            return int(line.split("=", 1)[1].strip())
    raise SystemExit("missing " + name)
doc = {
    "mysql": {
        "version": mysql_version,
        "binary": mysql_home + "/bin/mysqld",
        "cli": mysql_home + "/bin/mysql",
        "host": "127.0.0.1",
        "port": port("SF_COMPAT_MYSQL_PORT"),
        "datadir": inst + "/mysql-data",
        "pidfile": inst + "/mysql.pid",
        "env_file": env_file,
    },
    "mongo": {
        "version": mongo_version,
        "binary": mongo_home + "/bin/mongod",
        "cli": mongo_home + "/bin/mongo",
        "host": "127.0.0.1",
        "port": port("SF_COMPAT_MONGO_PORT"),
        "datadir": inst + "/mongo-data",
        "pidfile": inst + "/mongo.pid",
        "env_file": env_file,
        "replset": "sfcompat",
    },
}
with open(manifest, "w") as handle:
    json.dump(doc, handle, indent=2)
    handle.write("\n")
PY
  chmod 644 "$(manifest_of "$inst")"
}

quiet_stop() {
  local inst sock
  inst="$(active_instance 2>/dev/null || true)" || true
  [ -n "${inst:-}" ] || return 0
  sock="$(load_env_var SF_COMPAT_MYSQL_SOCKET "$(env_file_of "$inst")" 2>/dev/null || true)"
  if [ -n "$sock" ] && [ -S "$sock" ] && [ -f "${inst}/mysql-admin.cnf" ]; then
    MYSQL_HISTFILE=/dev/null "${MYSQL_HOME}/bin/mysqladmin" \
      --defaults-file="${inst}/mysql-admin.cnf" shutdown >/dev/null 2>&1 || true
  fi
  stop_owned "${inst}/mysql.pid.meta" || true
  stop_owned "${inst}/mongo.pid.meta" || true
}

# bash 3.2 uses the EXIT trap's own status as the script status. Capture the
# incoming status first, then stop owned servers, then exit with that status
# so a signal is not reported as success.
on_exit_stop_services() {
  local status=$?
  trap - EXIT
  set +e
  quiet_stop
  exit "$status"
}

cmd_start() {
  local inst
  ensure_binaries
  if ! inst="$(active_instance)"; then
    inst="$(new_instance)"
  fi
  write_credentials "$inst"
  trap 'on_exit_stop_services' EXIT
  if ! prepare_mysql "$inst"; then
    stop_owned "${inst}/mysql.pid.meta" || true
    stop_owned "${inst}/mongo.pid.meta" || true
    die "mysql start failed"
  fi
  if ! prepare_mongo "$inst"; then
    stop_owned "${inst}/mysql.pid.meta" || true
    stop_owned "${inst}/mongo.pid.meta" || true
    die "mongo start failed"
  fi
  write_manifest "$inst"
  trap - EXIT
  log "started"
  log "manifest=$(manifest_of "$inst")"
  log "env_file=$(env_file_of "$inst")"
}

service_state() {
  local meta="$1"
  if process_matches_meta "$meta" >/dev/null 2>&1; then
    printf 'running\n'
  else
    printf 'stopped\n'
  fi
}

cmd_status() {
  local inst mysql_state mongo_state mysql_port mongo_port env_file
  if ! inst="$(active_instance)"; then
    printf 'state=absent\ncache=%s\n' "$CACHE"
    return 0
  fi
  env_file="$(env_file_of "$inst")"
  mysql_state="$(service_state "${inst}/mysql.pid.meta")"
  mongo_state="$(service_state "${inst}/mongo.pid.meta")"
  mysql_port="$(load_env_var SF_COMPAT_MYSQL_PORT "$env_file" 2>/dev/null || true)"
  mongo_port="$(load_env_var SF_COMPAT_MONGO_PORT "$env_file" 2>/dev/null || true)"
  printf 'state=present\n'
  printf 'instance=%s\n' "$inst"
  printf 'manifest=%s\n' "$(manifest_of "$inst")"
  printf 'env_file=%s\n' "$env_file"
  printf 'mysql_state=%s\n' "$mysql_state"
  printf 'mysql_port=%s\n' "${mysql_port:-}"
  printf 'mongo_state=%s\n' "$mongo_state"
  printf 'mongo_port=%s\n' "${mongo_port:-}"
  printf 'mysql_version=%s\n' "$MYSQL_VERSION"
  printf 'mongo_version=%s\n' "$MONGO_VERSION"
}

cmd_stop() {
  local inst
  if ! inst="$(active_instance)"; then
    log "no active instance"
    return 0
  fi
  local sock
  sock="$(load_env_var SF_COMPAT_MYSQL_SOCKET "$(env_file_of "$inst")" 2>/dev/null || true)"
  if [ -n "$sock" ] && [ -S "$sock" ] && [ -f "${inst}/mysql-admin.cnf" ]; then
    MYSQL_HISTFILE=/dev/null "${MYSQL_HOME}/bin/mysqladmin" \
      --defaults-file="${inst}/mysql-admin.cnf" shutdown >/dev/null 2>&1 || true
  fi
  stop_owned "${inst}/mysql.pid.meta" || true
  stop_owned "${inst}/mongo.pid.meta" || true
  local i=0
  while [ "$i" -lt 20 ]; do
    if [ "$(service_state "${inst}/mysql.pid.meta")" = "stopped" ] \
      && [ "$(service_state "${inst}/mongo.pid.meta")" = "stopped" ]; then
      log "stopped"
      return 0
    fi
    sleep 0.5
    i=$((i + 1))
  done
  die "owned services are still running"
}

filter_compat_lines() {
  grep -E '^COMPAT_[A-Z0-9_]+( .*)?$' || true
}

cmd_verify() {
  local inst env_file mysql_port mongo_port report js status
  inst="$(require_instance)"
  [ "$(service_state "${inst}/mysql.pid.meta")" = "running" ] || die "mysql is not running"
  [ "$(service_state "${inst}/mongo.pid.meta")" = "running" ] || die "mongo is not running"
  env_file="$(env_file_of "$inst")"
  mysql_port="$(load_env_var SF_COMPAT_MYSQL_PORT "$env_file")"
  mongo_port="$(load_env_var SF_COMPAT_MONGO_PORT "$env_file")"
  report="${inst}/verify-last.txt"
  umask 077
  {
    printf 'mysql_client_exit='
    if client_mysql "$inst" --batch --raw --skip-column-names <<SQL >"${inst}/mysql-verify.out" 2>"${inst}/mysql-verify.err"
SELECT CONCAT('COMPAT_VERSION ', VERSION());
SELECT 'COMPAT_PING_OK';
CREATE TABLE IF NOT EXISTS compat_probe (
  id INT NOT NULL PRIMARY KEY,
  note VARCHAR(64) NOT NULL
);
INSERT INTO compat_probe (id, note) VALUES (1, 'ok')
  ON DUPLICATE KEY UPDATE note='ok';
SELECT CONCAT('COMPAT_RW ', note) FROM compat_probe WHERE id=1;
START TRANSACTION;
UPDATE compat_probe SET note='tx' WHERE id=1;
ROLLBACK;
SELECT CONCAT('COMPAT_TX ', note) FROM compat_probe WHERE id=1;
SQL
    then
      printf '0\n'
    else
      status=$?
      printf '%s\n' "$status"
    fi
    filter_compat_lines <"${inst}/mysql-verify.out"
    printf 'mongo_client_exit='
    js="${inst}/mongo-verify.js"
    cat >"$js" <<EOF
var ping = db.runCommand({ping: 1});
if (!ping || ping.ok != 1) quit(2);
print("COMPAT_PING_OK");
var probe = db.getSiblingDB("${DB_NAME}").compat_probe;
probe.update({_id: "probe"}, {\$set: {note: "ok"}}, {upsert: true});
var doc = probe.findOne({_id: "probe"});
if (!doc || doc.note != "ok") quit(3);
print("COMPAT_RW_OK");
var session = db.getMongo().startSession();
var sdb = session.getDatabase("${DB_NAME}");
session.startTransaction();
sdb.compat_probe.update({_id: "probe"}, {\$set: {note: "tx"}});
session.abortTransaction();
session.endSession();
var after = probe.findOne({_id: "probe"});
if (!after || after.note != "ok") quit(4);
print("COMPAT_TX_OK");
print("COMPAT_VERSION " + db.version());
EOF
    if mongo_eval_file "$inst" "$mongo_port" "$js" "${inst}/mongo-verify.out"; then
      printf '0\n'
    else
      status=$?
      printf '%s\n' "$status"
    fi
    filter_compat_lines <"${inst}/mongo-verify.out"
    printf 'mysql_server_version=%s\n' "$("${MYSQL_HOME}/bin/mysqld" --version 2>/dev/null || true)"
    printf 'mongo_server_version_exit='
    if "${MONGO_HOME}/bin/mongod" --version >"${inst}/mongo-version.out" 2>"${inst}/mongo-version.err"; then
      printf '0\n'
    else
      status=$?
      printf '%s\n' "$status"
    fi
    sed -n '1p' "${inst}/mongo-version.out"
  } >"$report"
  chmod 644 "$report"
  cat "$report"
  grep -F -x 'COMPAT_PING_OK' "$report" >/dev/null || return 1
  grep -F -x 'COMPAT_RW ok' "$report" >/dev/null || return 1
  grep -F -x 'COMPAT_TX ok' "$report" >/dev/null || return 1
  grep -F -x 'COMPAT_RW_OK' "$report" >/dev/null || return 1
  grep -F -x 'COMPAT_TX_OK' "$report" >/dev/null || return 1
  grep -E '^mysql_client_exit=0$' "$report" >/dev/null || return 1
  grep -E '^mongo_client_exit=0$' "$report" >/dev/null || return 1
}

cmd_mysql_cli() {
  local inst
  inst="$(require_instance)"
  [ "$(service_state "${inst}/mysql.pid.meta")" = "running" ] || die "mysql is not running"
  client_mysql "$inst" "$@"
}

cmd_mongo_eval() {
  local inst port js
  [ ! -t 0 ] || die "mongo-eval reads JavaScript on stdin"
  inst="$(require_instance)"
  [ "$(service_state "${inst}/mongo.pid.meta")" = "running" ] || die "mongo is not running"
  port="$(load_env_var SF_COMPAT_MONGO_PORT "$(env_file_of "$inst")")"
  js="${inst}/mongo-eval-stdin.js"
  cat >"$js"
  chmod_private "$js"
  local status
  mongo_eval_file "$inst" "$port" "$js" "${inst}/mongo-eval.out"
  status=$?
  grep -v 'db\.auth(' "${inst}/mongo-eval.out" || true
  return "$status"
}

cmd_run() {
  local inst status stop_status
  [ "$#" -gt 0 ] || die "run requires a command"
  cmd_start
  inst="$(require_instance)"
  # bash 3.2 does not run a trapped signal until the foreground command
  # returns, and `wait` does not return until the child exits. A TERM sent to
  # this script would otherwise sit for the whole child lifetime and leave
  # MySQL and Mongo up. Sleep briefly so the trap runs, then signal the child
  # pid recorded below (not a parent launcher). Exit 128+signal.
  trap 'on_exit_stop_services' EXIT
  set +e
  SF_COMPAT_ENV_FILE="$(env_file_of "$inst")" \
    SF_COMPAT_MANIFEST="$(manifest_of "$inst")" \
    SF_COMPAT_MYSQL_HOST=127.0.0.1 \
    SF_COMPAT_MYSQL_PORT="$(load_env_var SF_COMPAT_MYSQL_PORT "$(env_file_of "$inst")")" \
    SF_COMPAT_MYSQL_DATABASE="$DB_NAME" \
    SF_COMPAT_MONGO_HOST=127.0.0.1 \
    SF_COMPAT_MONGO_PORT="$(load_env_var SF_COMPAT_MONGO_PORT "$(env_file_of "$inst")")" \
    SF_COMPAT_MONGO_DATABASE="$DB_NAME" \
    SF_COMPAT_MONGO_REPLSET="$REPLSET" \
    MYSQL_HISTFILE=/dev/null \
    "$@" &
  SF_COMPAT_RUN_CHILD=$!
  trap 'kill -INT "$SF_COMPAT_RUN_CHILD" 2>/dev/null || true; exit 130' INT
  trap 'kill -TERM "$SF_COMPAT_RUN_CHILD" 2>/dev/null || true; exit 143' TERM
  while kill -0 "$SF_COMPAT_RUN_CHILD" 2>/dev/null; do
    sleep 0.2
  done
  wait "$SF_COMPAT_RUN_CHILD"
  status=$?
  SF_COMPAT_RUN_CHILD=
  trap - INT TERM
  cmd_stop
  stop_status=$?
  trap - EXIT
  set -e
  [ "$status" -eq 0 ] || return "$status"
  return "$stop_status"
}

usage() {
  cat <<EOF
Usage: dev/compat-services.sh <command>

  start              download official binaries if needed, start loopback servers
  status             show instance paths, ports, and owned-process state
  stop               stop only the pid recorded for this instance
  verify             ping, write, read, and roll back a transaction on each server
  run -- command     start, run command, then stop
  mysql-cli -- args  mysql client using the private defaults file
  mongo-eval         run stdin JavaScript after authenticating

Credentials stay in the instance env file (mode 600). Status prints that path
and does not print the credential values.
EOF
}

main() {
  need_cmd curl
  need_cmd python3
  need_cmd tar
  need_cmd lsof
  local cmd="${1:-}"
  case "$cmd" in
    start) cmd_start ;;
    status) cmd_status ;;
    stop) cmd_stop ;;
    verify) cmd_verify ;;
    run)
      shift
      [ "${1:-}" = "--" ] && shift
      cmd_run "$@"
      ;;
    mysql-cli)
      shift
      [ "${1:-}" = "--" ] && shift
      cmd_mysql_cli "$@"
      ;;
    mongo-eval) cmd_mongo_eval ;;
    -h|--help|help|"") usage ;;
    *) die "unknown command: ${cmd}" ;;
  esac
}

main "$@"
