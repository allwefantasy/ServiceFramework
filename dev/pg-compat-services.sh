#!/usr/bin/env bash
# Isolated PostgreSQL compat service. It owns only a private data directory under
# the cache root and a randomly assigned loopback port. It never uses port 5432.
set -u
set -o pipefail

PG_BIN="${SF_COMPAT_PG_BIN:-/usr/local/opt/postgresql@17/bin}"
CACHE_ROOT="${SF_COMPAT_PG_CACHE:-$HOME/Library/Caches/serviceframework-pg-compat-services}"
INSTANCE_ROOT="$CACHE_ROOT/instances"
ACTIVE="$CACHE_ROOT/active-instance"
LOCK_FILE="$CACHE_ROOT/lock"
DB_NAME="sf_compat"
APP_USER="sf_compat"
APP_PASSWORD=""
ADMIN_PASSWORD=""
PORT=""
INSTANCE=""
DATA=""
SOCKET=""
LOG=""
META=""
ENV_FILE=""

usage() {
  cat <<'EOF'
Usage: dev/pg-compat-services.sh <command>

Commands:
  start        Initialize or reuse this workspace's private PostgreSQL instance.
  stop         Stop that instance and wait for the postmaster to exit.
  status       Print status only if the private loopback process is live.
  verify       Run a short authenticated CREATE/SELECT/ROLLBACK probe.
  run -- CMD   Start the service, run CMD with SF_COMPAT_PG_ENV_FILE set, then stop it.
  psql         Open an authenticated psql session against the private instance.
  shell        Print environment-variable assignments for eval/debugging.
  version      Print PostgreSQL client/server installation versions.

Environment:
  SF_COMPAT_PG_BIN      PostgreSQL bin directory (default /usr/local/opt/postgresql@17/bin)
  SF_COMPAT_PG_CACHE    Private cache root (default ~/Library/Caches/serviceframework-pg-compat-services)
  SF_COMPAT_PG_ENV_FILE Set by run/shell. File contains credentials and is mode 600.

PostgreSQL runs with listen_addresses=127.0.0.1 on an allocated non-5432 port.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

note() {
  printf '%s\n' "$*" >&2
}

need_bins() {
  for bin in initdb postgres pg_ctl pg_isready psql; do
    [ -x "$PG_BIN/$bin" ] || die "missing $PG_BIN/$bin (set SF_COMPAT_PG_BIN)"
  done
}

mkdir_cache() {
  mkdir -p "$INSTANCE_ROOT" || die "cannot create $INSTANCE_ROOT"
  chmod 700 "$CACHE_ROOT" "$INSTANCE_ROOT" 2>/dev/null || true
}

acquire_lock() {
  mkdir_cache
  exec 9>"$LOCK_FILE"
  if ! flock -x 9 2>/dev/null; then
    if command -v lockf >/dev/null 2>&1; then
      lockf -t 0 "$LOCK_FILE" true || die "another pg-compat-services operation is running"
    fi
  fi
}

random_token() {
  openssl rand -hex 24 2>/dev/null || uuidgen | tr -d '-' | tr '[:upper:]' '[:lower:]'
}

allocate_port() {
  /usr/bin/python3 - <<'PY'
import random
import socket

for _ in range(200):
    port = random.randint(25000, 55000)
    if port in (5432, 3306, 33060, 27017):
        continue
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.bind(("127.0.0.1", port))
    except OSError:
        continue
    finally:
        sock.close()
    print(port)
    raise SystemExit(0)
raise SystemExit(1)
PY
}

load_meta() {
  [ -n "$INSTANCE" ] && return 0
  [ -f "$ACTIVE" ] || return 1
  INSTANCE="$(cat "$ACTIVE")"
  [ -f "$INSTANCE/meta.sh" ] || return 1
  . "$INSTANCE/meta.sh"
  DATA="$INSTANCE/pg-data"
  SOCKET="$INSTANCE/socket"
  LOG="$INSTANCE/postgres.log"
  ENV_FILE="$INSTANCE/postgres.env"
  [ -n "$PORT" ] || return 1
  return 0
}

postmaster_pid() {
  [ -f "$DATA/postmaster.pid" ] || return 1
  head -n 1 "$DATA/postmaster.pid" | tr -d '[:space:]'
}

running_pid() {
  local pid command
  pid="$(postmaster_pid 2>/dev/null)" || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
  case "$command" in
    *postgres*" -D "*"$DATA"*|*postgres*-D"$DATA"*) printf '%s\n' "$pid"; return 0 ;;
    *) return 1 ;;
  esac
}

is_ready() {
  "$PG_BIN/pg_isready" -q -h 127.0.0.1 -p "$PORT" -d postgres >/dev/null 2>&1
}

