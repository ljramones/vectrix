# Hot Path Policy

Date: 2026-03-05

## Objective
Keep runtime-critical Vectrix paths data-oriented, allocation-free, and benchmark-driven.

## Rules
1. Prefer batch APIs over per-object APIs in hot loops.
2. Prefer contiguous SoA/packed data over scattered object access.
3. Use TRS/SoA for compose/update; materialize late.
4. Prefer packed affine for bulk transform consumers (bounds/update/upload).
5. Treat full `Matrix4f` as boundary format, not default hot-path format.
6. Avoid hidden allocations, boxing, and temporary object churn in kernel code.
7. Require benchmark evidence for hot-path API changes.

## Discouraged Patterns
- Repeated object-level transforms in inner loops.
- Eager full-matrix conversion in frame-critical stages.
- Convert-then-convert-again pipelines in one frame phase.

## Required Performance Hygiene
- Hot kernels should target `0 B/op` in benchmark profiles.
- Throughput comparisons should use normalized metrics (`ns/item`, `items/sec`).
- Primitive micro-op benches are regression guards, not active optimization goals.

## Package Guidance
- Preferred hot-path packages: `org.vectrix.affine`, `org.vectrix.soa`, `org.vectrix.geometry`, `org.vectrix.gpu`.
- `org.vectrix.core` matrix/quaternion APIs remain foundational and boundary-safe, but hot kernels should avoid object-heavy usage patterns where batch kernels exist.

## Enforcement
- New hot-path code should include/extend JMH coverage.
- API docs should explicitly mark boundary-oriented helpers versus hot-path-preferred methods.
