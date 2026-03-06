#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$ROOT_DIR/scripts/bench-run.sh"

# Pass H gate A: miss-heavy/scattered ray-AABB winner.
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-passh-geometry-rayaab \
BENCH_REGEX='org.vectrix.bench.GeometryIntersectionBenchmark.rayAabBatch$' \
JMH_EXTRA='-p count=16384 -p precision=float -p distribution=missHeavy -p accessPattern=random -p verts=16' \
"$RUN"

# Pass H gate B: SSS LUT winner on comparable medium quality shape.
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-passh-sss-lut \
BENCH_REGEX='org.vectrix.bench.RenderingMathBenchmark.buildSssLut$' \
JMH_EXTRA='-p count=1024 -p precision=float -p mode=arrayBatch -p distribution=uniform -p accessPattern=sequential -p resolution=64 -p quality=medium' \
"$RUN"
