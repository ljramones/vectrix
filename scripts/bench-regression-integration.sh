#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$ROOT_DIR/scripts/bench-run.sh"

# Composed-path gate: packed vs matrix under large batch and locality variants.
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-integration-slice \
BENCH_REGEX='org.dynamisengine.vectrix.bench.IntegrationPipelineBenchmark.(integrationPackedPipeline|integrationMatrixPipeline)$' \
JMH_EXTRA='-p count=16384 -p vertices=4096 -p traversalMode=SEQUENTIAL,RANDOM' \
"$RUN"
