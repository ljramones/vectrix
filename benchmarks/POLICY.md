# Benchmark Policy

## Standard Build
```bash
mvn clean package -Pbench -DskipTests
```

## Standard Run
```bash
scripts/bench-run.sh
```

## Save Baseline
```bash
scripts/bench-save-baseline.sh target/benchmarks/latest.csv
```

## Compare Against Baseline
```bash
scripts/bench-compare.sh benchmarks/baselines/<baseline>.csv target/benchmarks/latest.csv
```

## Default Regression Thresholds
- `matrix4f_getByteBuffer`, `matrix4f_getFloatBuffer`: +5% max slowdown
- all other benchmarks: +3% max slowdown

## Baseline Naming
Use machine + JDK + date, for example:
- `m4pro-jdk25-2026-02-20.csv`

## Notes
- Keep fork count, warmup, and measurement settings consistent between baseline and candidate runs.
- Run on an idle machine for publishable numbers.
