# Release checklist

## 1. Before the release commit

- `main` is green in CI: the six Verify jobs (x86-64, arm64 and macOS, each in the default and experimental
  profiles), the Examples job and the GPU kernels job.
- On a Mac, `mvn -f vectrix-gpu/pom.xml verify -Pmetal,vulkan` passes: CI has no GPU, so this is the only run of
  the kernels on hardware ([vectrix-gpu/README.md](../../vectrix-gpu/README.md#testing)).
- `mvn clean verify` and `mvn clean verify -Pexperimental` pass locally.
- `CrossPlatformDeterminismTest` reports no differences from the golden. If results changed on purpose,
  re-record the golden (see [determinism.md](../determinism.md)) and say why in the CHANGELOG.
- New public API has `@since` tags. Removed or changed API was deprecated in an earlier minor release
  ([api-policy.md](../api-policy.md)).

## 2. The release commit

Set the version everywhere it appears:
- `pom.xml` and the `<parent>` version of every module's POM (`vectrix-core`, `vectrix-render`, `vectrix-signal`,
  `vectrix-ml`, `vectrix` and `vectrix-tests`); a module with another version fails the build;
- `examples/pom.xml` and `vectrix-gpu/pom.xml`, each in both `<version>` and `<vectrix.version>`, which
  `ExamplesVersionTest` checks;
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
scripts/package-release.sh        # verify the library and vectrix-gpu, stage both in target/release, zip them
                                  # (deploy includes install: the local repository gets the release too)
git push origin main vX.Y.Z
gh release create vX.Y.Z --title "Vectrix vX.Y.Z" --notes-file <the CHANGELOG section> \
    target/release/vectrix-X.Y.Z-maven.zip target/release/SHA256SUMS target/release/*.jar
```

The ZIP is a Maven repository with every module's POM (the parent's included) and jars, and an `install.sh` that
copies it into the local repository: since 4.0 a jar alone is not enough, because each jar's POM names its parent and
dependencies. `package-release.py` checks that every artifact is there, has the release version and, for the four
jars, a module descriptor, and that `vectrix-tests` and the examples are not published. Since 4.0.1 the ZIP also holds
vectrix-gpu (built against DynamisGPU from the local repository, which must hold the version its POM names).

vectrix-gpu is never deployed to Central: it depends on DynamisGPU, which is distributed only through its GitHub
Releases.

Central accepts versions in any order, so a version released this way can be published later from its tag:
`git checkout vX.Y.Z && ./deploy.sh`.

### To Maven Central

```bash
./deploy.sh                 # mvn -Prelease clean verify, then deploy (GPG signing runs only in this profile)
git push origin main vX.Y.Z
```

Then confirm the deployment in the Sonatype Central Portal, and check that `vectrix-parent`, `vectrix` and, for
each of `vectrix-core`, `vectrix-render`, `vectrix-signal` and `vectrix-ml`, the plain, `simd`, sources and Javadoc
jars appear on Maven Central.
