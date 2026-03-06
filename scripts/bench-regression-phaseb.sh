#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$ROOT_DIR/scripts/bench-run.sh"

# Phase B gate A: locality-sensitive transform AABB comparison (packedAffine vs matrix4f)
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-phaseb-aabb \
BENCH_REGEX='org.vectrix.bench.TransformAabbBenchmark.transformAabbBatch$' \
JMH_EXTRA='-p count=16384 -p representation=matrix4f,packedAffine -p dataDistribution=clustered -p accessPattern=hot -p traversalMode=SEQUENTIAL,RANDOM -p chunkSize=128' \
"$RUN"

# Phase B gate B: upload-prep default path vs matrix baseline
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-phaseb-upload \
BENCH_REGEX='org.vectrix.bench.InstanceUploadBenchmark.(instanceUploadMatrix4f|instanceUploadPackedAffine)$' \
JMH_EXTRA='-p instances=16384 -p dataDistribution=clustered -p accessPattern=hot -p traversalMode=SEQUENTIAL,RANDOM -p chunkSize=128' \
"$RUN"
