#!/usr/bin/env bash
# Build remote-notes and publish it to RemoteService.
#   dev/remote-notes/publish.sh all         framework + application
#   dev/remote-notes/publish.sh app         application jar only
#   dev/remote-notes/publish.sh framework   rebuild framework jars, keep this application source
#
# MySQL password is taken from SF_COMPAT_ENV_FILE (default /tmp/sf-remote-mysql.env)
# and stored on the server as mysql.env mode 600. It is not printed.

set -euo pipefail

MODE="${1:-all}"
case "${MODE}" in
  all|app|framework) ;;
  *)
    echo "usage: publish.sh all|app|framework" >&2
    exit 2
    ;;
esac

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
APP_DIR="${ROOT}/apps/remote-notes"
STATE_DIR="${ROOT}/dev/remote-notes/.publish"
FRAMEWORK_CACHE="${STATE_DIR}/framework-lib"
REMOTE="${SF_REMOTE_HOST:-remoteservice}"
REMOTE_ROOT=/home/william-pc/softwares/serviceframework-remote-notes
JAVA_REMOTE="${SF_REMOTE_JAVA:-/home/william-pc/softwares/infinity-sql-spark412-2.4.10-codex/jdk17/bin/java}"
ENV_FILE="${SF_COMPAT_ENV_FILE:-/tmp/sf-remote-mysql.env}"
RELEASE_ID="$(date +%Y%m%d%H%M%S)"
DIST="${APP_DIR}/target/remote-notes-dist"

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "${JAVA_HOME}/bin/java" ]; then
  echo "JAVA_HOME must point at a JDK that can build this repository" >&2
  exit 1
fi
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

if [ ! -f "${ENV_FILE}" ]; then
  echo "MySQL env file is missing: ${ENV_FILE}" >&2
  exit 1
fi
python3 - "${ENV_FILE}" <<'PY'
import os, sys
path = sys.argv[1]
mode = os.stat(path).st_mode & 0o777
if mode & 0o077:
    sys.exit("refusing an env file that is readable by group or other")
keys = []
for line in open(path):
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    keys.append(line.split("=", 1)[0])
needed = ["SF_COMPAT_MYSQL_USER", "SF_COMPAT_MYSQL_PASSWORD", "SF_COMPAT_MYSQL_DATABASE"]
missing = [key for key in needed if key not in keys]
if missing:
    sys.exit("env file is missing " + ",".join(missing))
print("env-file-ok")
PY

cd "${ROOT}"
if [ "${MODE}" = "app" ]; then
  if [ ! -d "${FRAMEWORK_CACHE}" ] || [ -z "$(ls -A "${FRAMEWORK_CACHE}" 2>/dev/null || true)" ]; then
    echo "no cached framework jars; run publish.sh all first" >&2
    exit 1
  fi
  mvn -pl apps/remote-notes package -DskipTests -Dmaven.javadoc.skip=true
else
  mvn -pl apps/remote-notes -am install -DskipTests -Dmaven.javadoc.skip=true
fi

APP_JAR="${APP_DIR}/target/remote-notes-2.0.9.jar"
LIB_SRC="${APP_DIR}/target/remote-notes-lib"
if [ ! -f "${APP_JAR}" ]; then
  echo "application jar was not built: ${APP_JAR}" >&2
  exit 1
fi

rm -rf "${DIST}"
mkdir -p "${DIST}/lib" "${DIST}/config" "${DIST}/bin" "${DIST}/logs"
cp "${APP_JAR}" "${DIST}/remote-notes.jar"
cp "${APP_DIR}/src/main/resources/schema.sql" "${DIST}/schema.sql"
cp "${APP_DIR}/src/main/resources/config/application.yml.template" "${DIST}/config/application.yml.template"
cp "${APP_DIR}/src/main/resources/config/logging.yml" "${DIST}/config/logging.yml"
cp "${ROOT}/dev/remote-notes/remote-notes.sh" "${DIST}/bin/remote-notes.sh"
chmod 755 "${DIST}/bin/remote-notes.sh"
printf '%s\n' "${RELEASE_ID}" > "${DIST}/release-id"
printf '%s\n' "${MODE}" > "${DIST}/publish-mode"

