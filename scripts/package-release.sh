#!/bin/sh
# Build and test Vectrix, then stage its artifacts as a downloadable Maven repository for a GitHub release
# (target/release). The Central release profile (GPG signing) is not used.
set -eu
repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repo"
version=$(python3 -c 'import xml.etree.ElementTree as E; print(E.parse("pom.xml").getroot().find("{*}version").text)')
repository="$repo/target/release/repository"
rm -rf "$repo/target/release"
mvn clean deploy -DdeployAtEnd=true "-DaltDeploymentRepository=github-release::file://$repository" "$@"
python3 scripts/package-release.py "$version" "$repository"
