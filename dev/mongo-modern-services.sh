#!/usr/bin/env bash
# Isolated loopback MongoDB 8.3 (current stable series) for ServiceFramework
# compat checks. Mongo-only companion to dev/compat-services.sh; it owns only
# the pid it records and never reads developer databases or default ports.
#
# Key contract is the same as compat-services.sh: credentials and connection
# coordinates live in a chmod-600 env file inside the instance directory and
# are never printed. When run is invoked with SF_COMPAT_ENV_FILE pointing at
# another env file (for example the MySQL 8 instance from compat-services.sh),
# a merged private env is produced: every key of that file is kept and only the
# SF_COMPAT_MONGO_* keys from this instance override it.
set -euo pipefail
set +x

MONGO_VERSION=8.3.11
MONGO_ARCHIVE="mongodb-macos-x86_64-${MONGO_VERSION}.tgz"
MONGO_URL="https://fastdl.mongodb.org/osx/${MONGO_ARCHIVE}"
MONGO_MD5=
MONGO_SHA1=663d9eb9cab36e5363c7f8d15fc726e79747ae4a
MONGO_SHA256=45b4a7913c1384d4b3ce32c81630a5c0e1993de2f6119f511995ec2324dec5c9
# The 8.3 tarball nests under a double-dash directory name.
MONGO_TOP="mongodb-macos-x86_64--${MONGO_VERSION}"

MONGOSH_VERSION=2.12.0
MONGOSH_ARCHIVE="mongosh-${MONGOSH_VERSION}-darwin-x64.zip"
MONGOSH_URL="https://downloads.mongodb.com/compass/${MONGOSH_ARCHIVE}"
MONGOSH_SHA1=e89e92f6cc4f7fba6f442c2880710a5a1adb9668
MONGOSH_SHA256=7515f20dd08e900572d67610f22509fe0dc2f004a52f7f3d0c2d5e0166955b07
MONGOSH_TOP="mongosh-${MONGOSH_VERSION}-darwin-x64"

CACHE="${SF_MONGO_MODERN_CACHE:-${HOME}/Library/Caches/serviceframework-mongo-modern}"
DOWNLOADS="${CACHE}/downloads"
DIST="${CACHE}/dist"
INSTANCES="${CACHE}/instances"
ACTIVE_FILE="${CACHE}/active-instance"
MONGO_HOME="${DIST}/${MONGO_TOP}"
MONGOSH_HOME="${DIST}/${MONGOSH_TOP}"
DB_NAME=sf_compat
REPLSET=sfcompat

log() {
  printf '[mongo-modern-services] %s\n' "$*" >&2
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

file_sha1() {
  shasum -a 1 "$1" | awk '{print $1}'
}

file_sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

verify_archive() {
  local file="$1" expect_sha1="$2" expect_sha="$3" got_sha1 got_sha
  got_sha1="$(file_sha1 "$file")"
  got_sha="$(file_sha256 "$file")"
  if [ "$got_sha1" != "$expect_sha1" ] || [ "$got_sha" != "$expect_sha" ]; then
    die "checksum mismatch for $(basename "$file")"
  fi
}

ensure_archive() {
  local url="$1" name="$2" expect_sha1="$3" expect_sha="$4"
  local dest="${DOWNLOADS}/${name}" partial attempt status
  partial="${dest}.partial"
  if [ -f "$dest" ]; then
    verify_archive "$dest" "$expect_sha1" "$expect_sha"
    return 0
  fi
  log "downloading ${name}"
  # Mongo's download endpoints have interrupted HTTP/2 transfers with framing
  # errors. Bound each attempt, stay on HTTP/1.1, and resume the partial file
  # instead of restarting a large archive from byte zero.
  for attempt in 1 2 3 4 5; do
    if curl --http1.1 -fL \
        --connect-timeout 20 --max-time 900 \
        --speed-limit 1024 --speed-time 30 \
        -C - -o "$partial" "$url"; then
      verify_archive "$partial" "$expect_sha1" "$expect_sha"
      mv "$partial" "$dest"
      chmod 644 "$dest"
      return 0
    else
      status=$?
    fi
    if [ "$status" -eq 33 ]; then
      # The saved bytes cannot satisfy the requested range; start cleanly.
      rm -f "$partial"
    fi
    if [ "$attempt" -lt 5 ]; then
      sleep $((attempt * 2))
    fi
  done
  die "download failed for ${name} after 5 attempts"
}

ensure_tree() {
  local archive="$1" top="$2" marker="$3"
  local dest="${DIST}/${top}"
  if [ -e "${dest}/${marker}" ]; then
    return 0
  fi
  case "$archive" in
    *.zip)
      # The zip already nests under its own top directory.
      rm -rf "$dest"
      ditto -xk "${DOWNLOADS}/${archive}" "$DIST"
      ;;
    *)
      tar -xzf "${DOWNLOADS}/${archive}" -C "$DIST"
      ;;
  esac
  [ -e "${dest}/${marker}" ] || die "archive ${archive} did not contain ${top}/${marker}"
}

