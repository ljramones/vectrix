#!/usr/bin/env bash
# Runs the benchmark groups behind the published snapshot in docs/benchmarks.md ("quick" profile: one fork, 3 x 1 s warmup,
# 5 x 1 s measurement). Usage: scripts/bench-snapshot.sh <output-dir>   (after: mvn clean package -Pbench -DskipTests)
set -euo pipefail
OUT=${1:?usage: bench-snapshot.sh <output-dir>}
mkdir -p "$OUT"
Q=(-f 1 -wi 3 -w 1s -i 5 -r 1s -tu ns -bm avgt)
run() {
  name=$1; shift
  java -jar target/benchmarks.jar "$@" "${Q[@]}" -rf csv -rff "$OUT/$name.csv" > "$OUT/$name.txt" 2>&1
  echo "$name done $(date +%H:%M:%S)"
}
run memory-backends 'MemoryBackendBenchmark' -p matrices=1,16,256
run transform-compose 'TransformComposeBenchmark' -p size=64,1024,16384
run transform-compose-scalar 'TransformComposeBenchmark.composeSoABatch' -p size=64,1024,16384 -jvmArgsAppend -Dvectrix.simd.enabled=false
run frustum-culling 'FrustumCullingBenchmark' -p count=16384
run skinning 'SkinningKernelBenchmark' -p vertices=4096 -p paletteAccess=contiguous -p paletteSize=64
run transform-aabb 'TransformAabbBenchmark' -p count=16384 -p representation=matrix4f,affine4x3,packedAffine -p dataDistribution=uniform -p accessPattern=hot -p traversalMode=SEQUENTIAL -p chunkSize=256
run instance-upload 'InstanceUploadBenchmark' -p instances=16384 -p dataDistribution=uniform -p accessPattern=hot -p traversalMode=SEQUENTIAL -p chunkSize=256
run packed-affine-conversion 'PackedAffineConversionBenchmark' -p size=4096
run rendering-math '(CurveBenchmark|ShBenchmark|OpticsBenchmark|QuaternionRotationBenchmark|ShHotPathBenchmark|LtcBenchmark|LowDiscrepancyBenchmark)' -p count=4096
run fft 'FftBenchmark' -p complexCount=1024
echo ALL DONE
