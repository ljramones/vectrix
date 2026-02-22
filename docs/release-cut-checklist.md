# Release Cut Checklist

This checklist is the canonical release process for Vectrix.

## 1. Preflight
- Ensure working tree is clean: `git status --short`
- Confirm target branch: `git branch --show-current`
- Confirm version in `pom.xml` is correct for release.

## 2. Validation
- Unit/integration checks: `mvn -q test`
- Full verification (style/tests/package): `mvn -q verify`
- Benchmark artifact build: `mvn -q clean package -Pbench -DskipTests`

## 3. Benchmark Baseline
- Run focused release baseline:
  - `BENCH_REGEX='org.vectrix.bench.(CurveBenchmark|FftBenchmark|OpticsBenchmark|ShBenchmark|QuaternionRotationBenchmark|ShHotPathBenchmark|LtcBenchmark|LowDiscrepancyBenchmark|SkinningKernelBenchmark).*' FORKS=1 WARMUP_ITERS=1 MEASURE_ITERS=1 THREADS=1 TIME_UNIT=ns ./scripts/bench-run.sh`
- Ensure artifacts exist in `target/benchmarks/` (`latest.csv`, `latest.txt`).
- Update `BENCHMARKS.md` with date, hardware/JVM, representative results, and normalized per-op tables.

## 4. Documentation
- Update `CHANGELOG.md` for the release date and highlights.
- Ensure `README.md` links are valid and examples compile logically.
- Ensure `docs/vectrix-capabilities.md` reflects current features.
- Ensure `NOTICE` and `LICENSE` are present and accurate.

## 5. API Stability Pass
- Confirm `@since` coverage for newly added public APIs.
- Confirm no breaking API changes are introduced unintentionally.
- If any break is intentional, document in `CHANGELOG.md`.

## 6. Commit and Tag
- Commit release changes:
  - `git add -A`
  - `git commit -m "Prepare release vX.Y.Z"`
- Tag release:
  - `git tag -a vX.Y.Z -m "Vectrix vX.Y.Z"`
- Push commit and tags:
  - `git push`
  - `git push --tags`

## 7. Post-Release
- Verify CI status is green on the tagged commit.
- Publish release notes using `CHANGELOG.md` content.
- Start next development cycle version update if needed.
