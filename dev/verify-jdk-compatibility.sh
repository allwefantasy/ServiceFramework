#!/usr/bin/env bash
# Build Java 8 bytecode once on JDK 17, then run that same output on JDK 8 and JDK 17.
# JDK8_HOME and JDK17_HOME are required. Do not discover them with /usr/libexec/java_home.
#
# Layout of a run:
#   1. mvn install -DskipTests into an isolated local repo under the report dir.
#      Internal module deps therefore resolve to JARs built by THIS run.
#   2. animal-sniffer java18 signature check on the compiled classes.
#   3. dependency:tree + dependency audit over the exact resolved classpath.
#   4. surefire:test and scalatest:test on JDK 8, then on JDK 17, with no
#      compile or clean in between. Class/JAR SHA256 must not change.

set -u
set -o pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK8_HOME="${JDK8_HOME:-}"
JDK17_HOME="${JDK17_HOME:-}"
REPORT=""
JAVASSIST_VERSION=""
MAVEN_SETTINGS=""
MODULES=()
FAILURES=0
SUMMARY=""

usage() {
  cat <<'EOF'
Usage:
  JDK8_HOME=... JDK17_HOME=... dev/verify-jdk-compatibility.sh [options] [module ...]

Options:
  --jdk8-home PATH          JDK 8 home. Overrides JDK8_HOME.
  --jdk17-home PATH         JDK 17 home. Overrides JDK17_HOME.
  --report-dir PATH         Default: target/jdk-compatibility
  --javassist-version VER   Pass -Djavassist.version. Comparison baseline: 3.30.2-GA
  --maven-settings PATH     Maven settings file passed to every mvn and the audit.
                            The path may contain spaces. When omitted, the script
                            writes <report-dir>/maven-central-settings.xml and uses
                            that. It never edits ~/.m2/settings.xml.
  -h, --help

No module means the whole reactor. A module is built with -am so reactor
dependencies come from this build. The build installs into an isolated Maven
local repository at <report-dir>/m2 (seeded from ~/.m2/repository when empty),
so tests and the dependency audit resolve internal artifacts produced by this
run instead of whatever happened to be in ~/.m2. Tests are surefire:test and
scalatest:test only: they do not compile or clean. deploy and performRelease
are not invoked. Inherited add-opens in MAVEN_OPTS, JAVA_TOOL_OPTIONS,
_JAVA_OPTIONS, and JDK_JAVA_OPTIONS are cleared for these commands.

This machine's /usr/libexec/java_home -v 1.8 can print a JDK 17 home. Pass the
two homes explicitly. Example:
  JDK8_HOME=/usr/local/opt/openjdk@8 \
  JDK17_HOME=/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home \
  dev/verify-jdk-compatibility.sh serviceframework-common
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

note() {
  printf '%s\n' "$*" | tee -a "$SUMMARY"
}

normalize_module() {
  local name="${1%/}"
  name="${name##*/}"
  case "$name" in
    serviceframework-common|serviceframework-common_2.13) printf '%s\n' serviceframework-common ;;
    serviceframework-orm|serviceframework-orm_2.13) printf '%s\n' serviceframework-orm ;;
    serviceframework-mongo|serviceframework-mongo_2.13) printf '%s\n' serviceframework-mongo ;;
    serviceframework-web|serviceframework-web_2.13) printf '%s\n' serviceframework-web ;;
    serviceframework-dispatcher|serviceframework-dispatcher_2.13) printf '%s\n' serviceframework-dispatcher ;;
    serviceframework-jetty-9-server|serviceframework-jetty-9-server_2.13) printf '%s\n' serviceframework-jetty-9-server ;;
    *) return 1 ;;
  esac
}

