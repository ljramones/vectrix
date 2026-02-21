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
JMH_FORMAT="${JMH_FORMAT:-csv}"

STAMP="$(date +%Y%m%d-%H%M%S)"
TXT_FILE="$OUT_DIR/jmh-$STAMP.txt"
RESULT_FILE="$OUT_DIR/jmh-$STAMP.$JMH_FORMAT"

java --add-modules jdk.incubator.vector -jar target/benchmarks.jar "$BENCH_REGEX" \
  -f "$FORKS" \
  -wi "$WARMUP_ITERS" \
  -i "$MEASURE_ITERS" \
  -t "$THREADS" \
  -tu "$TIME_UNIT" \
  -rf "$JMH_FORMAT" \
  -rff "$RESULT_FILE" \
  $JMH_EXTRA | tee "$TXT_FILE"

cp "$TXT_FILE" "$OUT_DIR/latest.txt"
cp "$RESULT_FILE" "$OUT_DIR/latest.$JMH_FORMAT"

echo "Wrote: $RESULT_FILE"
echo "Wrote: $TXT_FILE"
echo "Updated: $OUT_DIR/latest.$JMH_FORMAT"
echo "Updated: $OUT_DIR/latest.txt"
