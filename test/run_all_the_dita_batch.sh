#!/usr/bin/env bash
# Convert all EPS under All-the-DITA with logging for path analysis.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SRC="${1:-/Users/android/Downloads/All-the-DITA}"
OUT="test/output/all-the-dita"
LOG_DIR="test/output/logs"
PROG="$LOG_DIR/all-the-dita_batch_progress.txt"
LOG_CFG="test/batch-convert-logging.properties"

mkdir -p "$OUT" "$LOG_DIR"
: > "$PROG"

mvn -q package -DskipTests
JAR="target/eps2svg-1.0-SNAPSHOT-jar-with-dependencies.jar"

ok=0
fail=0
while IFS= read -r eps; do
  rel="${eps#$SRC/}"
  svg="$OUT/${rel%.eps}.svg"
  mkdir -p "$(dirname "$svg")"
  if java -Djava.util.logging.config.file="$LOG_CFG" \
      -jar "$JAR" "$eps" "$svg" >> "$LOG_DIR/all-the-dita_batch_stdout.log" 2>&1; then
    echo "OK $rel" >> "$PROG"
    ok=$((ok + 1))
  else
    echo "FAIL $rel" >> "$PROG"
    fail=$((fail + 1))
  fi
done < <(find "$SRC" -name '*.eps' -type f | sort)

echo "DONE ok=$ok fail=$fail total=$((ok + fail))" | tee -a "$PROG"