if [ "${MODE}" = "app" ]; then
  cp "${FRAMEWORK_CACHE}/"*.jar "${DIST}/lib/"
  for jar in "${LIB_SRC}/"*.jar; do
    base="$(basename "${jar}")"
    case "${base}" in
      serviceframework-*) ;;
      *) cp "${jar}" "${DIST}/lib/${base}" ;;
    esac
  done
else
  cp "${LIB_SRC}/"*.jar "${DIST}/lib/"
  BUILD_FILE="$(mktemp)"
  printf 'buildId=%s\nmode=%s\n' "${RELEASE_ID}" "${MODE}" > "${BUILD_FILE}"
  STAMP_DIR="$(mktemp -d)"
  mkdir -p "${STAMP_DIR}/META-INF"
  cp "${BUILD_FILE}" "${STAMP_DIR}/META-INF/sf-framework-build.txt"
  for jar in "${DIST}/lib"/serviceframework-*.jar; do
    case "${jar}" in
      *-sources.jar|*-javadoc.jar) continue ;;
    esac
    jar uf "${jar}" -C "${STAMP_DIR}" META-INF/sf-framework-build.txt
  done
  rm -rf "${STAMP_DIR}" "${BUILD_FILE}"
  rm -rf "${FRAMEWORK_CACHE}"
  mkdir -p "${FRAMEWORK_CACHE}"
  cp "${DIST}/lib"/serviceframework-*.jar "${FRAMEWORK_CACHE}/"
fi

python3 - "${ENV_FILE}" "${STATE_DIR}/mysql.env" <<'PY'
import os, sys
src, dest = sys.argv[1], sys.argv[2]
os.makedirs(os.path.dirname(dest), exist_ok=True)
password = None
database = None
user = None
for line in open(src):
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    key, value = line.split("=", 1)
    if key == "SF_COMPAT_MYSQL_PASSWORD":
        password = value
    elif key == "SF_COMPAT_MYSQL_DATABASE":
        database = value
    elif key == "SF_COMPAT_MYSQL_USER":
        user = value
if database != "sf_serviceframework_e2e" or user != "sf_e2e":
    sys.exit("refusing a database or user other than sf_serviceframework_e2e / sf_e2e")
with open(dest, "w") as handle:
    handle.write("SF_REMOTE_MYSQL_PASSWORD=%s\n" % password)
os.chmod(dest, 0o600)
PY

ssh -o BatchMode=yes "${REMOTE}" "mkdir -p '${REMOTE_ROOT}/releases' '${REMOTE_ROOT}/logs'"
rsync -az --delete "${DIST}/" "${REMOTE}:${REMOTE_ROOT}/releases/${RELEASE_ID}/"
scp -q "${STATE_DIR}/mysql.env" "${REMOTE}:${REMOTE_ROOT}/mysql.env"
ssh -o BatchMode=yes "${REMOTE}" "chmod 600 '${REMOTE_ROOT}/mysql.env' && ln -sfn '${REMOTE_ROOT}/releases/${RELEASE_ID}' '${REMOTE_ROOT}/current'"

ssh -o BatchMode=yes "${REMOTE}" "SF_REMOTE_JAVA='${JAVA_REMOTE}' bash '${REMOTE_ROOT}/releases/${RELEASE_ID}/bin/remote-notes.sh' stop"
ssh -o BatchMode=yes "${REMOTE}" "SF_REMOTE_JAVA='${JAVA_REMOTE}' bash '${REMOTE_ROOT}/releases/${RELEASE_ID}/bin/remote-notes.sh' apply-schema"
ssh -o BatchMode=yes "${REMOTE}" "SF_REMOTE_JAVA='${JAVA_REMOTE}' bash '${REMOTE_ROOT}/releases/${RELEASE_ID}/bin/remote-notes.sh' start"

echo "published ${MODE} ${RELEASE_ID}"
