#!/usr/bin/env bash
set -euo pipefail

echo "==> Building vectrix"
mvn clean install
echo "==> Build complete"
