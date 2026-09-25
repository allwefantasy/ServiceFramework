#!/usr/bin/env bash
# Measure real ServiceFramework startup phases and HTTP calls on one already
# built Java 8 classpath. JDK 8 and JDK 17 both run that same output.
# This does not compile, install, or write ~/.m2.
#
# Cold is a new JVM. Repeated uses a new application loader in one JVM.
# Hot times GET /db/both on one context. None of these numbers is a historical
# JDK 17 startup baseline: the old loader could not start on JDK 17.
# Logical JDBC getTables/getColumns counts are API calls, not network round trips.
# Service and util packages in this fixture are empty.

set -u
set -o pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JDK8_HOME="${JDK8_HOME:-/usr/local/opt/openjdk@8}"
JDK17_HOME="${JDK17_HOME:-/usr/local/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home}"
PROJECT=""
REPO=""
OUT=""
SETTINGS=""
COLD=3
WARMUP=1
REPEATED=5
HOT=20

usage() {
  cat <<'EOF'
Usage:
  dev/measure-framework-phases.sh --project DIR --repo M2 --out DIR [--settings FILE]

--project is a tree already compiled once with JDK 17 to Java 8 bytecode.
--repo is the private Maven repository used to resolve third-party jars.
The script reads SF_COMPAT_ENV_FILE from the environment. When that variable
is unset it starts MySQL 8.0.46 and MongoDB 4.4.29 through dev/compat-services.sh
and runs itself once inside that session. It does not print the env file path
or credential values.
EOF
}

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --settings) SETTINGS="$2"; shift 2 ;;
    --cold) COLD="$2"; shift 2 ;;
    --warmup) WARMUP="$2"; shift 2 ;;
    --repeated) REPEATED="$2"; shift 2 ;;
    --hot) HOT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[ -n "$PROJECT" ] || die "--project is required"
[ -n "$REPO" ] || die "--repo is required"
[ -n "$OUT" ] || die "--out is required"
[ -d "$PROJECT" ] || die "project dir missing"
[ -d "$REPO" ] || die "repo missing"
[ -x "$JDK8_HOME/bin/java" ] || die "JDK 8 java missing"
[ -x "$JDK17_HOME/bin/java" ] || die "JDK 17 java missing"

if [ -z "${SF_COMPAT_ENV_FILE:-}" ]; then
  WRAP=(--project "$PROJECT" --repo "$REPO" --out "$OUT" --cold "$COLD" --warmup "$WARMUP" --repeated "$REPEATED" --hot "$HOT")
  if [ -n "$SETTINGS" ]; then
    WRAP+=(--settings "$SETTINGS")
  fi
  exec "$ROOT/dev/compat-services.sh" run -- env \
    SF_COMPAT_WEB_DB=true \
    JDK8_HOME="$JDK8_HOME" \
    JDK17_HOME="$JDK17_HOME" \
    "$ROOT/dev/measure-framework-phases.sh" "${WRAP[@]}"
fi

mkdir -p "$OUT"
SUMMARY="$OUT/framework-phases.txt"
CP_FILE="$OUT/classpath.txt"

unset JAVA_TOOL_OPTIONS
unset _JAVA_OPTIONS
unset JDK_JAVA_OPTIONS
unset MAVEN_OPTS
unset MallocStackLogging
export SF_COMPAT_WEB_DB=true
MARKER_TEXT="sf-phase-measure-marker"

hash_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

{
  printf 'project=%s\n' "$PROJECT"
  printf 'jdk8='
  "$JDK8_HOME/bin/java" -version 2>&1 | tr '\n' ' '
  printf '\njdk17='
  "$JDK17_HOME/bin/java" -version 2>&1 | tr '\n' ' '
  printf '\n'
} >"$SUMMARY"

MODULES="serviceframework-common serviceframework-orm serviceframework-mongo serviceframework-web serviceframework-dispatcher serviceframework-jetty-9-server"
CP=""
for module in $MODULES; do
  classes="$PROJECT/$module/target/classes"
  [ -d "$classes" ] || die "missing $classes"
  CP="${CP:+$CP:}$classes"
done
WEB_TEST="$PROJECT/serviceframework-web/target/test-classes"
[ -d "$WEB_TEST" ] || die "missing web test classes"
CP="$CP:$WEB_TEST"

MVN_ARGS=(-o -B --no-transfer-progress -DperformRelease=false -Dmaven.javadoc.skip=true -Dmaven.repo.local="$REPO")
if [ -n "$SETTINGS" ]; then
  MVN_ARGS+=(-s "$SETTINGS")
