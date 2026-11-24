#!/bin/sh
# Compile the vectrix-gpu Slang kernels to SPIR-V (Vulkan) and Metal source, and record their ABI, with DynamisGPU's
# shader build (dynamis-gpu-shaders, ShaderBuild): the same rounding rules as every DynamisGPU kernel. Run it after
# editing src/main/slang or src/test/slang, and commit the regenerated resources:
#     sh vectrix-gpu/scripts/build-kernels.sh
# The build itself never needs slangc: it uses the committed outputs, and KernelManifestTest fails when a .slang file
# no longer matches the hash in the manifest. slangc is found through $SLANGC, $VULKAN_SDK or ~/VulkanSDK.
# The kernels are listed in src/main/slang/kernels.list and src/test/slang/kernels.list.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$root"
version=$(sed -n 's:.*<dynamisgpu.version>\(.*\)</dynamisgpu.version>.*:\1:p' pom.xml)
jar="${MAVEN_REPO:-$HOME/.m2/repository}/org/dynamisengine/dynamis-gpu-shaders/$version/dynamis-gpu-shaders-$version.jar"
if [ ! -f "$jar" ]; then
  echo "missing $jar: install DynamisGPU $version (see README.md)" >&2
  exit 1
fi
build() { java -cp "$jar" org.dynamisengine.gpu.shaders.ShaderBuild "$@"; }
main=src/main/slang test=src/test/slang
build --src "$main" --kernels "$main/kernels.list" --manifest kernels.properties \
  --out src/main/resources/org/dynamisengine/vectrix/compute/kernels
build --src "$test" --include "$main" --hash "$main" --hash "$test" --kernels "$test/kernels.list" \
  --manifest testkernels.properties --out src/test/resources/org/dynamisengine/vectrix/compute/testkernels
