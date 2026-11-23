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

### Without Maven Central

Maven Central limits releases to 7 a month per account. When the quota is used up, release without it, and deploy
to Central later:

```bash
mvn clean install                 # into the local repository, for the components that depend on Vectrix
git push origin main vX.Y.Z
gh release create vX.Y.Z --title "Vectrix vX.Y.Z" --notes-file <the CHANGELOG section> \
    target/vectrix-X.Y.Z.jar target/vectrix-X.Y.Z-simd.jar target/vectrix-X.Y.Z-sources.jar \
    target/vectrix-X.Y.Z-javadoc.jar
```

Central accepts versions in any order, so a version released this way can be published later from its tag:
`git checkout vX.Y.Z && ./deploy.sh`.

### To Maven Central

```bash
./deploy.sh                 # mvn -Prelease clean verify, then deploy (GPG signing runs only in this profile)
git push origin main vX.Y.Z
```

Then confirm the deployment in the Sonatype Central Portal, and check that the plain, `simd`, sources and
Javadoc jars appear on Maven Central.