fi
# ORM and Mongo are provided on the web module. runtime scope drops them, and
# -am writes this output file once per reactor module. Test scope of web alone
# keeps compile, runtime, provided and test dependencies, including the driver.
(
  cd "$PROJECT" || exit 1
  env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS -u JDK_JAVA_OPTIONS -u MAVEN_OPTS -u MallocStackLogging \
    JAVA_HOME="$JDK17_HOME" \
    mvn "${MVN_ARGS[@]}" -pl serviceframework-web \
    dependency:build-classpath -DincludeScope=test -Dmdep.outputFile="$CP_FILE"
) >>"$OUT/classpath-maven.log" 2>&1 || die "dependency classpath failed; see classpath-maven.log"
DEPS="$(cat "$CP_FILE")"
CP="$CP:$DEPS"
# dependency:build-classpath does not include the web module's own jar.
# The six target/classes directories stay first. The installed web jar is
# appended so the rebuilt artifact is on the classpath and in the hash set.
WEB_INSTALLED="$REPO/net/csdn/serviceframework-web_2.13/2.0.9/serviceframework-web_2.13-2.0.9.jar"
[ -f "$WEB_INSTALLED" ] || die "installed web jar missing"
case ":$CP:" in
  *":$WEB_INSTALLED:"*) ;;
  *) CP="$CP:$WEB_INSTALLED" ;;
esac
printf '%s' "$CP" | tr ':' '\n' >"$OUT/classpath-entries.txt"

record_hashes() {
  local dest="$1"
  python3 - "$PROJECT" "$REPO" "$CP_FILE" "$dest" "$OUT/class-list.txt" "$OUT/classpath-proof.txt" <<'PY'
import hashlib
import os
import sys
import zipfile

project, repo, dep_file, dest, class_list, proof = sys.argv[1:7]
modules = [
    "serviceframework-common",
    "serviceframework-orm",
    "serviceframework-mongo",
    "serviceframework-web",
    "serviceframework-dispatcher",
    "serviceframework-jetty-9-server",
]
deps = [item for item in open(dep_file, "r").read().strip().split(":") if item]
jars = [path for path in deps if path.endswith(".jar")]
missing = [path for path in jars if not os.path.isfile(path)]
if missing:
    sys.stderr.write("classpath jar missing: %s\n" % missing[0])
    raise SystemExit(2)

def find_class(binary_name):
    needle = binary_name.replace(".", "/") + ".class"
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            try:
                archive.getinfo(needle)
            except KeyError:
                continue
            return jar
    return None

mongo_jar = find_class("com.mongodb.MongoClientOptions")
mysql_jar = find_class("com.mysql.jdbc.Driver")
if mongo_jar is None or mysql_jar is None:
    sys.stderr.write("test classpath is missing the MongoDB or MySQL driver\n")
    raise SystemExit(2)

lines = []
listed = []

def digest_bytes(data):
    return hashlib.sha256(data).hexdigest()

def add_file(path, label):
    data = open(path, "rb").read()
    lines.append("%s  %s\n" % (digest_bytes(data), label))
    listed.append(path)

def installed_jar(module):
    path = os.path.join(
        repo, "net", "csdn", module + "_2.13", "2.0.9", module + "_2.13-2.0.9.jar")
    if not os.path.isfile(path):
        sys.stderr.write("installed jar missing for %s\n" % module)
        raise SystemExit(2)
    return path

installed = {}
for module in modules:
    installed[module] = installed_jar(module)
for required in (
        "serviceframework-common",
        "serviceframework-orm",
        "serviceframework-mongo",
        "serviceframework-jetty-9-server"):
    if installed[required] not in jars:
        sys.stderr.write("test classpath is missing %s\n" % installed[required])
        raise SystemExit(2)

for module in modules:
    root = os.path.join(project, module, "target", "classes")
    jar = installed[module]
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        class_files = []
        for dirpath, _, filenames in os.walk(root):
            for filename in filenames:
                if filename.endswith(".class"):
                    class_files.append(os.path.join(dirpath, filename))
        if not class_files:
            sys.stderr.write("no classes in %s\n" % root)
            raise SystemExit(2)
        for full in sorted(class_files):
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            if rel not in names:
                sys.stderr.write("class missing from installed jar %s %s\n" % (rel, jar))
                raise SystemExit(2)
            file_bytes = open(full, "rb").read()
            if digest_bytes(file_bytes) != digest_bytes(archive.read(rel)):
                sys.stderr.write("class bytes differ from installed jar %s\n" % rel)
                raise SystemExit(2)
            add_file(full, os.path.relpath(full, project))

fixture_root = os.path.join(
    project, "serviceframework-web", "target", "test-classes",
    "net", "csdn", "bootstrap", "lifecycle")
fixture_files = []
for dirpath, _, filenames in os.walk(fixture_root):
    for filename in filenames:
        if filename.endswith(".class"):
            fixture_files.append(os.path.join(dirpath, filename))
if not fixture_files:
    sys.stderr.write("precompiled web fixture classes are missing\n")
    raise SystemExit(2)
for full in sorted(fixture_files):
    add_file(full, os.path.relpath(full, project))

hashed_jars = []
seen_jars = set()
for jar in jars + [installed[module] for module in modules]:
    if jar not in seen_jars:
        seen_jars.add(jar)
        hashed_jars.append(jar)
for jar in sorted(hashed_jars):
    add_file(jar, jar)

class_file_count = len(listed) - len(hashed_jars)
lines.sort()
listed.sort()
with open(dest, "w") as handle:
    handle.writelines(lines)
with open(class_list, "w") as handle:
    handle.write("\n".join(listed))
    handle.write("\n")
with open(proof, "w") as handle:
    handle.write("modules=6\n")
    handle.write("classFiles=%d\n" % class_file_count)
    handle.write("fixtureClassFiles=%d\n" % len(fixture_files))
    handle.write("jars=%d\n" % len(hashed_jars))
    handle.write("dependencyEntries=%d\n" % len(deps))
    handle.write("classpathEntries=%d\n" % (6 + 1 + len(deps) + (0 if installed["serviceframework-web"] in jars else 1)))
    handle.write("mongoDriverJar=%s\n" % mongo_jar)
    handle.write("mysqlDriverJar=%s\n" % mysql_jar)
    handle.write("sameClassBytesAsInstalledJars=true\n")
    handle.write("scope=test\n")
    handle.write("reactorModulesInClasspathResolution=serviceframework-web\n")
    handle.write("webOwnJar=%s\n" % installed["serviceframework-web"])
PY
}

