#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <csv-file> [baseline-name.csv]" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SRC="$1"
if [[ ! -f "$SRC" ]]; then
  echo "Baseline source not found: $SRC" >&2
  exit 1
fi

if [[ $# -ge 2 ]]; then
  NAME="$2"
else
  NAME="$(hostname | tr '[:upper:]' '[:lower:]' | tr -cd '[:alnum:]-')-jdk$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)-$(date +%Y-%m-%d).csv"
fi

mkdir -p benchmarks/baselines
cp "$SRC" "benchmarks/baselines/$NAME"
echo "Saved baseline: benchmarks/baselines/$NAME"