while [ $# -gt 0 ]; do
  case "$1" in
    --jdk8-home)
      [ $# -ge 2 ] || die "--jdk8-home needs a path"
      JDK8_HOME="$2"
      shift 2
      ;;
    --jdk17-home)
      [ $# -ge 2 ] || die "--jdk17-home needs a path"
      JDK17_HOME="$2"
      shift 2
      ;;
    --report-dir)
      [ $# -ge 2 ] || die "--report-dir needs a path"
      REPORT="$2"
      shift 2
      ;;
    --javassist-version)
      [ $# -ge 2 ] || die "--javassist-version needs a version"
      JAVASSIST_VERSION="$2"
      shift 2
      ;;
    --maven-settings)
      [ $# -ge 2 ] || die "--maven-settings needs a path"
      MAVEN_SETTINGS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    -*)
      die "unknown option: $1"
      ;;
    *)
      MODULES+=("$1")
      shift
      ;;
  esac
done
while [ $# -gt 0 ]; do
  MODULES+=("$1")
  shift
done

[ -n "$JDK8_HOME" ] || die "JDK8_HOME is required. Do not use /usr/libexec/java_home -v 1.8."
[ -n "$JDK17_HOME" ] || die "JDK17_HOME is required."
[ -d "$JDK8_HOME" ] || die "JDK8_HOME is not a directory: $JDK8_HOME"
[ -d "$JDK17_HOME" ] || die "JDK17_HOME is not a directory: $JDK17_HOME"
[ -x "$JDK8_HOME/bin/java" ] || die "JDK8_HOME has no bin/java: $JDK8_HOME"
[ -x "$JDK17_HOME/bin/java" ] || die "JDK17_HOME has no bin/java: $JDK17_HOME"
[ -x "$JDK8_HOME/bin/javac" ] || die "JDK8_HOME has no bin/javac: $JDK8_HOME"
[ -x "$JDK17_HOME/bin/javac" ] || die "JDK17_HOME has no bin/javac: $JDK17_HOME"

java_version_token() {
  "$1/bin/java" -version 2>&1 | head -n 1 | sed -n 's/.*version "\([^"]*\)".*/\1/p'
}

require_java_major() {
  local home="$1"
  local expected="$2"
  local token javac_line
  token="$(java_version_token "$home")"
  [ -n "$token" ] || die "could not read java -version from $home"
  javac_line="$("$home/bin/javac" -version 2>&1)"
  case "$expected" in
    8)
      case "$token" in
        1.8.*) ;;
        *) die "JDK8_HOME is $token, not Java 8: $home" ;;
      esac
      case "$javac_line" in
        *"1.8."*) ;;
        *) die "JDK8_HOME javac is not Java 8: $javac_line" ;;
      esac
      ;;
    17)
      case "$token" in
        17.*) ;;
        *) die "JDK17_HOME is $token, not Java 17: $home" ;;
      esac
      case "$javac_line" in
        *"17."*) ;;
        *) die "JDK17_HOME javac is not Java 17: $javac_line" ;;
      esac
      ;;
  esac
  printf '%s\n' "$token"
}

# require_java_major exits inside a command-substitution subshell, and this
# script does not use set -e, so without an explicit check a bad JDK would only
# abort the subshell and the run would continue with an empty token.
JDK8_TOKEN="$(require_java_major "$JDK8_HOME" 8)" || die "JDK8_HOME validation failed"
JDK17_TOKEN="$(require_java_major "$JDK17_HOME" 17)" || die "JDK17_HOME validation failed"
[ "$JDK8_HOME" != "$JDK17_HOME" ] || die "JDK8_HOME and JDK17_HOME must be different installations"

SELECTED=()
if [ "${#MODULES[@]}" -gt 0 ]; then
  for raw in "${MODULES[@]}"; do
    name="$(normalize_module "$raw")" || die "unknown module: $raw"
    SELECTED+=("$name")
  done
fi

if [ -z "$REPORT" ]; then
  REPORT="$ROOT/target/jdk-compatibility"
fi
mkdir -p "$REPORT"
SUMMARY="$REPORT/summary.txt"
: > "$SUMMARY"

# A settings file replaces ~/.m2/settings.xml for this process only. The
# machine settings redirect central to maven.aliyun.com, which timed out
# fetching the Surefire provider. Do not edit the user file.
write_central_settings() {
  local dest="$1"
  mkdir -p "$(dirname "$dest")"
  cat >"$dest" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <mirrors>
    <mirror>
      <id>central</id>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
EOF
}

