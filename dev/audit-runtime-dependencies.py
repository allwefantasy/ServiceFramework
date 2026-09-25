#!/usr/bin/env python3
"""Audit the resolved Maven classpath for the JDK 8 runtime baseline.

Resolves the test-scope classpath with maven-dependency-plugin
(build-classpath) using the same Maven flags as the test runs, then scans the
resolved entries verbatim. Reactor dependencies appear as the sibling module's
own build output (target/classes or its jar); nothing is rewritten or
substituted. The module's own target/classes and target/test-classes are
appended so project bytecode is checked too.

Rules:
- JDK 8-visible classes must be major 52 or older. Multi-release classes under
  META-INF/versions/ and module-info.class are reported, not failed.
- Every class under javassist/, com/google/inject/, javax/persistence/,
  javax/xml/bind/, javax/activation/, org/slf4j/, and net/csdn/jpa/ must come
  from exactly one classpath entry, so a second Javassist, a second activation
  or JAXB API, an SLF4J binding beside the API, or a standalone ActiveORM jar
  cannot share the classpath.
- Resolution failure, empty classpaths, zero visible classes, unreadable
  class entries, or duplicates are all failures.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shlex
import subprocess
import sys
import zipfile
from collections import defaultdict
from pathlib import Path
from xml.etree import ElementTree as ET

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
KEY_PREFIXES = (
    "javassist/",
    "com/google/inject/",
    "javax/persistence/",
    "javax/xml/bind/",
    "javax/activation/",
    "org/slf4j/",
    "net/csdn/jpa/",
)
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath"
CLASS_MAGIC = b"\xca\xfe\xba\xbe"
MAX_VISIBLE_MAJOR = 52


def pom_root(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def child_text(root: ET.Element, name: str, default: str | None = None) -> str:
    element = root.find(f"m:{name}", NS)
    if element is None or element.text is None:
        if default is not None:
            return default
        raise SystemExit(f"pom missing <{name}>: {root}")
    return element.text.strip()


def module_names(repo: Path) -> list[str]:
    root = pom_root(repo / "pom.xml")
    names = []
    for element in root.findall("m:modules/m:module", NS):
        if element.text and element.text.strip():
            names.append(element.text.strip())
    if not names:
        raise SystemExit(f"no modules in {repo / 'pom.xml'}")
    return names


def artifact_id(module_dir: Path) -> str:
    return child_text(pom_root(module_dir / "pom.xml"), "artifactId")


def packaging(module_dir: Path) -> str:
    return child_text(pom_root(module_dir / "pom.xml"), "packaging", "jar")


def project_version(repo: Path) -> str:
    return child_text(pom_root(repo / "pom.xml"), "version")


def module_output(module_dir: Path, artifact: str, version: str) -> Path | None:
    jar = module_dir / "target" / f"{artifact}-{version}.jar"
    classes = module_dir / "target" / "classes"
    if jar.is_file():
        return jar
    if classes.is_dir() and any(classes.rglob("*.class")):
        return classes
    return None


def class_major(data: bytes) -> int | None:
    if len(data) < 8 or data[:4] != CLASS_MAGIC:
        return None
    return int.from_bytes(data[6:8], "big")


def iter_class_entries(path: Path):
    if path.is_dir():
        for file_path in sorted(path.rglob("*")):
            if not file_path.is_file():
                continue
            rel = file_path.relative_to(path).as_posix()
            if rel.endswith(".class"):
                yield rel, file_path.read_bytes()
        return
    if not path.is_file():
        raise SystemExit(f"classpath entry does not exist: {path}")
    try:
        archive = zipfile.ZipFile(path)
    except zipfile.BadZipFile as exc:
        raise SystemExit(f"unreadable jar {path}: {exc}") from exc
    with archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            name = info.filename
            if name.endswith(".class"):
                yield name, archive.read(info)


def is_mrjar(name: str) -> bool:
    return name.startswith("META-INF/versions/")


def is_module_info(name: str) -> bool:
    return name == "module-info.class" or name.endswith("/module-info.class")


def key_prefix(name: str) -> str | None:
    if not name.endswith(".class"):
        return None
    for prefix in KEY_PREFIXES:
        if name.startswith(prefix):
            return prefix
    return None


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_classpath(
    repo: Path,
    selected: list[str],
    scope: str,
    local_repo: Path | None,
    extra_args: list[str],
    maven_settings: Path | None,
) -> dict[str, list[Path]]:
    for name in module_names(repo):
        stale = repo / name / "target" / "jdk-compat-classpath.txt"
        if stale.exists():
            stale.unlink()
    command = [
        "mvn",
        "-B",
        "--no-transfer-progress",
    ]
    if maven_settings is not None:
        command.extend(["-s", str(maven_settings)])
    command.extend(
        [
            "-f",
            str(repo / "pom.xml"),
            "-Daether.connector.connectTimeout=20000",
            "-Daether.connector.requestTimeout=120000",
            "-Daether.connector.retryCount=2",
        ]
    )
    if local_repo is not None:
        command.append(f"-Dmaven.repo.local={local_repo}")
    command.extend(extra_args)
    if selected:
        command.extend(["-pl", ",".join(selected), "-am"])
    command.extend(
        [
            DEPENDENCY_PLUGIN,
            f"-DincludeScope={scope}",
            "-Dmdep.pathSeparator=:",
            "-Dmdep.outputFile=${project.build.directory}/jdk-compat-classpath.txt",
        ]
    )
    print("COMMAND:", shlex.join(command), flush=True)
    child_env = os.environ.copy()
    for key in (
        "MAVEN_OPTS",
        "JAVA_TOOL_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "MallocStackLogging",
        "MallocStackLoggingNoCompact",
    ):
        child_env.pop(key, None)
    completed = subprocess.run(
        command, cwd=repo, text=True, capture_output=True, env=child_env
    )
    sys.stdout.write(completed.stdout)
    sys.stderr.write(completed.stderr)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)

    written = {}
    for name in module_names(repo):
        classpath_file = repo / name / "target" / "jdk-compat-classpath.txt"
        if classpath_file.is_file():
            text = classpath_file.read_text(encoding="utf-8").strip()
            entries = [Path(item) for item in text.split(":") if item]
            if entries:
                written[name] = entries
    missing = [name for name in selected if name not in written]
    if missing:
        raise SystemExit("Maven did not write a classpath for: " + ", ".join(missing))
    if not written:
        raise SystemExit("Maven wrote no classpath files")
    return written


def audit_module(
    repo: Path,
    module: str,
    entries: list[Path],
    artifacts: dict[str, str],
    version: str,
    report,
) -> dict:
    module_dir = repo / module
    resolved = []
    seen = set()
    own_classes = module_dir / "target" / "classes"
    own_test_classes = module_dir / "target" / "test-classes"
    # The module's own compiled output is not part of its dependency
    # classpath; scan it so project bytecode is checked against major 52 too.
    for entry in [own_classes, own_test_classes]:
        if entry.is_dir():
            resolved.append(entry)
            seen.add(str(entry.resolve()))
    for entry in entries:
        key = str(entry.resolve()) if entry.exists() else str(entry)
        if key in seen:
            continue
        seen.add(key)
        resolved.append(entry)

    stats = {
        "archives": 0,
        "visible": 0,
        "over": [],
        "bad": [],
        "mrjar": defaultdict(int),
        "mrjar_majors": defaultdict(set),
        "module_info": [],
        "owners": defaultdict(set),
    }
    print(f"MODULE {module}", file=report)
    output = module_output(module_dir, artifacts[module], version)
    print(f"  output: {output}", file=report)
    for entry in resolved:
        if entry.is_file():
            print(f"  classpath: {entry} sha256={sha256_file(entry)}", file=report)
        else:
            print(f"  classpath: {entry}", file=report)
        stats["archives"] += 1
        label = str(entry)
        for name, data in iter_class_entries(entry):
            major = class_major(data)
            if major is None:
                stats["bad"].append(f"{label}!{name}")
                continue
            if is_mrjar(name):
                stats["mrjar"][label] += 1
                stats["mrjar_majors"][label].add(major)
                continue
            if is_module_info(name):
                stats["module_info"].append(f"{label}!{name} major={major}")
                continue
            stats["visible"] += 1
            if major > MAX_VISIBLE_MAJOR:
                stats["over"].append(f"major {major} {label}!{name}")
            prefix = key_prefix(name)
            if prefix:
                stats["owners"][name].add(label)
    duplicates = {name: sorted(labels) for name, labels in stats["owners"].items() if len(labels) > 1}
    print(f"  visible_classes: {stats['visible']}", file=report)
    print(f"  over_52: {len(stats['over'])}", file=report)
    print(f"  unreadable_class_entries: {len(stats['bad'])}", file=report)
    print(f"  module_info: {len(stats['module_info'])}", file=report)
    print(f"  duplicate_key_classes: {len(duplicates)}", file=report)
    if stats["mrjar"]:
        print("  multi_release_exempt:", file=report)
        for label, count in sorted(stats["mrjar"].items()):
            majors = ",".join(str(item) for item in sorted(stats["mrjar_majors"][label]))
            print(f"    {count} classes majors={majors} {label}", file=report)
    else:
        print("  multi_release_exempt: none", file=report)
    for line in stats["module_info"]:
        print(f"  module_info_entry: {line}", file=report)
    for line in stats["over"][:100]:
        print(f"  OVER52: {line}", file=report)
    if len(stats["over"]) > 100:
        print(f"  OVER52: ... {len(stats['over']) - 100} more", file=report)
    for line in stats["bad"][:20]:
        print(f"  BADCLASS: {line}", file=report)
    for name, labels in sorted(duplicates.items()):
        print(f"  DUPLICATE {name}", file=report)
        for label in labels:
            print(f"    {label}", file=report)
    key_jars = defaultdict(set)
    for name, labels in stats["owners"].items():
        prefix = next(item for item in KEY_PREFIXES if name.startswith(item))
        key_jars[prefix].update(labels)
    for prefix in KEY_PREFIXES:
        jars = sorted(key_jars.get(prefix, ()))
        print(f"  key_archives {prefix}: {len(jars)}", file=report)
        for jar in jars:
            print(f"    {jar}", file=report)
    stats["duplicates"] = duplicates
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=None)
    parser.add_argument("--module", action="append", default=[])
    parser.add_argument("--scope", default="test", choices=("test", "runtime", "compile"))
    parser.add_argument("--report", type=Path, default=None)
    parser.add_argument("--local-repo", type=Path, default=None)
    parser.add_argument(
        "--maven-settings",
        type=Path,
        default=None,
        help="Maven settings file. Passed as a separate -s argument so spaces in the path are preserved.",
    )
    parser.add_argument(
        "--javassist-version",
        default=None,
        help="Pass -Djavassist.version so the audited classpath matches the tested one.",
    )
    parser.add_argument(
        "--maven-arg",
        action="append",
        default=[],
        help="Extra -D or option passed verbatim to the Maven resolution call.",
    )
    args = parser.parse_args()
    repo = (args.repo or Path(__file__).resolve().parent.parent).resolve()
    if not (repo / "pom.xml").is_file():
        print(f"repo pom not found: {repo}", file=sys.stderr)
        return 2
    known = module_names(repo)
    selected = args.module or known
    unknown = [name for name in selected if name not in known]
    if unknown:
        print("unknown module: " + ", ".join(unknown), file=sys.stderr)
        return 2
    artifacts = {name: artifact_id(repo / name) for name in known}
    packagings = {name: packaging(repo / name) for name in known}
    version = project_version(repo)

    if args.maven_settings is not None and not args.maven_settings.is_file():
        print(f"maven settings not found: {args.maven_settings}", file=sys.stderr)
        return 2
    extra_args = list(args.maven_arg)
    if args.javassist_version:
        extra_args.append(f"-Djavassist.version={args.javassist_version}")

    try:
        classpaths = resolve_classpath(
            repo,
            [name for name in selected if packagings[name] != "pom"] if args.module else [],
            args.scope,
            args.local_repo,
            extra_args,
            args.maven_settings,
        )
    except SystemExit as exc:
        code = exc.code if isinstance(exc.code, int) else 1
        print(f"AUDIT_FAIL classpath resolution exit={code}", file=sys.stderr)
        return code or 1

    report_lines = []

    class Report:
        def write(self, text):
            report_lines.append(text)
            return len(text)

        def flush(self):
            return None

    report = Report()
    print(f"repo: {repo}", file=report)
    print(f"version: {version}", file=report)
    print(f"scope: {args.scope}", file=report)
    if args.local_repo:
        print(f"local_repo: {args.local_repo}", file=report)
    if args.maven_settings:
        print(f"maven_settings: {args.maven_settings}", file=report)
    if args.javassist_version:
        print(f"javassist.version: {args.javassist_version}", file=report)
    print(
        "rule: JDK 8-visible class major <= 52; META-INF/versions and module-info are exempt; "
        "key classes must be unique",
        file=report,
    )
    failed = False
    total_visible = 0
    for module in sorted(classpaths):
        if packagings.get(module, "jar") == "pom":
            print(f"MODULE {module}: skipped (pom packaging)", file=report)
            continue
        try:
            stats = audit_module(repo, module, classpaths[module], artifacts, version, report)
        except SystemExit as exc:
            print(f"AUDIT_FAIL {module}: {exc}", file=report)
            failed = True
            continue
        total_visible += stats["visible"]
        if stats["visible"] == 0:
            print(f"AUDIT_FAIL {module}: scanned no JDK 8-visible classes", file=report)
            failed = True
        if stats["over"] or stats["bad"] or stats["duplicates"]:
            failed = True
    if total_visible == 0:
        failed = True
    print(f"visible_classes_total: {total_visible}", file=report)
    print("AUDIT_OK" if not failed else "AUDIT_FAIL", file=report)
    text = "".join(report_lines)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(text, encoding="utf-8")
    sys.stdout.write(text)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