record_hashes "$OUT/artifact-hashes.before" || die "classpath proof failed"
cat "$OUT/classpath-proof.txt" >>"$SUMMARY"

run_java() {
  local home="$1" name="$2" markers="$3"
  shift 3
  local log="$OUT/$name.log"
  printf 'command %s exit ' "$name" >>"$SUMMARY"
  env -u JAVA_TOOL_OPTIONS -u _JAVA_OPTIONS -u JDK_JAVA_OPTIONS -u MAVEN_OPTS -u MallocStackLogging \
    SF_COMPAT_WEB_DB=true \
    "$home/bin/java" -cp "$CP" net.csdn.bootstrap.lifecycle.FrameworkPhaseBenchmark "$@" >"$log" 2>&1
  local status=$?
  printf '%s\n' "$status" >>"$SUMMARY"
  if [ "$status" -ne 0 ]; then
    tail -40 "$log" >&2
    die "$name failed"
  fi
  local file_log="$OUT/logs/$name/sf-phase-measure.log"
  local file_count console_count
  file_count="$(grep -c "$MARKER_TEXT" "$file_log" || true)"
  console_count="$(grep -c "$MARKER_TEXT" "$log" || true)"
  printf 'marker %s file=%s console=%s\n' "$name" "$file_count" "$console_count" >>"$SUMMARY"
  if [ "$file_count" -lt "$markers" ] || [ "$console_count" -lt "$markers" ]; then
    die "$name marker missing from the log file or the console capture"
  fi
}

for home_name in jdk8 jdk17; do
  if [ "$home_name" = "jdk8" ]; then
    HOME_DIR="$JDK8_HOME"
  else
    HOME_DIR="$JDK17_HOME"
  fi
  n=1
  while [ "$n" -le "$COLD" ]; do
    run_java "$HOME_DIR" "$home_name-cold-$n" 1 \
      --group cold --iterations 1 --warmup 0 \
      --log-dir "$OUT/logs/$home_name-cold-$n" \
      --out "$OUT/$home_name-cold-$n.json"
    n=$((n + 1))
  done
  run_java "$HOME_DIR" "$home_name-repeated" "$((WARMUP + REPEATED))" \
    --group repeated --warmup "$WARMUP" --iterations "$REPEATED" \
    --log-dir "$OUT/logs/$home_name-repeated" \
    --out "$OUT/$home_name-repeated.json"
  run_java "$HOME_DIR" "$home_name-hot" 1 \
    --group hot --warmup "$WARMUP" --iterations "$HOT" \
    --log-dir "$OUT/logs/$home_name-hot" \
    --out "$OUT/$home_name-hot.json"
  run_java "$HOME_DIR" "$home_name-smoke" 1 \
    --group smoke \
    --log-dir "$OUT/logs/$home_name-smoke" \
    --out "$OUT/$home_name-smoke.json"