if [ -z "$MAVEN_SETTINGS" ]; then
  MAVEN_SETTINGS="$REPORT/maven-central-settings.xml"
  write_central_settings "$MAVEN_SETTINGS"
else
  [ -f "$MAVEN_SETTINGS" ] || die "maven settings file does not exist: $MAVEN_SETTINGS"
fi

# Isolated local repository. Artifacts installed by this run land here, so
# nothing in this verification can pick up an older net.csdn jar from ~/.m2.
M2="$REPORT/m2"
if [ ! -d "$M2" ]; then
  if [ -d "$HOME/.m2/repository" ]; then
    note_seed=1
    mkdir -p "$M2"
    # cp -c clonefiles on APFS: seeding is cheap. Artifacts are copied as-is;
    # missing pieces are downloaded by Maven below.
    cp -Rc "$HOME/.m2/repository/" "$M2/" 2>/dev/null || cp -R "$HOME/.m2/repository/" "$M2/"
    # Drop resolver origin tracking: artifacts seeded this way must be usable
    # regardless of which remote id originally produced them.
    find "$M2" -name '_remote.repositories' -delete 2>/dev/null || true
    find "$M2" -name '*.lastUpdated' -delete 2>/dev/null || true
  else
    mkdir -p "$M2"
  fi
fi
# A previous mirror timeout leaves *.lastUpdated next to a missing jar and
# Maven will not retry until that marker expires. A tracking file whose
# repository id is not the current mirror makes Maven re-download every
# cached artifact. Drop those markers only; keep the cached files.
find "$M2" \( -name '*.lastUpdated' -o -name '_remote.repositories' \) -delete 2>/dev/null || true

note "verify-jdk-compatibility"
note "root=$ROOT"
note "JDK8_HOME=$JDK8_HOME"
note "JDK8=$JDK8_TOKEN"
note "JDK17_HOME=$JDK17_HOME"
note "JDK17=$JDK17_TOKEN"
note "javassist_override=${JAVASSIST_VERSION:-<pom default>}"
note "local_repo=$M2"
note "maven_settings=$MAVEN_SETTINGS"
if [ "${note_seed:-0}" = "1" ]; then
  note "local_repo_seeded_from=$HOME/.m2/repository"
fi
if [ "${#SELECTED[@]}" -gt 0 ]; then
  note "modules=${SELECTED[*]}"
else
  note "modules=<reactor>"
fi
note "cleared for child commands: MAVEN_OPTS JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS"
note "goals after the build: surefire:test scalatest:test (no compile, no clean, no deploy)"

PL_ARGS=()
if [ "${#SELECTED[@]}" -gt 0 ]; then
  joined="$(IFS=,; printf '%s' "${SELECTED[*]}")"
  PL_ARGS=(-pl "$joined" -am)
fi
VERSION_ARGS=()
if [ -n "$JAVASSIST_VERSION" ]; then
  VERSION_ARGS=(-Djavassist.version="$JAVASSIST_VERSION")
fi
REPO_ARGS=(-Dmaven.repo.local="$M2")
# Bound every network wait so a stalled repository cannot hang the run.
NET_ARGS=(
  -Daether.connector.connectTimeout=20000
  -Daether.connector.requestTimeout=180000
  -Daether.connector.retryCount=3
)

run_logged() {
  local java_home="$1"
  local log_file="$2"
  shift 2
  local -a cmd
  # -s and the settings path are separate argv elements so a space in the path
  # is not split. Every build, sniffer, tree, and test goal goes through here.
  cmd=(mvn -B --no-transfer-progress -s "$MAVEN_SETTINGS")
  cmd+=("${NET_ARGS[@]}" "$@")
  {
    printf 'COMMAND:'
    printf ' %q' env JAVA_HOME="$java_home" PATH="$java_home/bin:$PATH" "${cmd[@]}"
    printf '\n'
    printf 'DATE: %s\n' "$(date '+%Y-%m-%d %H:%M:%S %z')"
    "$java_home/bin/java" -version
    "$java_home/bin/javac" -version
  } > "$log_file" 2>&1
  env \
    -u MAVEN_OPTS \
    -u JAVA_TOOL_OPTIONS \
    -u _JAVA_OPTIONS \
    -u JDK_JAVA_OPTIONS \
    -u MallocStackLogging \
    -u MallocStackLoggingNoCompact \
    JAVA_HOME="$java_home" \
    PATH="$java_home/bin:$PATH" \
    "${cmd[@]}" >> "$log_file" 2>&1
  return $?
}

