#!/usr/bin/env bash
set -euo pipefail
BENCH_PROFILE=regression RESULT_BASENAME=regression "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/bench-run.sh"