ensure_loopback() {
  local listeners
  listeners="$(lsof -nP -a -iTCP:"$PORT" -sTCP:LISTEN -c postgres 2>/dev/null || true)"
  printf '%s\n' "$listeners" | grep 'postgres' | grep '127.0.0.1:' >/dev/null || die "instance port $PORT is not a loopback PostgreSQL listener"
  if printf '%s\n' "$listeners" | grep 'postgres' | grep -v '127.0.0.1:' >/dev/null; then
    die "instance port $PORT listens on a non-loopback address"
  fi
}

write_env() {
  cat >"$ENV_FILE" <<EOF
SF_COMPAT_PG_HOST=127.0.0.1
SF_COMPAT_PG_PORT=$PORT
SF_COMPAT_PG_DATABASE=$DB_NAME
SF_COMPAT_PG_USER=$APP_USER
SF_COMPAT_PG_PASSWORD=$APP_PASSWORD
SF_COMPAT_PG_SCHEMA=public
SF_COMPAT_PG_INSTANCE=$INSTANCE
EOF
  chmod 600 "$ENV_FILE"
}

write_meta() {
  cat >"$META" <<EOF
PORT=$PORT
PID=
CREATED_AT=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
EOF
}

start_owned() {
  acquire_lock
  need_bins
  if load_meta; then
    local pid
    pid="$(running_pid || true)"
    if [ -n "$pid" ] && is_ready; then
      ensure_loopback
      ENV_FILE="$INSTANCE/postgres.env"
      return 0
    fi
    rm -rf "$INSTANCE" "$ACTIVE"
    INSTANCE=""
  fi

  PORT="$(allocate_port)" || die "no loopback port is available"
  [ "$PORT" != "5432" ] || die "internal error: allocated PostgreSQL default port"
  INSTANCE="$INSTANCE_ROOT/pg-$PORT-$$"
  DATA="$INSTANCE/pg-data"
  SOCKET="$INSTANCE/socket"
  LOG="$INSTANCE/postgres.log"
  META="$INSTANCE/meta.sh"
  ENV_FILE="$INSTANCE/postgres.env"
  mkdir -p "$DATA" "$SOCKET" || die "cannot create $INSTANCE"
  chmod 700 "$INSTANCE" "$DATA" "$SOCKET"
  APP_PASSWORD="pg_compat_$(random_token)"
  ADMIN_PASSWORD="pg_admin_$(random_token)"
  write_env

  local admin_password_file="$INSTANCE/.admin-password"
  printf '%s\n' "$ADMIN_PASSWORD" >"$admin_password_file"
  chmod 600 "$admin_password_file"
  if ! "$PG_BIN/initdb" -D "$DATA" -U postgres \
      --auth=scram-sha-256 --auth-local=scram-sha-256 --auth-host=scram-sha-256 \
      --pwfile="$admin_password_file" --encoding=UTF8 --locale=C \
      >"$INSTANCE/initdb.log" 2>&1; then
    rm -rf "$INSTANCE"
    die "initdb failed; see $INSTANCE/initdb.log"
  fi
  rm -f "$admin_password_file"
  write_meta
  printf '%s\n' "$INSTANCE" >"$ACTIVE"

  "$PG_BIN/postgres" -D "$DATA" -h 127.0.0.1 -p "$PORT" \
      -c listen_addresses=127.0.0.1 -c unix_socket_directories='' \
      -c log_connections=off -c log_disconnections=off \
      >>"$LOG" 2>&1 &
  local pid=$!
  sed -i '' "s/^PID=.*/PID=$pid/" "$META" 2>/dev/null || true

  local attempt=0
  while [ $attempt -lt 200 ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$ACTIVE"
      die "postgres exited during startup; see $LOG"
    fi
    if is_ready; then
      break
    fi
    attempt=$((attempt + 1))
    sleep 0.1
  done
  is_ready || { rm -f "$ACTIVE"; kill "$pid" 2>/dev/null || true; die "postgres did not become ready on 127.0.0.1:$PORT"; }
  ensure_loopback

  PGPASSWORD="$ADMIN_PASSWORD" "$PG_BIN/psql" \
      -h 127.0.0.1 -p "$PORT" -U postgres -d postgres \
      -v ON_ERROR_STOP=1 --no-psqlrc --quiet >"$INSTANCE/bootstrap.log" 2>&1 <<'SQL' || {
CREATE DATABASE sf_compat;
SQL
    rm -f "$ACTIVE"
    kill "$pid" 2>/dev/null || true
    die "database bootstrap failed; see $INSTANCE/bootstrap.log"
  }

  PGPASSWORD="$ADMIN_PASSWORD" "$PG_BIN/psql" \
      -h 127.0.0.1 -p "$PORT" -U postgres -d sf_compat \
      -v ON_ERROR_STOP=1 --no-psqlrc --quiet >"$INSTANCE/bootstrap.log" 2>&1 <<SQL || {
REVOKE CONNECT ON DATABASE sf_compat FROM PUBLIC;
CREATE USER $APP_USER PASSWORD '$APP_PASSWORD';
ALTER DATABASE sf_compat OWNER TO $APP_USER;
GRANT CONNECT ON DATABASE sf_compat TO $APP_USER;
GRANT USAGE, CREATE ON SCHEMA public TO $APP_USER;
ALTER SCHEMA public OWNER TO $APP_USER;
SQL
    rm -f "$ACTIVE"
    kill "$pid" 2>/dev/null || true
    die "database bootstrap failed; see $INSTANCE/bootstrap.log"
  }
  note "SF_COMPAT_PG_ENV_FILE=$ENV_FILE"
  note "SF_COMPAT_PG_MANIFEST=$INSTANCE/manifest"
  {
    printf 'instance=%s\n' "$INSTANCE"
    printf 'port=%s\n' "$PORT"
    "$PG_BIN/postgres" --version | sed 's/^/postgres=/'
    "$PG_BIN/psql" --version | sed 's/^/psql=/'
  } >"$INSTANCE/manifest"
}

stop_owned() {
  acquire_lock
  if ! load_meta; then
    return 0
  fi
  local pid
  pid="$(running_pid || true)"
  if [ -n "$pid" ]; then
    "$PG_BIN/pg_ctl" -D "$DATA" -w -t 30 -m fast stop >/dev/null 2>&1 || {
      kill "$pid" 2>/dev/null || true
      local attempt=0
      while kill -0 "$pid" 2>/dev/null && [ $attempt -lt 100 ]; do
        sleep 0.1
        attempt=$((attempt + 1))
      done
    }
  fi
  rm -f "$ACTIVE"
}

status_owned() {
  acquire_lock
  if ! load_meta; then
    note "stopped"
    return 1
  fi
  local pid
  pid="$(running_pid || true)"
  if [ -z "$pid" ] || ! is_ready; then
    note "stopped"
    return 1
  fi
  ensure_loopback
  note "running instance=$INSTANCE port=$PORT pid=$pid"
  note "env=$INSTANCE/postgres.env"
  return 0
}

load_env() {
  if ! load_meta; then
    die "no active private PostgreSQL instance"
  fi
  [ -f "$ENV_FILE" ] || die "missing env file $ENV_FILE"
  . "$ENV_FILE"
}

verify_owned() {
  load_env
  local token="pgverify_$(date +%s)_$$"
  PGPASSWORD="$SF_COMPAT_PG_PASSWORD" "$PG_BIN/psql" \
      -h 127.0.0.1 -p "$SF_COMPAT_PG_PORT" -U "$SF_COMPAT_PG_USER" -d "$SF_COMPAT_PG_DATABASE" \
      -v ON_ERROR_STOP=1 --no-psqlrc -XAt <<SQL
BEGIN;
CREATE TABLE pg_compat_verify_$token (id integer PRIMARY KEY, text_value varchar(64));
INSERT INTO pg_compat_verify_$token VALUES (1, 'ok');
SELECT 'COMPAT_PG probe=' || text_value FROM pg_compat_verify_$token WHERE id = 1;
ROLLBACK;
SQL
}

run_command() {
  local status=0
  start_owned
  export SF_COMPAT_PG_ENV_FILE="$INSTANCE/postgres.env"
  export SF_COMPAT_PG_MANIFEST="$INSTANCE/manifest"
  "$@" || status=$?
  stop_owned
  return "$status"
}

command="${1:-}"
case "$command" in
  start)
    start_owned
    ;;
  stop)
    stop_owned
    ;;
  status)
    status_owned
    ;;
  verify)
    verify_owned
    ;;
  shell)
    start_owned
    printf 'SF_COMPAT_PG_ENV_FILE=%q\n' "$INSTANCE/postgres.env"
    printf 'SF_COMPAT_PG_MANIFEST=%q\n' "$INSTANCE/manifest"
    ;;
  psql)
    load_env
    PGPASSWORD="$SF_COMPAT_PG_PASSWORD" exec "$PG_BIN/psql" \
      -h 127.0.0.1 -p "$SF_COMPAT_PG_PORT" -U "$SF_COMPAT_PG_USER" -d "$SF_COMPAT_PG_DATABASE"
    ;;
  version)
    need_bins
    "$PG_BIN/postgres" --version
    "$PG_BIN/psql" --version
    ;;
  run)
    shift
    [ "${1:-}" = "--" ] && shift
    [ $# -gt 0 ] || die "run needs a command after --"
    trap 'stop_owned' INT TERM HUP
    trap 'stop_owned' EXIT
    run_command "$@"
    status=$?
    trap - INT TERM HUP EXIT
    exit "$status"
    ;;
  -h|--help|help|'')
    usage
    ;;
  *)
    die "unknown command: $command"
    ;;
esac