hash_outputs() {
  local label="$1"
  local out="$REPORT/sha256-${label}.txt"
  : > "$out"
  local list="$REPORT/module-dirs.txt"
  # Hash every module output that exists, including -am dependencies, so a
  # later test goal cannot recompile a dependency unnoticed.
  find "$ROOT" -mindepth 1 -maxdepth 1 -type d -name 'serviceframework-*' -print | sed "s|$ROOT/||" | sort > "$list"
  local module classes tests jar
  while IFS= read -r module; do
    [ -n "$module" ] || continue
    classes="$ROOT/$module/target/classes"
    tests="$ROOT/$module/target/test-classes"
    if [ -d "$classes" ]; then
      find "$classes" -type f -name '*.class' -print | sort | while IFS= read -r file; do
        shasum -a 256 "$file"
      done >> "$out"
    fi
    if [ -d "$tests" ]; then
      find "$tests" -type f -name '*.class' -print | sort | while IFS= read -r file; do
        shasum -a 256 "$file"
      done >> "$out"
    fi
    if [ -d "$ROOT/$module/target" ]; then
      find "$ROOT/$module/target" -maxdepth 1 -type f -name '*.jar' -print | sort | while IFS= read -r jar; do
        shasum -a 256 "$jar"
      done >> "$out"
    fi
  done < "$list"
  note "sha256 $label entries=$(wc -l < "$out" | tr -d ' ') file=$out"
}

record_step() {
  local name="$1"
  local code="$2"
  note "STEP $name exit=$code"
  if [ "$code" -ne 0 ]; then
    FAILURES=1
  fi
}

# install (not package): sibling artifacts must exist in the isolated local
# repo so that the audit and the per-module test runs resolve the JARs built
# by this run. flatten-maven-plugin writes .flattened-pom.xml and repoints the
# reactor at it; it does not rewrite the source pom.xml files.
BUILD_LOG="$REPORT/build-jdk17.log"
BUILD_CMD=(install -DskipTests -DperformRelease=false -Dmaven.javadoc.skip=true)
if [ "${#VERSION_ARGS[@]}" -gt 0 ]; then
  BUILD_CMD+=("${VERSION_ARGS[@]}")
fi
if [ "${#PL_ARGS[@]}" -gt 0 ]; then
  BUILD_CMD+=("${PL_ARGS[@]}")
fi
BUILD_CMD+=("${REPO_ARGS[@]}")
note "STEP build-jdk17 command=mvn ${BUILD_CMD[*]}"
run_logged "$JDK17_HOME" "$BUILD_LOG" "${BUILD_CMD[@]}"
BUILD_EXIT=$?
record_step build-jdk17 "$BUILD_EXIT"
if [ "$BUILD_EXIT" -ne 0 ]; then
  note "STOP tests, animal-sniffer, and dependency audit were not run because the JDK 17 build failed."
  note "log=$BUILD_LOG"
  note "RESULT fail"
  exit 1
fi

hash_outputs after-build

SNIFFER_LOG="$REPORT/animal-sniffer.log"
SNIFFER_CMD=(org.codehaus.mojo:animal-sniffer-maven-plugin:1.24:check -DperformRelease=false)
if [ "${#VERSION_ARGS[@]}" -gt 0 ]; then
  SNIFFER_CMD+=("${VERSION_ARGS[@]}")
fi
if [ "${#PL_ARGS[@]}" -gt 0 ]; then
  SNIFFER_CMD+=("${PL_ARGS[@]}")