done

record_hashes "$OUT/artifact-hashes.after" || die "classpath proof failed after the runs"
if ! cmp -s "$OUT/artifact-hashes.before" "$OUT/artifact-hashes.after"; then
  die "class or jar hashes changed while the two JDKs were running"
fi
printf 'artifactHashes=unchanged\n' >>"$SUMMARY"
python3 - "$OUT" "$COLD" "$WARMUP" "$REPEATED" "$HOT" <<'PY'
import json
import math
import os
import sys

out, cold_n, warmup_n, repeated_n, hot_n = sys.argv[1:6]
cold_n = int(cold_n)
warmup_n = int(warmup_n)
repeated_n = int(repeated_n)
hot_n = int(hot_n)

def load(name):
    path = os.path.join(out, name)
    with open(path, "r") as handle:
        return json.load(handle)

def percentile(ordered, percent):
    index = int(math.ceil(percent / 100.0 * len(ordered))) - 1
    if index < 0:
        index = 0
    if index >= len(ordered):
        index = len(ordered) - 1
    return ordered[index]

def distribution(values, unit):
    ordered = sorted(values)
    if not ordered:
        raise SystemExit("empty distribution")
    return {
        "n": len(ordered),
        "min": ordered[0],
        "p50": percentile(ordered, 50),
        "p95": percentile(ordered, 95),
        "max": ordered[-1],
        "mean": sum(ordered) // len(ordered),
        "unit": unit,
        "smallSample": True,
    }

def phase_distributions(samples):
    names = []
    seen = set()
    for sample in samples:
        for phase in sample["phases"]:
            if phase["name"] not in seen:
                seen.add(phase["name"])
                names.append(phase["name"])
    rows = []
    for name in names:
        nested_flags = []
        values = []
        for sample in samples:
            matches = [phase for phase in sample["phases"] if phase["name"] == name]
            if len(matches) != 1 and name not in ("define",) and not name.startswith("rule."):
                # define and rule samples repeat once per class; other names should be one each
                pass
            if not matches:
                raise SystemExit("phase %s missing from a sample" % name)
            nested_flags.append(matches[0]["nested"])
            values.append(sum(phase["nanos"] for phase in matches))
        rows.append({
            "name": name,
            "nested": nested_flags[0],
            "nestedSameInEverySample": all(flag == nested_flags[0] for flag in nested_flags),
            "occurrencesPerSample": [sum(1 for phase in sample["phases"] if phase["name"] == name) for sample in samples],
            "sumNanos": distribution(values, "nanoseconds"),
        })
    return rows

def check_sample(sample):
    non_nested = 0
    nested = 0
    for phase in sample["phases"]:
        if phase["nested"]:
            nested += phase["nanos"]
        else:
            non_nested += phase["nanos"]
    jdbc = sample["jdbc"]["elapsedNanos"]
    if sample["exclusiveNanos"] != non_nested + jdbc:
        raise SystemExit("exclusive nanos is not non-nested phases plus jdbc")
    if sample["residualNanos"] != sample["wallNanos"] - sample["exclusiveNanos"]:
        raise SystemExit("residual nanos does not match wall minus exclusive")
    if sample["residualNanos"] < 0:
        raise SystemExit("residual nanos is negative")
    if sample["jdbc"]["getTablesCalls"] != 1 or sample["jdbc"]["getColumnsCalls"] != 1:
        raise SystemExit("jdbc metadata calls were not one getTables and one getColumns")
    return nested

