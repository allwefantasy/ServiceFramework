#!/usr/bin/env bash
# Measure public enhancement diagnostics cost and scan callback stream peak.
# This does not install, deploy, or run Javadoc. It does not edit ~/.m2.
#
# The benchmark class is serviceframework-common's EnhancementCostBenchmark.
# Pass the Java 8 class output and a Maven repo that already contains javassist
# and common-utils. JDK 17 compiles nothing here; it only runs the benchmark
# unless --compile-benchmark is given.

set -u
set -o pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK17_HOME="${JDK17_HOME:-/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home}"
CLASSES=""
TEST_CLASSES=""
REPO=""
OUT=""
SETTINGS=""
CLASSES_N=24
WARMUP=3
ITERATIONS=11
COMPILE=0

usage() {
  cat <<'EOF'
Usage:
  dev/measure-enhancement-costs.sh --classes DIR --test-classes DIR --repo M2 --out DIR
      [--settings FILE] [--fixture-classes N] [--warmup N] [--iterations N]
      [--compile-benchmark]

--classes is serviceframework-common Java 8 main output.
--test-classes must contain net.csdn.common.scan.EnhancementCostBenchmark.
--repo is a private Maven repository. This script only reads jars from it.
Logical stream counts in the report are not OS file descriptors.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --classes) CLASSES="$2"; shift 2 ;;
    --test-classes) TEST_CLASSES="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --settings) SETTINGS="$2"; shift 2 ;;
    --fixture-classes) CLASSES_N="$2"; shift 2 ;;
    --warmup) WARMUP="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --compile-benchmark) COMPILE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$CLASSES" ] || die "--classes is required"
[ -n "$TEST_CLASSES" ] || die "--test-classes is required"
[ -n "$REPO" ] || die "--repo is required"
[ -n "$OUT" ] || die "--out is required"
[ -d "$CLASSES" ] || die "classes dir missing: $CLASSES"
[ -d "$REPO" ] || die "repo missing: $REPO"
[ -x "$JDK17_HOME/bin/java" ] || die "JDK17 java missing: $JDK17_HOME"
mkdir -p "$OUT"

find_jar() {
  local name="$1"
  local found
  found="$(find "$REPO" -name "$name" -type f -print -quit)"
  [ -n "$found" ] || die "missing jar $name under $REPO"
  printf '%s\n' "$found"
}

JAVASSIST="$(find_jar 'javassist-3.33.0-GA.jar')"
UTILS="$(find_jar 'common-utils_2.13-1.0.0.jar')"
SCALA="$(find_jar 'scala-library-2.13*.jar')"

if [ "$COMPILE" -eq 1 ]; then
  mkdir -p "$TEST_CLASSES"
  "$JDK17_HOME/bin/javac" --release 8 \
    -cp "$CLASSES:$JAVASSIST:$UTILS:$SCALA" \
    -d "$TEST_CLASSES" \
    "$ROOT/serviceframework-common/src/test/java/net/csdn/common/scan/EnhancementCostBenchmark.java" \
    || die "benchmark javac failed"
fi

[ -f "$TEST_CLASSES/net/csdn/common/scan/EnhancementCostBenchmark.class" ] \
  || die "benchmark class missing; pass --compile-benchmark or compile tests first"

CP="$TEST_CLASSES:$CLASSES:$JAVASSIST:$UTILS:$SCALA"
{
  printf 'COMMAND:'
  printf ' %q' "$JDK17_HOME/bin/java" -cp "$CP" net.csdn.common.scan.EnhancementCostBenchmark \
    --out "$OUT" --classes "$CLASSES_N" --warmup "$WARMUP" --iterations "$ITERATIONS"
  printf '\nJDK17_HOME=%s\n' "$JDK17_HOME"
  printf 'CLASSES=%s\nTEST_CLASSES=%s\nREPO=%s\n' "$CLASSES" "$TEST_CLASSES" "$REPO"
} > "$OUT/command.txt"

env \
  -u JAVA_TOOL_OPTIONS \
  -u _JAVA_OPTIONS \
  -u JDK_JAVA_OPTIONS \
  -u MAVEN_OPTS \
  "$JDK17_HOME/bin/java" -cp "$CP" net.csdn.common.scan.EnhancementCostBenchmark \
  --out "$OUT" \
  --classes "$CLASSES_N" \
  --warmup "$WARMUP" \
  --iterations "$ITERATIONS" \
  > "$OUT/benchmark.out" 2> "$OUT/benchmark.err"
code=$?
printf 'exit=%s\n' "$code" >> "$OUT/command.txt"
exit "$code"