fi
SNIFFER_CMD+=("${REPO_ARGS[@]}")
note "STEP animal-sniffer command=mvn ${SNIFFER_CMD[*]}"
run_logged "$JDK17_HOME" "$SNIFFER_LOG" "${SNIFFER_CMD[@]}"
record_step animal-sniffer $?

TREE_LOG="$REPORT/dependency-tree.log"
TREE_CMD=(org.apache.maven.plugins:maven-dependency-plugin:3.6.1:tree -DoutputFile='${project.build.directory}/jdk-compat-tree.txt' -DperformRelease=false)
if [ "${#VERSION_ARGS[@]}" -gt 0 ]; then
  TREE_CMD+=("${VERSION_ARGS[@]}")
fi
if [ "${#PL_ARGS[@]}" -gt 0 ]; then
  TREE_CMD+=("${PL_ARGS[@]}")
fi
TREE_CMD+=("${REPO_ARGS[@]}")
note "STEP dependency-tree command=mvn ${TREE_CMD[*]}"
run_logged "$JDK17_HOME" "$TREE_LOG" "${TREE_CMD[@]}"
record_step dependency-tree $?
{
  echo "# javassist / guice / persistence / mysql lines"
  find "$ROOT" -path '*/target/jdk-compat-tree.txt' -print | sort | while IFS= read -r tree; do
    echo "## $tree"
    grep -E 'javassist|guice|javax.persistence|mysql-connector|mycila|hibernate-jpa' "$tree" || true
  done
} > "$REPORT/dependency-tree-key.txt"

AUDIT_CMD=(python3 "$ROOT/dev/audit-runtime-dependencies.py" --repo "$ROOT" --report "$REPORT/dependency-audit.txt" --local-repo "$M2" --maven-settings "$MAVEN_SETTINGS")
if [ -n "$JAVASSIST_VERSION" ]; then
  AUDIT_CMD+=(--javassist-version "$JAVASSIST_VERSION")
fi
if [ "${#SELECTED[@]}" -gt 0 ]; then
  for module in "${SELECTED[@]}"; do
    AUDIT_CMD+=(--module "$module")
  done
fi
note "STEP dependency-audit command=${AUDIT_CMD[*]}"
env \
  -u MAVEN_OPTS \
  -u JAVA_TOOL_OPTIONS \
  -u _JAVA_OPTIONS \
  -u JDK_JAVA_OPTIONS \
  -u MallocStackLogging \
  -u MallocStackLoggingNoCompact \
  JAVA_HOME="$JDK17_HOME" \
  PATH="$JDK17_HOME/bin:$PATH" \
  "${AUDIT_CMD[@]}" > "$REPORT/dependency-audit.stdout" 2>&1
record_step dependency-audit $?

stash_test_reports() {
  # Surefire leaves the previous run's XML in place. Move those directories
  # aside so this invocation can only be counted from files it just wrote.
  local label="$1"
  local stash="$REPORT/stale-test-reports-${label}"
  rm -rf "$stash"
  mkdir -p "$stash"
  local dir kind dest n
  n=0
  for dir in "$ROOT" "$ROOT"/serviceframework-*; do
    [ -d "$dir" ] || continue
    for kind in surefire-reports scalatest-reports; do
      if [ -d "$dir/target/$kind" ]; then
        dest="$stash/${n}-${kind}"
        mv "$dir/target/$kind" "$dest"
        n=$((n + 1))
      fi
    done
  done
}

