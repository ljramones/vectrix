#!/usr/bin/env python3
"""Check and package the file Maven repository that scripts/package-release.sh staged, for a GitHub release."""
import hashlib
import shutil
import sys
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

version, repository_arg = sys.argv[1:]
repository = Path(repository_arg).resolve()
out = repository.parent
JARS = ["vectrix-core", "vectrix-render", "vectrix-signal", "vectrix-ml"]
ARTIFACTS = {"vectrix-parent": [], "vectrix": ["", "-sources", "-javadoc"]}
ARTIFACTS.update({jar: ["", "-simd", "-sources", "-javadoc"] for jar in JARS})
ARTIFACTS["vectrix-gpu"] = ["", "-sources"]  # since 4.0.1; depends on DynamisGPU's dynamis-gpu-api



def staged(artifact, suffix, ext):
    """The staged file; a SNAPSHOT (a rehearsal) is deployed under a timestamp, so take its latest."""
    folder = repository / "org/dynamisengine" / artifact / version
    if version.endswith("-SNAPSHOT"):
        base = version[:-len("-SNAPSHOT")]
        found = sorted(folder.glob(f"{artifact}-{base}-*{suffix}.{ext}"))
        found = [f for f in found if f.name.count("-") == f"{artifact}-{base}-0-0{suffix}.{ext}".count("-")]
        if found:
            return found[-1]
    return folder / f"{artifact}-{version}{suffix}.{ext}"


for artifact, suffixes in ARTIFACTS.items():
    pom = staged(artifact, "", "pom")
    for file in [pom] + [staged(artifact, s, "jar") for s in suffixes]:
        if not file.is_file():
            raise SystemExit(f"Missing release artifact: {file}")
    root = ET.parse(pom).getroot()
    own = root.find("{*}version")
    if own is None:
        own = root.find("{*}parent/{*}version")
    if own is None or own.text != version:
        raise SystemExit(f"Mismatched version in {pom}")
for internal in ("vectrix-tests", "vectrix-examples"):
    if (repository / "org/dynamisengine" / internal).exists():
        raise SystemExit(f"{internal} must not be published")
for jar in JARS:
    path = staged(jar, "", "jar")
    with zipfile.ZipFile(path) as z:
        if "module-info.class" not in z.namelist():
            raise SystemExit(f"{path.name} has no module descriptor; package in the default profile")

gpu_pom = ET.parse(staged("vectrix-gpu", "", "pom")).getroot()
dynamisgpu = gpu_pom.find("{*}properties/{*}dynamisgpu.version").text

readme = f"""Vectrix {version}

This Maven repository bundle is distributed through GitHub, not Maven Central. Requires JDK 25.

Install it into your local Maven repository (~/.m2/repository, or $MAVEN_REPOSITORY):

    sh install.sh

Then depend on org.dynamisengine:vectrix:{version} for the whole library, or on the jars you use:
vectrix-core, vectrix-render, vectrix-signal and vectrix-ml (each also with the simd classifier).
vectrix-gpu (the GPU twins of the kernels) is here too; it needs DynamisGPU {dynamisgpu}'s dynamis-gpu-api,
from the DynamisGPU GitHub release, and dynamis-gpu-metal or dynamis-gpu-vulkan at runtime.
See docs/migrating-to-4.0.md in tag v{version}.

Alternatively configure this directory's repository/ folder as a Maven file repository. The parent
org.dynamisengine:dynamis-parent:1.0.1 is on Maven Central.
"""
install = """#!/bin/sh
set -eu
bundle=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
target=${MAVEN_REPOSITORY:-$HOME/.m2/repository}
mkdir -p "$target"
cp -R "$bundle/repository/." "$target/"
echo "Installed Vectrix VERSION into $target"
""".replace("VERSION", version)

archive = out / f"vectrix-{version}-maven.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as bundle:
    prefix = f"vectrix-{version}/"
    bundle.writestr(prefix + "README.txt", readme)
    bundle.writestr(prefix + "install.sh", install)
    for file in sorted(repository.rglob("*")):
        if file.is_file():
            bundle.write(file, prefix + "repository/" + file.relative_to(repository).as_posix())

# The jars as individual downloads too; the ZIP has their POMs and every attachment.
assets = [archive]
for jar in JARS:
    for suffix in ("", "-simd"):
        source = staged(jar, suffix, "jar")
        destination = out / f"{jar}-{version}{suffix}.jar"
        shutil.copy2(source, destination)
        assets.append(destination)
for suffix in ("", "-sources"):
    source = staged("vectrix-gpu", suffix, "jar")
    destination = out / f"vectrix-gpu-{version}{suffix}.jar"
    shutil.copy2(source, destination)
    assets.append(destination)
(out / "SHA256SUMS").write_text("".join(
    f"{hashlib.sha256(f.read_bytes()).hexdigest()}  {f.name}\n" for f in assets))
print(f"Packaged {archive}")
print(f"Checksums: {out / 'SHA256SUMS'}")
