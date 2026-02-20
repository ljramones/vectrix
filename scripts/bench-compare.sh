#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <baseline.csv> <current.csv> [core_threshold_pct] [copy_threshold_pct]" >&2
  exit 1
fi

BASE="$1"
CURR="$2"
CORE_THRESHOLD="${3:-3}"
COPY_THRESHOLD="${4:-5}"

if [[ ! -f "$BASE" ]]; then
  echo "Missing baseline CSV: $BASE" >&2
  exit 1
fi
if [[ ! -f "$CURR" ]]; then
  echo "Missing current CSV: $CURR" >&2
  exit 1
fi

TMP_BASE="$(mktemp)"
TMP_CURR="$(mktemp)"
trap 'rm -f "$TMP_BASE" "$TMP_CURR"' EXIT

awk -F, '
NR>1 {
  key=$1;
  for (i=8; i<=NF; i++) key=key "|" $i;
  print key "," $5;
}' "$BASE" | sort > "$TMP_BASE"
awk -F, '
NR>1 {
  key=$1;
  for (i=8; i<=NF; i++) key=key "|" $i;
  print key "," $5;
}' "$CURR" | sort > "$TMP_CURR"

echo "Benchmark,Base,Current,Delta%,Threshold%,Status"

FAIL=0
join -t, -1 1 -2 1 "$TMP_BASE" "$TMP_CURR" | while IFS=, read -r key base curr; do
  name="${key%%|*}"
  if [[ -z "$base" || -z "$curr" ]]; then
    continue
  fi

  delta=$(awk -v b="$base" -v c="$curr" 'BEGIN{printf "%.2f", ((c-b)/b)*100}')

  if [[ "$name" == *"matrix4f_getByteBuffer"* || "$name" == *"matrix4f_getFloatBuffer"* ]]; then
    threshold="$COPY_THRESHOLD"
  else
    threshold="$CORE_THRESHOLD"
  fi

  status="PASS"
  cmp=$(awk -v d="$delta" -v t="$threshold" 'BEGIN{if (d>t) print 1; else print 0}')
  if [[ "$cmp" -eq 1 ]]; then
    status="FAIL"
    FAIL=1
  fi

  printf "%s,%s,%s,%s,%s,%s\n" "$key" "$base" "$curr" "$delta" "$threshold" "$status"
done

# Recompute fail deterministically outside subshell
FAIL_COUNT=$(join -t, -1 1 -2 1 "$TMP_BASE" "$TMP_CURR" | awk -F, -v ct="$CORE_THRESHOLD" -v cpt="$COPY_THRESHOLD" '
{
  key=$1; base=$2; curr=$3;
  split(key, parts, "|");
  name=parts[1];
  delta=((curr-base)/base)*100.0;
  threshold=ct;
  if (index(name,"matrix4f_getByteBuffer")>0 || index(name,"matrix4f_getFloatBuffer")>0) threshold=cpt;
  if (delta>threshold) fail++;
}
END{print fail+0}
')

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  echo "Regression check failed: $FAIL_COUNT benchmark(s) exceeded threshold." >&2
  exit 2
fi

echo "Regression check passed."
