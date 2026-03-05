#!/usr/bin/env bash
set -euo pipefail
BENCH_PROFILE=full RESULT_BASENAME=full "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/bench-run.sh"
