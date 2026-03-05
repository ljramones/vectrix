#!/usr/bin/env bash
set -euo pipefail
BENCH_PROFILE=quick RESULT_BASENAME=quick "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/bench-run.sh"