count_fresh_reports() {
  # Surefire XML: one testsuite per class, tests includes skipped. ScalaTest
  # here writes a text file, not XML; take that file's last total so a suite
  # line and the run summary are not added together. Print "executed skipped files".
  python3 - "$ROOT" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(sys.argv[1])
executed = 0
skipped = 0
files = 0

def local(tag):
    return tag.rsplit("}", 1)[-1]

def take_suite(element):
    global executed, skipped
    tests = int(element.attrib.get("tests", "0") or 0)
    skip = int(element.attrib.get("skipped", "0") or 0)
    executed += max(tests - skip, 0)
    skipped += skip

modules = [root]
modules.extend(sorted(path for path in root.iterdir() if path.is_dir() and path.name.startswith("serviceframework-")))
for module in modules:
    for kind in ("surefire-reports", "scalatest-reports"):
        report_dir = module / "target" / kind
        if not report_dir.is_dir():
            continue
        xml_files = sorted(report_dir.glob("*.xml"))
        if xml_files:
            for xml in xml_files:
                try:
                    document = ET.parse(xml)
                except ET.ParseError as exc:
                    sys.stderr.write(f"unreadable test report {xml}: {exc}\n")
                    raise SystemExit(2)
                element = document.getroot()
                tag = local(element.tag)
                if tag == "testsuite":
                    take_suite(element)
                elif tag == "testsuites":
                    for child in list(element):
                        if local(child.tag) == "testsuite":
                            take_suite(child)
                else:
                    sys.stderr.write(f"unexpected test report root {tag} in {xml}\n")
                    raise SystemExit(2)
                files += 1
            continue
        for text_file in sorted(report_dir.glob("*.txt")):
            totals = []
            for line in text_file.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.startswith("Total number of tests run:"):
                    totals.append(line)
            if not totals:
                continue
            number = int(totals[-1].split(":", 1)[1].strip().split()[0].replace(",", ""))
            executed += number
            files += 1
sys.stdout.write(f"{executed} {skipped} {files}\n")
PY
}

run_tests() {
  local label="$1"
  local java_home="$2"
  local log_file="$REPORT/test-${label}.log"
  local cmd=(
    org.apache.maven.plugins:maven-surefire-plugin:3.2.5:test
    org.scalatest:scalatest-maven-plugin:2.2.0:test
    -DskipTests=false
    -DperformRelease=false
  )
  if [ "${#VERSION_ARGS[@]}" -gt 0 ]; then
    cmd+=("${VERSION_ARGS[@]}")
  fi
  if [ "${#PL_ARGS[@]}" -gt 0 ]; then
    cmd+=("${PL_ARGS[@]}")
  fi
  cmd+=("${REPO_ARGS[@]}")
  stash_test_reports "$label"
  note "STEP test-$label command=mvn -s <maven_settings> ${cmd[*]}"
  run_logged "$java_home" "$log_file" "${cmd[@]}"
  local code=$?
  if grep -q 'Tests are skipped' "$log_file"; then
    note "FAIL test-$label: surefire skipped tests"
    code=1
  fi
  local count_line executed skipped files
  if ! count_line="$(count_fresh_reports)"; then
    note "FAIL test-$label: could not read fresh Surefire or ScalaTest reports"
    code=1
    executed=0
    skipped=0
    files=0
  else
    read -r executed skipped files <<EOF
$count_line
EOF
  fi
  note "test-$label executed_tests=$executed skipped_tests=$skipped report_files=$files"
  if [ "$executed" -eq 0 ]; then
    note "FAIL test-$label: zero tests were executed (skipped=$skipped)"
    code=1
  fi
  record_step "test-$label" "$code"
  hash_outputs "after-${label}"
  if ! cmp -s "$REPORT/sha256-after-build.txt" "$REPORT/sha256-after-${label}.txt"; then
    note "FAIL test-$label: class or jar SHA256 changed, so the run did not reuse the JDK 17 build output"
    diff -u "$REPORT/sha256-after-build.txt" "$REPORT/sha256-after-${label}.txt" | head -n 80 >> "$SUMMARY" || true
    FAILURES=1
  else
    note "SHA256 unchanged after test-$label"
  fi
}

run_tests jdk8 "$JDK8_HOME"
run_tests jdk17 "$JDK17_HOME"

note "logs:"
note "  $BUILD_LOG"
note "  $SNIFFER_LOG"
note "  $TREE_LOG"
note "  $REPORT/dependency-audit.txt"
note "  $REPORT/test-jdk8.log"
note "  $REPORT/test-jdk17.log"
if [ "$FAILURES" -eq 0 ]; then
  note "RESULT pass"
  exit 0
fi
note "RESULT fail"
exit 1
