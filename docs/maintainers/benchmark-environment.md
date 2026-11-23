# Benchmark Environment

This document records the benchmark environment used for published Vectrix benchmark results.

## Host Snapshot (2026-03-05)
- Host region: US/Canada (America/Toronto timezone)
- OS: macOS 26.3 (Darwin 25.3.0 arm64)
- CPU: Apple M4 Max
- JDK: Temurin OpenJDK 25.0.1+8 LTS
- JVM command baseline: `java --add-modules jdk.incubator.vector -jar target/benchmarks.jar`

## Runtime Discipline
- Use benchmark profiles from `scripts/bench-*.sh` (`quick`, `full`, `prof`, `regression`).
- Prefer `full` for decision-making and `quick` only for local sanity checks.
- Run with minimal background load.
- Keep power/thermal state stable before full/profiled runs.
- Compare results only across matching OS/JDK/hardware baselines.

## Required Report Fields
Include these in benchmark publications or decision notes:
- CPU model and OS build
- JDK build/version
- benchmark profile used (`quick/full/prof/regression`)
- forks/warmup/measurement settings
- benchmark regex scope
- result files under `benchmarks/results/YYYY-MM-DD/`

## Optional but Recommended
- Core count and RAM snapshot
- Power profile / turbo policy
- background services minimized notes
- command-line JVM flags used in profiled runs
