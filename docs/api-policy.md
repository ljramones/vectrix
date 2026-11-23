# API policy

## What is public API

All 33 exported packages are public API. They are listed in [package-map.md](package-map.md). A class or member
is supported unless it is marked with one of these:

- **`@Internal`**: an implementation detail. Today that means `core.Options` and `core.Runtime`. It may change or
  disappear in any release. Configure the library through the documented system properties
  ([conventions.md](conventions.md#configuration-properties)), not through these classes.
- **`@Deprecated(forRemoval = true)`**: still works, but will be removed in the next major version. Its Javadoc
  names the replacement.

Only the exported packages are visible to modular applications. A class-path application can see everything, but
the same rules apply to it.

## Versioning

Vectrix follows semantic versioning.

- **Minor releases (2.x)** add API and deprecate old API. They never remove or change public signatures, so code
  that compiles against 2.x compiles against any later 2.y. Results can change only to fix a defect, and the
  CHANGELOG says so.
- **Major releases** remove what the previous major line deprecated. Every removal was announced at least one
  minor release earlier.
- **Deprecated APIs** forward to their replacements, so the results are the same while both exist.

## Bit-exact results

Some results are guaranteed bit for bit:
- results under the deterministic configuration in [determinism.md](determinism.md);
- the `blas` and `nn` kernels and `ReproducibleMath`, in any configuration.

Releases keep these bits unless the CHANGELOG lists an intended change. Inside the project,
`CrossPlatformDeterminismTest` compares every public method's results with a recorded golden file on x86-64 and
arm64.

## Performance

A change to a hot-path API needs benchmark evidence. CI runs a smoke benchmark to catch large regressions. See
[performance.md](performance.md) and [maintainers/benchmarking-guide.md](maintainers/benchmarking-guide.md).
