# Namespace Migration Plan

Old root: `org.vectrix`
New root: `org.dynamisengine.vectrix`

Goals:
- adopt `org.dynamisengine:dynamis-parent`
- move Java packages to `org.dynamisengine.vectrix`
- update module descriptors, benchmarks, regression tooling, and docs
- validate with tests + bench package + smoke runs
- no push until all validation passes

Affected areas:
- src/main/java
- src/test/java
- benchmarks
- module-info.java
- scripts
- docs

Notes:
- benchmark IDs will change because package names change
- historical benchmark artifacts remain valid but names differ
