#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
MVN="${MVN:-mvn}"

run_publish() {
  local label="$1"
  shift

  echo "==> Publishing ${label}"
  "${MVN}" clean deploy \
    -DskipTests=true \
    -Prelease-sign-artifacts \
    "$@"
}

cd "${ROOT_DIR}"

run_publish "Scala 2.11.8 artifacts (*_2.11)" -Pscala-2.11
run_publish "Scala 2.13.16 artifacts (*_2.13)"
