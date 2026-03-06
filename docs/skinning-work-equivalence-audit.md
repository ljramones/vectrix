# Skinning Work-Equivalence Audit

Date: 2026-03-06  
Scope: compare legacy `SkinningBenchmark.lbsLikeMatrixSkinning` and kernel `SkinningKernelBenchmark.skinLbs4`.

## Verdict
These benchmarks are not work-equivalent today. The observed speed gap is expected from workload and data-shape differences, not a direct apples-to-apples kernel regression signal.

## Major Differences
1. Influence count:
- Legacy path blends 2 influences (`w0=0.6`, `w1=0.4`).
- Kernel path blends 4 influences per vertex.

2. Joint transform source:
- Legacy path uses 2 prebuilt `Matrix4f` objects shared by all vertices.
- Kernel path gathers per-vertex joint indices from a palette (`TransformSoA`) with 4 random/structured references.

3. Memory indirection:
- Legacy path has near-constant transform fetch pattern.
- Kernel path reads indices/weights arrays and gathers 4 joints per vertex.

4. Work shape:
- Legacy path performs two `mulPosition` operations then blend.
- Kernel path performs 4 weighted rigid transform evaluations with per-influence quaternion rotation + translation accumulation.

5. Output/storage behavior:
- Legacy path uses `Vector3f[]` object outputs with preallocated temporaries.
- Kernel path uses flat float arrays (`outX/outY/outZ`) and packed/SoA kernels.

## Consequence
Treat legacy-vs-kernel numbers as directional only. Use `SkinningKernelBenchmark` variants for architecture and optimization decisions.

## Recommended Follow-Up Harness
Add a stripped comparison benchmark where both paths use:
1. same influence count,
2. same transform palette and fetch pattern,
3. same weight/index arrays,
4. same output layout.

This will isolate abstraction overhead from workload mismatch.
