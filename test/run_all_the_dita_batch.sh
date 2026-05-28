#!/usr/bin/env bash
# Convert all EPS under All-the-DITA with logging for path analysis.
#
# Usage:
#   run_all_the_dita_batch.sh [input-dir] [output-dir] [eps2svg options...]
#
# Defaults to --substitute-fonts=no when no font options are given.
# Example:
#   run_all_the_dita_batch.sh /path/to/eps test/output/all-the-dita \
#     --substitute-fonts yes --font-metrics relative
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SRC="${1:-/Users/android/Downloads/All-the-DITA}"
shift || true
OUT="${1:-test/output/all-the-dita}"
shift || true
CLI_OPTS=("$@")
if [[ ${#CLI_OPTS[@]} -eq 0 ]]; then
  CLI_OPTS=(--substitute-fonts=no)
fi

LOG_DIR="test/output/logs"
PROG="$LOG_DIR/all-the-dita_batch_progress.txt"
LOG_CFG="test/batch-convert-logging.properties"
DISPLAY_OPTS=(--min-width 100 --min-height 100 --max-width 8.5in --max-height 11in)
STDOUT_LOG="$LOG_DIR/all-the-dita_batch_stdout.log"
if [[ " ${CLI_OPTS[*]} " == *" --substitute-fonts=no"* ]] \
    || [[ " ${CLI_OPTS[*]} " == *" --substitute-fonts no "* ]]; then
  PROG="${PROG%.txt}_subfonts-no.txt"
  STDOUT_LOG="$LOG_DIR/all-the-dita_batch_subfonts-no_stdout.log"
fi

mkdir -p "$OUT" "$LOG_DIR"
: > "$PROG"
: > "$STDOUT_LOG"

mvn -q package -DskipTests
JAR="target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar"

set +e
java -Djava.util.logging.config.file="$LOG_CFG" \
  -jar "$JAR" --batch "${DISPLAY_OPTS[@]}" "${CLI_OPTS[@]}" "$SRC" "$OUT" \
  >> "$STDOUT_LOG" 2>&1
exit_code=$?
set -e
grep -E '^(OK|FAIL|DONE) ' "$STDOUT_LOG" > "$PROG"
exit $exit_code
