# Release checklist

## 1. Before the release commit

- `main` is green in CI: the six Verify jobs (x86-64, arm64 and macOS, each in the default and experimental
  profiles) and the Examples job.
- `mvn clean verify` and `mvn clean verify -Pexperimental` pass locally.
- `CrossPlatformDeterminismTest` reports no differences from the golden. If results changed on purpose,
  re-record the golden (see [determinism.md](../determinism.md)) and say why in the CHANGELOG.
- New public API has `@since` tags. Removed or changed API was deprecated in an earlier minor release
  ([api-policy.md](../api-policy.md)).

## 2. The release commit

Set the version everywhere it appears:
- `pom.xml`;
- `examples/pom.xml`, in both `<version>` and `<vectrix.version>`, which `ExamplesVersionTest` checks;
- the dependency snippets in `README.md` and `docs/getting-started.md`.

Update the docs:
- In `CHANGELOG.md`, turn `[Unreleased]` into `[X.Y.Z] - YYYY-MM-DD`, with a short "New in this release"
  summary above the detailed sections, and add an empty `[Unreleased]` section.
- If the release changes performance, refresh `docs/benchmarks.md` (see
  [benchmarking-guide.md](benchmarking-guide.md)).

Commit `chore: release X.Y.Z`, then tag:

```bash
git tag -a vX.Y.Z -m "Vectrix vX.Y.Z"
```

## 3. Publishing

```bash
./deploy.sh                 # mvn -Prelease clean verify, then deploy (GPG signing runs only in this profile)
git push origin main vX.Y.Z
```

Then confirm the deployment in the Sonatype Central Portal, and check that the plain, `simd`, sources and
Javadoc jars appear on Maven Central.