def startup_block(samples, group, cache_note):
    for sample in samples:
        check_sample(sample)
    return {
        "group": group,
        "samples": len(samples),
        "wallNanos": distribution([sample["wallNanos"] for sample in samples], "nanoseconds"),
        "exclusiveNanos": distribution([sample["exclusiveNanos"] for sample in samples], "nanoseconds"),
        "residualNanos": distribution([sample["residualNanos"] for sample in samples], "nanoseconds"),
        "businessCallNanos": distribution([sample["businessCallNanos"] for sample in samples], "nanoseconds"),
        "jdbcElapsedNanos": distribution([sample["jdbc"]["elapsedNanos"] for sample in samples], "nanoseconds"),
        "heapBeforeCloseBytes": distribution([sample["memoryBeforeClose"]["heapUsed"] for sample in samples], "bytes"),
        "heapAfterCloseAndGcHintBytes": distribution([sample["memoryAfterCloseAndGcHint"]["heapUsed"] for sample in samples], "bytes"),
        "metaspaceBeforeCloseBytes": distribution([sample["memoryBeforeClose"]["metaspaceUsed"] for sample in samples], "bytes"),
        "metaspaceAfterCloseAndGcHintBytes": distribution([sample["memoryAfterCloseAndGcHint"]["metaspaceUsed"] for sample in samples], "bytes"),
        "gcCountBefore": [sample["memoryBeforeClose"]["gcCount"] for sample in samples],
        "gcCountAfterHint": [sample["memoryAfterCloseAndGcHint"]["gcCount"] for sample in samples],
        "gcTimeMsBefore": [sample["memoryBeforeClose"]["gcTimeMs"] for sample in samples],
        "gcTimeMsAfterHint": [sample["memoryAfterCloseAndGcHint"]["gcTimeMs"] for sample in samples],
        "phases": phase_distributions(samples),
        "cacheNote": cache_note,
        "memoryNote": samples[-1]["gcNote"],
    }

def fmt_dist(item):
    return "min %d / p50 %d / p95 %d / max %d / mean %d %s (n=%d)" % (
        item["min"], item["p50"], item["p95"], item["max"], item["mean"], item["unit"], item["n"])

report = {
    "label": "new-full-runtime-baseline",
    "historicalJdk17Startup": False,
    "notAnOverallStartupSpeedup": True,
    "noEnhancementResultCache": True,
    "smallSample": True,
    "osAndDatabasesAlreadyWarm": True,
    "coldFreshJvm": cold_n,
    "repeatedWarmupDiscarded": warmup_n,
    "repeatedFreshLoaderSamples": repeated_n,
    "hotHttpCalls": hot_n,
    "fixture": {
        "route": "/db/both",
        "servicePackages": "",
        "utilPackages": "",
        "mysql": "8.0.46 loopback",
        "mongo": "4.4.29 loopback",
        "note": "Service and util packages are empty. MySQL and MongoDB were already running. OS page cache is not a cold disk.",
    },
    "exclusiveDefinition": "Sum of phase samples with nested=false, plus the one JDBC metadata refresh. That refresh runs in the DBInfo constructor before scan.orm, so it is not inside a phase sample. nested=true samples stay in the phase list and are not added again. residualNanos is wallNanos minus that sum.",
    "scanner": "Logical streams peaked at 25 then 1 for 25 classes. p50 about 15.1 ms versus 14.1 ms overlaps. Not an overall startup speedup and not an OS file-descriptor reduction.",
    "jdks": {},
}
lines = []
lines.append("Full runtime phase baseline. Not a historical JDK 17 startup comparison.")
lines.append("Cold: %d fresh JVMs. Repeated: %d discarded warmup then %d fresh loaders in one JVM. Hot: %d HTTP calls on one context." % (
    cold_n, warmup_n, repeated_n, hot_n))
