#!/usr/bin/env bash
set -euo pipefail
BENCH_PROFILE=prof RESULT_BASENAME=profiled "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/bench-run.sh"
