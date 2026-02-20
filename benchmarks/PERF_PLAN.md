# Performance Execution Plan

## Goal
Ship rendering math features aggressively while preventing hidden regressions with repeatable JMH gates.

## Phase 1: Benchmark Governance (Immediate)
1. Build benchmark artifact with `mvn clean package -Pbench -DskipTests`.
2. Run stable JMH profile using `scripts/bench-run.sh`.
3. Store benchmark artifacts and CSV outputs under `target/benchmarks/`.
4. Save approved baselines under `benchmarks/baselines/` using `scripts/bench-save-baseline.sh`.

## Phase 2: Automated Regression Checks (Immediate)
1. Compare current run against a baseline via `scripts/bench-compare.sh`.
2. Regression thresholds:
   - copy paths (`matrix4f_getByteBuffer`, `matrix4f_getFloatBuffer`): fail if slower by more than 5%
   - SIMD SoA paths (`transformBatchSoAScalar`, `transformBatchSoAVector`): fail if slower by more than 4%
   - core math paths: fail if slower by more than 3%
   - new benchmarks: add explicit category thresholds when they become release gates

## Phase 3: New Benchmark Coverage (Now in repo)
1. `BatchMatrixBenchmark`: batched matrix/vector workloads with size parameters.
2. `SkinningBenchmark`: palette/LBS-style vs quaternion-based skinning proxy workloads.
3. `MemoryBackendBenchmark`: direct transfer behavior across backend modes and payload sizes.
4. `InteropBenchmark`: AoS vs SoA staging workloads for GPU upload-like preparation.

## Phase 4: Feature Delivery with Gates
1. Add one performance feature at a time.
2. Run benchmark suite.
3. Compare against baseline.
4. Reject or fix changes that fail thresholds.
5. Refresh baseline only after explicit acceptance.

## Operational Rule
No performance-sensitive PR merges without an attached benchmark comparison output.