ensure_binaries() {
  ensure_layout
  ensure_archive "$MONGO_URL" "$MONGO_ARCHIVE" "$MONGO_SHA1" "$MONGO_SHA256"
  ensure_archive "$MONGOSH_URL" "$MONGOSH_ARCHIVE" "$MONGOSH_SHA1" "$MONGOSH_SHA256"
  ensure_tree "$MONGO_ARCHIVE" "$MONGO_TOP" "bin/mongod"
  ensure_tree "$MONGOSH_ARCHIVE" "$MONGOSH_TOP" "bin/mongosh"
  [ -x "${MONGO_HOME}/bin/mongod" ] || die "mongod binary missing"
  [ -x "${MONGOSH_HOME}/bin/mongosh" ] || die "mongosh binary missing"
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
  mkdir -m 700 "$path" "$path/mongo-data" "$path/mongo-client-home"
  printf '%s\n' "$path" >"$ACTIVE_FILE"
  chmod 600 "$ACTIVE_FILE"
  printf '%s\n' "$path"
}

env_file_of() {
  printf '%s\n' "$1/env"
}

merged_env_file_of() {
  printf '%s\n' "$1/env.merged"
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
  local inst="$1" env_file user mongo_pass
  env_file="$(env_file_of "$inst")"
  if [ -f "$env_file" ]; then
    return 0
  fi
  user="$(generate_user)"
  mongo_pass="$(generate_token)"
  write_env_file "$env_file" <<EOF
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

# Merge an outer env file (for example the MySQL keys written by
# dev/compat-services.sh) with this instance's Mongo keys. Only
# SF_COMPAT_MONGO_* keys are replaced or appended; everything else is copied
# unchanged. The merged file is chmod 600 inside the instance directory.
merge_env_file() {
  local inst="$1" outer="$2" own merged
  own="$(env_file_of "$inst")"
  merged="$(merged_env_file_of "$inst")"
  umask 077
  python3 - "$outer" "$own" "$merged" <<'PY'
import re, sys
outer_path, own_path, merged_path = sys.argv[1:]

def read(path):
    lines = []
    with open(path) as handle:
        for raw in handle.read().splitlines():
            lines.append(raw)
    return lines

outer_lines = read(outer_path)
own_lines = read(own_path)
own_keys = set()
own_map = {}
for line in own_lines:
    if "=" not in line:
        continue
    key = line.split("=", 1)[0].strip()
    own_keys.add(key)
    own_map[key] = line

out = []
seen = set()
for line in outer_lines:
    if "=" not in line or line.lstrip().startswith("#"):
        out.append(line)
        continue
    key = line.split("=", 1)[0].strip()
    if key in own_keys:
        # Mongo keys always come from this instance.
        continue
    out.append(line)
    seen.add(key)
for line in own_lines:
    out.append(line)
with open(merged_path, "w") as handle:
    handle.write("\n".join(out) + "\n")
PY
  chmod_private "$merged"
  printf '%s\n' "$merged"
}

assert_saved_credential() {
  local value="$1" label="$2"
  printf '%s' "$value" | grep -E '^[A-Za-z0-9_-]{8,}$' >/dev/null || die "${label} is missing or unusable"
}

mongo_client() {
  local inst="$1" port="$2" db="${3:-admin}"
  shift 3 2>/dev/null || shift $#
  HOME="${inst}/mongo-client-home" \
    "${MONGOSH_HOME}/bin/mongosh" --quiet --norc \
    "mongodb://127.0.0.1:${port}/${db}" "$@"
}

cleanup_mongo_history() {
  rm -rf "$1/mongo-client-home/.mongodb" "$1/mongo-client-home/.mongosh"
}

# Runs JavaScript from a file. Credentials are joined into the script inside
# the instance directory; they never appear on a command line.
mongo_eval_file() {
  local inst="$1" port="$2" js="$3" outfile="$4" env_file user pass
  env_file="$(env_file_of "$inst")"
  if [ -f "${inst}/mongo.bootstrapped" ]; then
    user="$(load_env_var SF_COMPAT_MONGO_USER "$env_file")"
    pass="$(load_env_var SF_COMPAT_MONGO_PASSWORD "$env_file")"
    assert_saved_credential "$user" "mongo user"
    assert_saved_credential "$pass" "mongo password"
    {
      printf 'if (!db.getSiblingDB("admin").auth("%s", "%s")) { quit(1); }\n' "$user" "$pass"
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

start_mongod() {
  local inst="$1" port="$2" auth_flag="$3" pid key
  if pid="$(process_matches_meta "${inst}/mongo.pid.meta" 2>/dev/null || true)" && [ -n "${pid:-}" ]; then
    printf '%s\n' "$pid"
    return 0
  fi
  if [ -n "$auth_flag" ]; then
    key="${inst}/mongo.key"
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
  var state = db.hello();
  if (state.isWritablePrimary || state.ismaster) { ready = true; break; }
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
  port="$(load_env_var SF_COMPAT_MONGO_PORT "$(env_file_of "$inst")" || true)"
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
  python3 - "$inst" "$(manifest_of "$inst")" "$(env_file_of "$inst")" "$MONGO_HOME" "$MONGOSH_HOME" "$MONGO_VERSION" "$MONGOSH_VERSION" <<'PY'
import json, sys
inst, manifest, env_file, mongo_home, mongosh_home, mongo_version, mongosh_version = sys.argv[1:]
def port(name):
    for line in open(env_file):
        if line.startswith(name + "="):
            return int(line.split("=", 1)[1].strip())
    raise SystemExit("missing " + name)
doc = {
    "mongo": {
        "version": mongo_version,
        "binary": mongo_home + "/bin/mongod",
        "cli": mongosh_home + "/bin/mongosh",
        "cli_version": mongosh_version,
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
  local inst
  inst="$(active_instance 2>/dev/null || true)" || true
  [ -n "${inst:-}" ] || return 0
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
  if ! prepare_mongo "$inst"; then
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
  local inst mongo_state mongo_port env_file
  if ! inst="$(active_instance)"; then
    printf 'state=absent\ncache=%s\n' "$CACHE"
    return 0
  fi
  env_file="$(env_file_of "$inst")"
  mongo_state="$(service_state "${inst}/mongo.pid.meta")"
  mongo_port="$(load_env_var SF_COMPAT_MONGO_PORT "$env_file" 2>/dev/null || true)"
  printf 'state=present\n'
  printf 'instance=%s\n' "$inst"
  printf 'manifest=%s\n' "$(manifest_of "$inst")"
  printf 'env_file=%s\n' "$env_file"
  printf 'mongo_state=%s\n' "$mongo_state"
  printf 'mongo_port=%s\n' "${mongo_port:-}"
  printf 'mongo_version=%s\n' "$MONGO_VERSION"
  printf 'mongosh_version=%s\n' "$MONGOSH_VERSION"
}

cmd_stop() {
  local inst
  if ! inst="$(active_instance)"; then
    log "no active instance"
    return 0
  fi
  stop_owned "${inst}/mongo.pid.meta" || true
  local i=0
  while [ "$i" -lt 20 ]; do
    if [ "$(service_state "${inst}/mongo.pid.meta")" = "stopped" ]; then
      log "stopped"
      return 0
    fi
    sleep 0.5
    i=$((i + 1))
  done
  die "owned services are still running"
}

# mongosh writes its "sfcompat [direct: primary] admin>" prompt onto stdout
# ahead of print() output, so markers are extracted, not line-anchored.
filter_compat_lines() {
  grep -oE 'COMPAT_[A-Z0-9_]+( [0-9][A-Za-z0-9._-]*)?' || true
}

strip_shell_echo() {
  sed -E 's/^[^ ]+ \[[^]]*\] [^ >]+> //' | sed '/^[[:space:]]*$/d'
}

cmd_verify() {
  local inst env_file mongo_port report js status
  inst="$(require_instance)"
  [ "$(service_state "${inst}/mongo.pid.meta")" = "running" ] || die "mongo is not running"
  env_file="$(env_file_of "$inst")"
  mongo_port="$(load_env_var SF_COMPAT_MONGO_PORT "$env_file")"
  report="${inst}/verify-last.txt"
  js="${inst}/mongo-verify.js"
  umask 077
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
  {
    printf 'mongo_client_exit='
    if mongo_eval_file "$inst" "$mongo_port" "$js" "${inst}/mongo-verify.out"; then
      printf '0\n'
    else
      status=$?
      printf '%s\n' "$status"
    fi
    rm -f "$js"
    filter_compat_lines <"${inst}/mongo-verify.out"
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
  grep -F -x 'COMPAT_RW_OK' "$report" >/dev/null || return 1
  grep -F -x 'COMPAT_TX_OK' "$report" >/dev/null || return 1
  grep -E '^mongo_client_exit=0$' "$report" >/dev/null || return 1
  grep -E '^mongo_server_version_exit=0$' "$report" >/dev/null || return 1
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
  strip_shell_echo <"${inst}/mongo-eval.out" | grep -v 'db\.getSiblingDB("admin")\.auth(' || true
  return "$status"
}

cmd_run() {
  local inst status stop_status child_env
  [ "$#" -gt 0 ] || die "run requires a command"
  cmd_start
  inst="$(require_instance)"
  child_env="$(env_file_of "$inst")"
  # When the caller points SF_COMPAT_ENV_FILE at another env file (for example
  # the MySQL instance managed by compat-services.sh), produce a merged private
  # file: outer keys are kept, only the SF_COMPAT_MONGO_* keys come from here.
  if [ -n "${SF_COMPAT_ENV_FILE:-}" ] && [ -f "${SF_COMPAT_ENV_FILE}" ] \
      && [ "${SF_COMPAT_ENV_FILE}" != "$child_env" ]; then
    child_env="$(merge_env_file "$inst" "$SF_COMPAT_ENV_FILE")"
  fi
  # bash 3.2 does not run a trapped signal until the foreground command
  # returns, and `wait` does not return until the child exits. A TERM sent to
  # this script would otherwise sit for the whole child lifetime and leave
  # Mongo up. Sleep briefly so the trap runs, then signal the child pid
  # recorded below (not a parent launcher). Exit 128+signal.
  trap 'on_exit_stop_services' EXIT
  set +e
  SF_COMPAT_ENV_FILE="$child_env" \
    SF_COMPAT_MANIFEST="$(manifest_of "$inst")" \
    SF_COMPAT_MONGO_HOST=127.0.0.1 \
    SF_COMPAT_MONGO_PORT="$(load_env_var SF_COMPAT_MONGO_PORT "$(env_file_of "$inst")")" \
    SF_COMPAT_MONGO_DATABASE="$DB_NAME" \
    SF_COMPAT_MONGO_REPLSET="$REPLSET" \
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
Usage: dev/mongo-modern-services.sh <command>

  start              download official binaries if needed, start loopback MongoDB 8.3
  status             show instance paths, ports, and owned-process state
  stop               stop only the pid recorded for this instance
  verify             ping, write, read, and roll back a transaction
  run -- command     start, run command, then stop
  mongo-eval         run stdin JavaScript after authenticating

Server: MongoDB ${MONGO_VERSION} (current stable 8.3 series), single-node
replica set ${REPLSET}, auth on, database ${DB_NAME}. Credentials stay in the
instance env file (mode 600). Status prints that path and does not print the
credential values. When SF_COMPAT_ENV_FILE points at another private env file
(for example the compat-services.sh MySQL instance), run merges it with these
Mongo keys instead of clashing.
EOF
}

main() {
  need_cmd curl
  need_cmd python3
  need_cmd tar
  need_cmd ditto
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
    mongo-eval) cmd_mongo_eval ;;
    -h|--help|help|"") usage ;;
    *) die "unknown command: ${cmd}" ;;
  esac
}

main "$@"
