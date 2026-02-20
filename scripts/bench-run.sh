#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="target/benchmarks"
mkdir -p "$OUT_DIR"

BENCH_REGEX="${BENCH_REGEX:-org.vectrix.bench.*}"
FORKS="${FORKS:-2}"
WARMUP_ITERS="${WARMUP_ITERS:-5}"
MEASURE_ITERS="${MEASURE_ITERS:-8}"
THREADS="${THREADS:-1}"
TIME_UNIT="${TIME_UNIT:-ns}"
JMH_EXTRA="${JMH_EXTRA:-}"

STAMP="$(date +%Y%m%d-%H%M%S)"
CSV_FILE="$OUT_DIR/jmh-$STAMP.csv"
TXT_FILE="$OUT_DIR/jmh-$STAMP.txt"

java -jar target/benchmarks.jar "$BENCH_REGEX" \
  -f "$FORKS" \
  -wi "$WARMUP_ITERS" \
  -i "$MEASURE_ITERS" \
  -t "$THREADS" \
  -tu "$TIME_UNIT" \
  -rf csv \
  -rff "$CSV_FILE" \
  $JMH_EXTRA | tee "$TXT_FILE"

cp "$CSV_FILE" "$OUT_DIR/latest.csv"
cp "$TXT_FILE" "$OUT_DIR/latest.txt"

echo "Wrote: $CSV_FILE"
echo "Wrote: $TXT_FILE"
echo "Updated: $OUT_DIR/latest.csv"
echo "Updated: $OUT_DIR/latest.txt"