lines.append("Small sample. OS page cache and the loopback databases were already warm. No enhancement-result cache and no overall startup-speedup claim.")
lines.append("Same Java 8 class files on both JDKs. Hashes cover those class files, the precompiled web fixtures, and the jars actually on the classpath.")
lines.append("")
for jdk in ("jdk8", "jdk17"):
    cold = []
    for index in range(1, cold_n + 1):
        document = load("%s-cold-%d.json" % (jdk, index))
        if document.get("group") != "cold" or len(document["samples"]) != 1:
            raise SystemExit("unexpected cold file for %s" % jdk)
        cold.extend(document["samples"])
    repeated = load("%s-repeated.json" % jdk)
    if repeated.get("warmup") != warmup_n or len(repeated["samples"]) != repeated_n:
        raise SystemExit("unexpected repeated file for %s" % jdk)
    hot = load("%s-hot.json" % jdk)
    if len(hot["calls"]) != hot_n:
        raise SystemExit("unexpected hot file for %s" % jdk)
    smoke = load("%s-smoke.json" % jdk)
    if smoke.get("callerApplicationRevision") != "config-20260924" or not smoke.get("eventsKeptCallerRevision"):
        raise SystemExit("smoke did not keep the caller revision")
    if not smoke.get("classDumps") or smoke.get("sourceDumps"):
        raise SystemExit("smoke class dump evidence is incomplete")
    block = {
        "jvm": cold and load("%s-cold-1.json" % jdk)["jvm"],
        "cold": startup_block(cold, "cold", load("%s-cold-1.json" % jdk)["cacheNote"]),
        "repeated": startup_block(repeated["samples"], "repeated-fresh-loader", repeated["cacheNote"]),
        "hot": {
            "startupWallNanos": hot["startupWallNanos"],
            "callNanos": hot["callNanosDistribution"],
            "route": hot["route"],
            "cacheNote": hot["cacheNote"],
        },
        "smoke": {
            "notBaseline": True,
            "callerApplicationRevision": smoke["callerApplicationRevision"],
            "bootstrapDefaultWhenCallerOmitsRevision": smoke["bootstrapDefaultWhenCallerOmitsRevision"],
            "moduleFormatTokens": smoke["moduleFormatTokens"],
            "digestBeforeAlter": smoke["digestBeforeAlter"],
            "digestAfterAlter": smoke["digestAfterAlter"],
            "classDumps": True,
            "sourceDumps": False,
            "jvm": smoke["jvm"],
        },
    }
    report["jdks"][jdk] = block
    lines.append(jdk)
    lines.append("  jvm %s" % json.dumps(block["jvm"], sort_keys=True))
    lines.append("  cold wall %s" % fmt_dist(block["cold"]["wallNanos"]))
    lines.append("  cold exclusive %s" % fmt_dist(block["cold"]["exclusiveNanos"]))
    lines.append("  cold residual %s" % fmt_dist(block["cold"]["residualNanos"]))
    lines.append("  cold jdbc metadata %s" % fmt_dist(block["cold"]["jdbcElapsedNanos"]))
    lines.append("  cold business call %s" % fmt_dist(block["cold"]["businessCallNanos"]))
    lines.append("  cold heap before close %s" % fmt_dist(block["cold"]["heapBeforeCloseBytes"]))
    lines.append("  cold heap after close and gc hint %s" % fmt_dist(block["cold"]["heapAfterCloseAndGcHintBytes"]))
    lines.append("  cold metaspace before / after bytes p50 %d / %d" % (
        block["cold"]["metaspaceBeforeCloseBytes"]["p50"],
        block["cold"]["metaspaceAfterCloseAndGcHintBytes"]["p50"]))
    lines.append("  cold gc count before %s after %s" % (block["cold"]["gcCountBefore"], block["cold"]["gcCountAfterHint"]))
    lines.append("  repeated wall %s" % fmt_dist(block["repeated"]["wallNanos"]))
    lines.append("  repeated exclusive %s" % fmt_dist(block["repeated"]["exclusiveNanos"]))
    lines.append("  repeated residual %s" % fmt_dist(block["repeated"]["residualNanos"]))
    lines.append("  hot call %s" % fmt_dist(block["hot"]["callNanos"]))
    lines.append("  smoke caller revision %s; digest %s -> %s" % (
        smoke["callerApplicationRevision"], smoke["digestBeforeAlter"], smoke["digestAfterAlter"]))
    lines.append("  phases (sum per sample; nested samples are not in exclusive):")
    for phase in block["cold"]["phases"]:
        lines.append("    %s nested=%s %s" % (phase["name"], str(phase["nested"]).lower(), fmt_dist(phase["sumNanos"])))
    lines.append("")

lines.append(report["exclusiveDefinition"])
lines.append(report["scanner"])
path_json = os.path.join(out, "framework-phases-aggregate.json")
path_text = os.path.join(out, "framework-phases-aggregate.txt")
with open(path_json, "w") as handle:
    json.dump(report, handle, indent=2, sort_keys=True)
    handle.write("\n")
with open(path_text, "w") as handle:
    handle.write("\n".join(lines))
    handle.write("\n")
PY
aggregate_status=$?
if [ "$aggregate_status" -ne 0 ]; then
  die "aggregate failed"
fi
printf 'aggregate=%s/framework-phases-aggregate.json\n' "$OUT" >>"$SUMMARY"
printf 'scannerBenchmark=/tmp/sf-t12-bench-verified/enhancement-costs.json peak 25 to 1 at 25 classes; p50 about 15.1ms vs 14.1ms overlaps. Not an overall startup speedup and not an FD-count reduction.\n' >>"$SUMMARY"
printf 'done\n' >>"$SUMMARY"
