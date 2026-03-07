#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN="$ROOT_DIR/scripts/bench-run.sh"

# Gate the current winning skinning path under two palette locality modes.
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-skinning-lbs4 \
BENCH_REGEX='org.dynamisengine.vectrix.bench.SkinningKernelBenchmark.skinLbs4$' \
JMH_EXTRA='-p vertices=16384 -p paletteAccess=contiguous,random -p paletteSize=64' \
"$RUN"

# Track experimental vector path in the same workload slice.
BENCH_PROFILE=regression \
RESULT_BASENAME=regression-skinning-lbs4vector \
BENCH_REGEX='org.dynamisengine.vectrix.bench.SkinningKernelBenchmark.skinLbs4Vector$' \
JMH_EXTRA='-p vertices=16384 -p paletteAccess=contiguous,random -p paletteSize=64' \
"$RUN"

BENCH_PROFILE=regression \
RESULT_BASENAME=regression-skinning-equivalence \
BENCH_REGEX='org.dynamisengine.vectrix.bench.SkinningEquivalenceBenchmark.skinningEquivalent$' \
JMH_EXTRA='-p vertices=16384 -p paletteSize=512 -p writeMode=fullWrite -p path=legacyLbs,kernelMatrixTight,kernelLbs' \
"$RUN"
