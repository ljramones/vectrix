#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BENCH_PROFILE="${BENCH_PROFILE:-full}"
BENCH_REGEX="${BENCH_REGEX:-org.vectrix.bench.*}"
THREADS="${THREADS:-1}"
TIME_UNIT="${TIME_UNIT:-ns}"
JMH_FORMAT="${JMH_FORMAT:-json}"
JMH_EXTRA="${JMH_EXTRA:-}"
DATE_DIR="${DATE_DIR:-$(date +%Y-%m-%d)}"
RESULT_BASENAME="${RESULT_BASENAME:-$BENCH_PROFILE}"

case "$BENCH_PROFILE" in
  quick)
    FORKS="${FORKS:-1}"
    WARMUP_ITERS="${WARMUP_ITERS:-3}"
    MEASURE_ITERS="${MEASURE_ITERS:-5}"
    ;;
  full)
    FORKS="${FORKS:-3}"
    WARMUP_ITERS="${WARMUP_ITERS:-6}"
    MEASURE_ITERS="${MEASURE_ITERS:-10}"
    ;;
  prof)
    FORKS="${FORKS:-3}"
    WARMUP_ITERS="${WARMUP_ITERS:-6}"
    MEASURE_ITERS="${MEASURE_ITERS:-10}"
    JMH_EXTRA="-prof gc -prof stack ${JMH_EXTRA}"
    ;;
  regression)
    FORKS="${FORKS:-2}"
    WARMUP_ITERS="${WARMUP_ITERS:-4}"
    MEASURE_ITERS="${MEASURE_ITERS:-6}"
    ;;
  *)
    echo "Unknown BENCH_PROFILE '$BENCH_PROFILE' (supported: quick, full, prof, regression)" >&2
    exit 2
    ;;
esac

OUT_DIR="benchmarks/results/$DATE_DIR"
LEGACY_OUT_DIR="target/benchmarks"
mkdir -p "$OUT_DIR" "$LEGACY_OUT_DIR"

TXT_FILE="$OUT_DIR/${RESULT_BASENAME}.txt"
RESULT_FILE="$OUT_DIR/${RESULT_BASENAME}.${JMH_FORMAT}"

java --add-modules jdk.incubator.vector -jar target/benchmarks.jar "$BENCH_REGEX" \
  -f "$FORKS" \
  -wi "$WARMUP_ITERS" \
  -i "$MEASURE_ITERS" \
  -t "$THREADS" \
  -tu "$TIME_UNIT" \
  -rf "$JMH_FORMAT" \
  -rff "$RESULT_FILE" \
  $JMH_EXTRA | tee "$TXT_FILE"

cp "$TXT_FILE" "$LEGACY_OUT_DIR/latest.txt"
cp "$RESULT_FILE" "$LEGACY_OUT_DIR/latest.$JMH_FORMAT"

echo "Wrote: $RESULT_FILE"
echo "Wrote: $TXT_FILE"
echo "Updated: $LEGACY_OUT_DIR/latest.$JMH_FORMAT"
echo "Updated: $LEGACY_OUT_DIR/latest.txt"
