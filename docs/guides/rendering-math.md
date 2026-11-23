# Rendering math: shading, lighting and color

This guide covers the math a renderer runs on the CPU, or bakes into tables and shaders: surface BRDFs and
Fresnel, image-based and spherical-harmonic lighting, area lights, sky and fog scattering, and color from spectra
to the display. It is for precomputation, tools, tests and CPU-side shading. Vectrix does not draw anything; it
gives you the numbers and table layouts that your shaders consume.

The packages are `brdf`, `optics`, `sh`, `spherical`, `ltc`, `atmosphere`, `color` and `renderingmath`. Most
classes come in a float (`f`) and a double (`d`) twin with the same methods, so this guide names the float class and
the double one works the same way. Directions are unit vectors and angles are radians unless a section says
otherwise; see [Conventions](../conventions.md) for the suffixes, `dest` parameters and FAST/STRICT modes.

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Linear ↔ sRGB, luminance, sRGB ↔ XYZ | `ColorMathf` | The library's single source for the sRGB/XYZ matrices |
| Convert between sRGB, Display P3, Rec.2020, ACEScg | `ColorSpacesf`, `ColorGamut` | Bradford or CAT16 white-point adaptation |
| HDR signal encoding (PQ, HLG, Rec.709, gamma 2.2) | `TransferFunctionsf` | sRGB curve is in `ColorMathf` |
| Camera exposure (EV100), photometric units | `Exposuref` | Lagarde/Filament conventions |
| Tonemapping | `Tonemapf` | ACES, AgX, Khronos PBR Neutral, Uchimura, Lottes, Reinhard |
| OKLab, CIELAB, HSV/HSL, color difference | `PerceptualColorf` | Hue in degrees |
| GGX specular, Smith visibility, Schlick Fresnel, sheen, clearcoat | `Microfacetf` | Reference model (Filament form) |
| Diffuse lobes | `Diffusef` | Lambert, Burley, Oren-Nayar |
| Match DynamisLightEngine's current shader BRDF | `LightEngineCompatf` | Reproduces the shader formulas exactly |
| Split-sum DFG table for image-based lighting | `DfgLutBuilder` | Also the cloth (sheen) channel |
| IOR to F0, exact Fresnel (dielectric, conductor) | `Iorf`, `Fresnelf` | Conductors take `core.Complexf` |
| Thin-film iridescence | `ThinFilmf` | Film thickness in nanometres |
| Wavelength sampling | `SpectralSamplingf` | 380 to 780 nm |
| Bake an environment into SH, diffuse irradiance | `ShProjection`, `ShConvolution`, `ShCoeffs9f` | Order 2 (9 coefficients) or 3 (16) |
| Rotate, window, multiply SH; cheap L1 irradiance | `ShRotation`, `ShWindowing`, `ShProduct`, `ShL1` | |
| Spherical Gaussian lobes | `SphericalGaussiansf` | Fit, product, irradiance |
| Octahedral and cubemap direction mappings | `DirectionMappingsf` | Exact cubemap texel solid angle |
| Area lights with linearly transformed cosines | `LtcTablef`, `LtcEvalf` | You supply the fitted LTC table |
| Sky: transmittance and multiple-scattering tables | `Atmosphere`, `AtmosphereModel` | Double precision, metres |
| Phase functions for sky, fog and clouds | `PhaseFunctionsf` | Exact inverse-CDF sampling |
| CIE observers, D65, spectra to XYZ | `CieObserver`, `color.Spectra` | Official 1 nm tables |
| RGB to a smooth reflectance spectrum | `SpectralUpsampling` | Jakob-Hanika 2019 |
| Blackbody color, Planck's law | `ColorSciencef` | |
| LUT interpolation, bent-normal cones, preintegrated skin | `renderingmath.Interpolationf`, `BentNormalConef`, `SssLutBuilder` | `renderingmath` |

## Color values: linear light and color spaces

Lighting math works on **linear** values: numbers proportional to light energy. Textures and the display use
**encoded** values, which spend more code values on dark tones. Decode a color texture with
`ColorMathf.srgbToLinear` before you light with it, and encode the final linear result with
`ColorMathf.linearToSrgb`. Both exist per channel (`float`) and per `Vector3f`.

`ColorMathf.luminanceLinear` gives relative luminance Y of linear sRGB. Its weights are the Y row of
`ColorMathf.linearSrgbToXyz`, so luminance and XYZ agree. `ColorMathf.xyzToLinearSrgb` is the inverse. Both use
D65 white.

To convert linear RGB between gamuts, call `ColorSpacesf.convert(rgb, from, to, dest)` with two `ColorGamut`
values: `ColorGamut.SRGB`, `ColorGamut.DISPLAY_P3`, `ColorGamut.REC2020`, `ColorGamut.ACES_CG` and
`ColorGamut.ACES_2065_1`. All gamuts are linear (no transfer function). The ACES gamuts have the ACES white point
(about D60), so the conversion adapts white with Bradford by default. Pass a `ColorSpacesd.Adaptation`
(`BRADFORD`, `CAT16` or `NONE`) to choose. For a shader, bake the matrix once with
`ColorSpacesf.conversionMatrix(from, to, adaptation, out9)`. It is 3×3 **row-major**: `out = M · rgb` with
`out.r = m[0] r + m[1] g + m[2] b`. Remember to transpose it if you upload it to a column-major GLSL `mat3`. The
float and double classes have the same methods, including `ColorSpacesf.toXyz`, `ColorSpacesf.fromXyz`,
`ColorSpacesf.toXyzMatrix`, `ColorSpacesf.fromXyzMatrix` and `ColorSpacesf.adaptationMatrix` (arbitrary white
points).

For display encoding beyond sRGB, `TransferFunctionsf` has:

| Function | Input | Output |
|---|---|---|
| `TransferFunctionsf.pqEncode` / `TransferFunctionsf.pqDecode` | luminance `y = nits / 10000` in [0, 1] | PQ signal in [0, 1] (SMPTE ST 2084) |
| `TransferFunctionsf.pqEncodeNits` | luminance in cd/m² | PQ signal |
| `TransferFunctionsf.hlgOetf` / `TransferFunctionsf.hlgInverseOetf` | normalized scene light in [0, 1] | HLG signal (ARIB STD-B67) |
| `TransferFunctionsf.rec709Oetf` / `TransferFunctionsf.rec709InverseOetf` | linear | Rec.709 camera signal |
| `TransferFunctionsf.gamma22Encode` / `TransferFunctionsf.gamma22Decode` | linear | pure power 1/2.2 |

`PerceptualColorf` converts to spaces where distances match perceived differences: OKLab
(`PerceptualColorf.oklabFromLinearSrgb`, `PerceptualColorf.linearSrgbFromOklab`, `PerceptualColorf.oklabFromXyz`),
CIELAB with D65 white (`PerceptualColorf.cielabFromXyz`, `PerceptualColorf.xyzFromCielab`), the cylindrical
forms (`PerceptualColorf.labToLch`, `PerceptualColorf.lchToLab`), and HSV/HSL (`PerceptualColorf.rgbToHsv`,
`PerceptualColorf.hsvToRgb`, `PerceptualColorf.rgbToHsl`, `PerceptualColorf.hslToRgb`). Hues are degrees in
[0, 360). HSV and HSL take RGB in [0, 1], usually gamma-encoded. For color differences use
`PerceptualColorf.deltaEOk` (Euclidean distance in OKLab) or `PerceptualColorf.deltaE2000` (CIEDE2000 on CIELAB).
Every `dest` may alias the input.

## Surface shading: BRDFs

A BRDF says how much of the light arriving from direction `l` leaves toward the viewer `v`. All functions in
`brdf` take cosines, not vectors: `nDotL`, `nDotV`, `nDotH` are dot products of unit vectors with the surface normal
`n`, `h` is the half vector `normalize(l + v)`, and `vDotH`, `lDotH` are dot products with `h`. Excluding
back-facing light (`nDotL <= 0`) is your job.

**Specular.** `Microfacetf` holds the reference microfacet model. A specular BRDF is `D · V · F`:

| Term | Method | Notes |
|---|---|---|
| Width `α` | `Microfacetf.alphaFromRoughness` | `α = perceptualRoughness²` |
| Distribution `D` | `Microfacetf.dGgx`, `Microfacetf.dGgxAnisotropic` | GGX / Trowbridge-Reitz, normalized; 0 for `nDotH <= 0` |
| Visibility `V` | `Microfacetf.vSmithGgxCorrelated`, `Microfacetf.vSmithGgxCorrelatedAnisotropic` | Height-correlated Smith, already divided by `4 nDotV nDotL` |
| Masking `G` | `Microfacetf.g1Smith`, `Microfacetf.g2SmithHeightCorrelated`, `Microfacetf.lambdaGgx` | When you need `G` itself |
| Fresnel `F` | `Microfacetf.fSchlick`, `Microfacetf.fSchlickRoughness` | Scalar or per RGB channel (`Vector3f`); `f90` is usually 1 |
| Sheen | `Microfacetf.dCharlie`, `Microfacetf.vNeubelt` | Cloth; `alpha` must be positive |
| Clearcoat | `Microfacetf.vKelemen` | Pair it with `dGgx` and `fSchlick` |
| Multiple scattering | `Microfacetf.energyCompensation` | Kulla-Conty factor from the DFG `(a, b)` |

Note that `Microfacetf` takes `alpha`, not perceptual roughness. Convert once with
`Microfacetf.alphaFromRoughness`. `Microfacetf.gSchlickGgxKaris` is the separable UE4 term with `k = α / 2`, kept
for tables built the traditional way; the Javadoc recommends `Microfacetf.g2SmithHeightCorrelated` for new work.

**Diffuse.** `Diffusef.lambert` returns `1/π`. `Diffusef.burley` (Disney) adds retro-reflection at grazing angles
and is not energy conserving. `Diffusef.orenNayar` models rough matte surfaces; its `sigma` is the facet-slope
standard deviation in radians, and `sigma = 0` gives Lambert. Each returns the BRDF value to multiply by the
albedo.

To shade with one light, add the diffuse and specular BRDFs and multiply by the light's radiance and `nDotL`:
`(albedo · Diffusef.lambert() + D · V · F) · radiance · nDotL`.

**Matching DynamisLightEngine.** `LightEngineCompatf` (and `LightEngineCompatd`) reproduces the BRDF that
DynamisLightEngine's OpenGL and Vulkan shaders use today, formula for formula: `LightEngineCompatf.distributionGgx`,
`LightEngineCompatf.geometrySchlickGgx`, `LightEngineCompatf.geometrySmith` and `LightEngineCompatf.fresnelSchlick`.
These follow the UE4 / LearnOpenGL form: π written as 3.14159, denominators clamped to 1e-4, and a separable
Schlick-GGX term with `k = (roughness + 1)² / 8` instead of height-correlated Smith. Their `roughness` argument is
perceptual roughness. Use them when CPU-side tools or tests must produce what the renderer draws, or to measure
what changes when the shaders move to `Microfacetf`. Do not use them for new shading code. In the same spirit,
`Tonemapf.lightEngineExponential` reproduces the engine's current tonemap.

## Image-based lighting: the DFG table

Split-sum image-based lighting (Karis 2013) stores, per `(nDotV, roughness)`, a scale `a` and bias `b` so that the
specular lobe integrated against a white environment is `f0 · a + b`. Build the table once at startup or offline:

- `DfgLutBuilder.build(size, sampleCount, model, outAb)` fills `outAb` (at least `2 · size · size` floats). Texel
  `(x, y)` is at index `2 · (y · size + x)`. It covers `nDotV = (x + 0.5) / size` and perceptual roughness
  `(y + 0.5) / size`. This is the `vec2(nDotV, roughness)` lookup DynamisLightEngine uses.
- `model` is a `DfgLutBuilder.Model`: `HEIGHT_CORRELATED_SMITH` (the reference, as in Filament) or
  `SCHLICK_GGX_KARIS` (UE4's table).
- `sampleCount` is the number of Hammersley samples per texel. The Javadoc gives 512 to 1024 as typical for 8-bit
  to 16-bit precision.
- `DfgLutBuilder.integrate` computes one texel's `(a, b)` in double precision.
- `DfgLutBuilder.buildCloth` and `DfgLutBuilder.integrateCloth` compute the sheen lobe's directional albedo, the
  third DFG channel in Filament. `buildCloth` writes one float per texel in the same layout.

`a + b` is the lobe's directional albedo. Pass `f0`, `a` and `b` to `Microfacetf.energyCompensation` to get the
factor that restores the energy lost by single-scattering GGX on rough surfaces. The table is a deterministic
estimate accumulated in double precision, so the same inputs give the same table on every platform.

For the roughness-dependent Fresnel of the ambient term, use `Microfacetf.fSchlickRoughness`.

## Fresnel, index of refraction and thin films

Fresnel reflectance is the fraction of light a surface reflects at a given angle. The `optics` package computes it
exactly from indices of refraction (IOR).

- `Iorf.schlickF0(etaI, etaT)` gives the reflectance at normal incidence, `((etaI − etaT) / (etaI + etaT))²`. Air
  to glass (1.0 to 1.5) gives 0.04. `Iorf.etaRatio` and `Iorf.schlickF0FromEtaRatio` work with the ratio
  `etaI / etaT`.
- `Fresnelf.dielectric(cosThetaI, etaI, etaT)` is the exact unpolarised reflectance of glass, water or plastic.
  A negative `cosThetaI` means the ray arrives from the `etaT` side, and the indices are swapped. Total internal
  reflection returns 1.
- `Fresnelf.conductor(cosThetaI, eta)` is the exact unpolarised reflectance of a metal with complex IOR
  `n + i k`, passed as a `core.Complexf` or as two floats `n, k`. The outside medium has IOR 1. For `k = 0` it equals
  the dielectric result. Metals have a different `n, k` per wavelength, so call it once per RGB channel.
- `ThinFilmf.reflectanceRgb(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, dest)` gives the reflectance of a thin
  coating (soap bubble, oil slick) on a substrate. It point-samples the three wavelengths of `SpectralRGBf` (650,
  550 and 450 nm), so it shows the color shifts but not a spectral integral. `ThinFilmf.reflectance` evaluates
  one wavelength in nanometres.
- `SpectralSamplingf` draws wavelengths in [`SpectralSamplingf.MIN_NM`, `SpectralSamplingf.MAX_NM`] (380 to
  780 nm): `SpectralSamplingf.sampleUniform`, `SpectralSamplingf.sampleStratified` and the matching
  `SpectralSamplingf.pdfUniform`.

IOR arguments must be positive; the methods throw `IllegalArgumentException` otherwise.

## Environment lighting with spherical harmonics

Spherical harmonics (SH) store a function on the sphere, such as the light arriving from every direction at a
probe, as a few coefficients. Order 2 uses 9 coefficients per color channel and order 3 uses 16. A diffuse
surface responds only to the low-frequency part of the lighting, so 9 coefficients reproduce diffuse irradiance
well.

**Storage.** `ShCoeffs9f` and `ShCoeffs16f` (and their `d` twins) hold RGB coefficients in one public array
`ShCoeffs9f.c`, laid out `[c0.r, c0.g, c0.b, c1.r, …]`. Coefficient `i` of basis function `Y(l, m)` is at
`i = l · (l + 1) + m`, and its channel `ch` is at `c[3 · i + ch]`. `ShCoeffs9f.COEFFS` is the count per channel
(9 or 16). A new object is all zeros; `ShCoeffs9f.zero` clears it. Both sizes have the same accessors:
`ShCoeffs16f.get(index, channel)` and `ShCoeffs16f.set(index, channel, value)` take the coefficient index `i` (not
the band `l`) and reject an index or channel out of range, and `ShCoeffs16f.set(other)` copies. Upload `c` as is:
27 floats per probe at order 2.

**Basis.** `ShBasis` defines the real SH basis used everywhere in the package: the standard graphics basis without
the Condon-Shortley phase, evaluated on `(x, y, z)` exactly as you pass it, with no axis remapping. Project and
evaluate in the same frame, and make your shader use the same basis order and constants (listed in the `ShBasis`
Javadoc). `ShBasis.evaluateL2` and `ShBasis.evaluateL3` write the 9 or 16 basis values for a direction.

**Projection.** Integrate the environment: for each sample direction, add `radiance · Y_i(direction) · weight`,
where `weight` is the solid angle the sample stands for. The weights over the whole sphere must sum to 4π. This
example integrates a sky over a latitude-longitude grid:

<!-- snippet: ShLighting#project -->
```java
// Integrate the environment over a latitude-longitude grid; each cell's weight is its solid angle.
ShCoeffs9f radiance = new ShCoeffs9f();
float[] scratch = new float[9];
float[] rgb = new float[3];
for (int i = 0; i < ROWS; i++) {
    double theta0 = Math.PI * i / ROWS;
    double theta1 = Math.PI * (i + 1) / ROWS;
    double theta = 0.5 * (theta0 + theta1);
    float solidAngle = (float) ((Math.cos(theta0) - Math.cos(theta1)) * 2 * Math.PI / COLS);
    for (int j = 0; j < COLS; j++) {
        double phi = 2 * Math.PI * (j + 0.5) / COLS;
        float x = (float) (Math.sin(theta) * Math.cos(phi));
        float y = (float) Math.cos(theta); // y is up
        float z = (float) (Math.sin(theta) * Math.sin(phi));
        sky(x, y, z, rgb);
        ShProjection.projectSample(x, y, z, rgb[0], rgb[1], rgb[2], solidAngle, scratch, radiance);
    }
}
```

`ShProjection.projectSample` adds into `dest`; it does not clear it. It takes a caller-owned 9-float scratch array
and allocates nothing. `ShProjection.accumulateSampleL2` does the same from `Vector3fc` arguments, and
`ShProjection.projectSampleL3` / `ShProjection.accumulateSampleL3` fill order-3 coefficients. To bake a cubemap,
weight each texel by `DirectionMappingsf.cubeTexelSolidAngle` and get its direction from
`DirectionMappingsf.cubeDirection`.

**Irradiance.** Convolving the radiance coefficients with the clamped cosine turns them into irradiance
coefficients, which you then evaluate along any normal:

<!-- snippet: ShLighting#irradiance -->
```java
// Convolve with the clamped cosine: the coefficients now give irradiance E(n) for any normal n.
// A Lambertian surface with albedo a reflects radiance a · E(n) / π.
ShCoeffs9f irradiance = ShConvolution.convolveLambertL2(radiance, new ShCoeffs9f());
float[] up = new float[3];
float[] side = new float[3];
float[] down = new float[3];
ShConvolution.evaluateL2(irradiance, 0, 1, 0, scratch, up);
ShConvolution.evaluateL2(irradiance, 1, 0, 0, scratch, side);
ShConvolution.evaluateL2(irradiance, 0, -1, 0, scratch, down);
```

`ShConvolution.convolveLambertL2` scales band 0 by π, band 1 by 2π/3 and band 2 by π/4; `dest` may be the input.
`ShConvolution.convolveLambertL3` also zeroes band 3, because the cosine has no band-3 content. Evaluate with
`ShConvolution.evaluateL2` (either with the normal as three floats, a scratch array and an RGB array out, or into a
`Vector3f`; neither allocates) or `ShConvolution.evaluateL3`. The `evaluate...` methods do not convolve: they
reconstruct whatever coefficients you pass. The result is irradiance E; divide by
π and multiply by the albedo for the reflected radiance of a Lambertian surface.

**Other operations.**

| Job | Method | Notes |
|---|---|---|
| Rotate a probe | `ShRotation.rotateL2`, `ShRotation.rotateL3` | Takes a `Matrix3fc` rotation. "Rotating by R" moves a feature at `u` to `R u`. Exact up to rounding |
| Rotate many probes by one rotation | `ShRotation.rotationMatrixL2` then `ShRotation.applyL2` | Matrix of `ShRotation.L2_MATRIX_SIZE` (34) or `ShRotation.L3_MATRIX_SIZE` (83) floats |
| Reduce ringing around bright lights | `ShWindowing.windowL2`, `ShWindowing.windowL3`, `ShWindowing.weight` | `ShWindowing.Window` is `HANN` (stronger) or `LANCZOS` (milder); start with `width = maxBand + 1` |
| Multiply two SH functions | `ShProduct.multiplyL2`, `ShProduct.multiplyL3` | Channel by channel; the result is projected back to the same order, so it is approximate |
| Multiply lighting by visibility | `ShProduct.multiplyScalarL2`, `ShProduct.multiplyScalarL3` | Visibility as a plain 9- or 16-element array |
| Gaunt (triple-product) coefficient | `ShProduct.gaunt` | Indices below 16 |
| Cheap irradiance from bands 0 and 1 | `ShL1.irradianceLinear`, `ShL1.irradianceNonLinear` | Read from radiance (not convolved) order-2 coefficients |

`ShL1.irradianceLinear` can go negative opposite a strong light. `ShL1.irradianceNonLinear` (Hazel 2017) keeps the
mean irradiance exactly and is never negative. Both take **radiance** coefficients and do the convolution
themselves. Rotation, windowing and products all accept `dest` equal to an input.

## Spherical Gaussians and direction mappings

A spherical Gaussian (SG) is a lobe `a · exp(λ (μ · v − 1))` with unit axis `μ`, sharpness `λ ≥ 0` and amplitude
`a`. SGs represent sharper lighting than order-2 SH with few parameters. `SphericalGaussiansf` writes lobes as
`(axis.xyz, sharpness)` into a `Vector4f` and returns the amplitude separately. Everything is linear in the
amplitude, so for RGB call once per channel or scale the result.

| Job | Method |
|---|---|
| Value in a direction | `SphericalGaussiansf.evaluate` |
| Integral over the sphere | `SphericalGaussiansf.integral` |
| Product of two SGs (an SG again) | `SphericalGaussiansf.product` |
| Integral of a product | `SphericalGaussiansf.innerProduct` |
| Diffuse irradiance at a normal | `SphericalGaussiansf.irradianceFitted` (Hill's fit, within 2 % for `λ ≥ 1`; exact for `λ < 1`, where the fit diverges), `SphericalGaussiansf.irradianceInnerProduct` (cheaper, up to about 15 % error) |
| Fit an SG to weighted sample directions | `SphericalGaussiansf.fit(dirs, dirsOffset, weights, weightsOffset, count, dest)`, `SphericalGaussiansf.fitSharpness` |
| Anisotropic SG | `SphericalGaussiansf.evaluateAnisotropic`, `SphericalGaussiansf.integralAnisotropic` |

`SphericalGaussiansf.COSINE_LOBE_SHARPNESS` and `SphericalGaussiansf.COSINE_LOBE_AMPLITUDE` give the SG that
approximates a clamped cosine lobe.

`DirectionMappingsf` maps directions to texture coordinates for probes and environment maps:

- `DirectionMappingsf.octahedralEncode` / `DirectionMappingsf.octahedralDecode`: the whole sphere onto
  [−1, 1]², with the upper hemisphere in the inner diamond. Map to [0, 1]² with `uv * 0.5 + 0.5`. The input
  direction need not be unit length. For bit-packed normals use `gpu.OctaNormal` instead.
- `DirectionMappingsf.hemiOctahedralEncode` / `DirectionMappingsf.hemiOctahedralDecode`: the upper hemisphere
  (`z ≥ 0`) onto the whole square. A direction with `z < 0` is folded: it encodes like its mirror image `(x, y, −z)`.
- `DirectionMappingsf.cubeFace` returns the face (0 to 5 for +X, −X, +Y, −Y, +Z, −Z) and writes face coordinates
  in [0, 1]² to a `Vector2f`; `DirectionMappingsf.cubeDirection` is the inverse. The convention is the OpenGL /
  Vulkan one: `u` right and `v` down on each face.
- `DirectionMappingsf.cubeTexelSolidAngle(size, i, j)` is the exact solid angle of a texel of a `size × size`
  face. Use it to weight samples when you filter a cubemap or project it into SH.

## Area lights with linearly transformed cosines

Linearly transformed cosines (LTC, Heitz et al. 2016) light a GGX surface with a polygonal area light. A fitted
table maps `(roughness, nDotV)` to a 3×3 matrix that turns the GGX lobe into a clamped cosine, whose integral over
a polygon has a closed form. Vectrix does not ship the fitted table; you load it (for example the published one)
and Vectrix samples it and integrates.

1. Sample the table with `LtcTablef.sampleMat3(table, width, height, roughness, ndotv, dest9)`. The table holds
   `width × height` texels of 9 floats, texel `(x, y)` at `9 · (y · width + x)`, with `x` along roughness and `y`
   along `nDotV`. Inputs are clamped to [0, 1] and map 0 to the first texel and 1 to the last (corner-aligned,
   not texel centers). `LtcTablef.sampleVec4` reads a 4-float table (such as the magnitude and Fresnel terms) the
   same way. Both are bilinear and throw on NaN inputs.
2. Express the light's vertices relative to the shading point in a frame where the normal is `+z`, and transform
   each direction with `LtcEvalf.transformDirection(m3, dir, dest)`. `m3` is row-major; pass the matrix your table
   stores for this purpose (usually the inverse LTC matrix). Normalise the results.
3. Integrate: `LtcEvalf.formFactorRectClipped` (four vertices) and `LtcEvalf.formFactorPolygonClipped` (an
   array and a vertex count of at least 3) clip the polygon at the horizon and return a value in [0, 1].
   `LtcEvalf.formFactorRect` skips the clipping and is correct only when the whole light is above the horizon.

The light is one-sided: its front face winds counter-clockwise about `+z`, and a back-facing polygon gives 0.
For round lights, `LtcEvalf.formFactorDisc(cosThetaCenter, angularRadius)` is exact for a sphere or disc that
subtends a spherical cap, including caps that cross the horizon. `LtcEvalf.formFactorTube` is a small-angle
approximation that is accurate only for small tubes above the horizon. `LtcEvalf.horizonClip` clamps a local `z`
to 0.

## Sky and participating media

The `atmosphere` package computes the tables of Hillaire's 2020 sky model with Bruneton's parameterisation. It is
double precision and meant for precomputation; DynamisSky decides how to render.

**The model.** `AtmosphereModel` is an immutable record: a spherical shell between `bottomRadius` and `topRadius`
(metres) with Rayleigh and Mie scattering (exponential density profiles), and ozone absorption (a tent profile).
Coefficients are RGB per metre. `AtmosphereModel.earth` returns Hillaire's Earth parameters (radii 6360 and
6460 km). The constructor validates its arguments and copies the arrays.

**Positions** are `(r, mu)`: `r` is the distance from the planet center in metres, and `mu` is the cosine between
the ray direction and the local zenith. A point at altitude `h` has `r = bottomRadius + h`.

| Job | Method |
|---|---|
| Densities and coefficients at altitude `h` | `Atmosphere.rayleighDensity`, `Atmosphere.mieDensity`, `Atmosphere.ozoneDensity`, `Atmosphere.extinction`, `Atmosphere.scattering` |
| Ray geometry | `Atmosphere.distanceToTop`, `Atmosphere.distanceToGround`, `Atmosphere.intersectsGround`, `Atmosphere.distanceToBoundary`, `Atmosphere.altitudeAt` |
| Optical depth along a ray | `Atmosphere.opticalDepth`, `Atmosphere.densityIntegrals` |
| Transmittance to the top of the atmosphere | `Atmosphere.transmittanceToTop` (0 if the ray hits the ground) |
| Transmittance table | `Atmosphere.transmittanceLut(model, width, height, outRgb)` |
| Table coordinates and their inverse | `Atmosphere.transmittanceLutUv`, `Atmosphere.transmittanceLutRMu` |
| Multiple scattering | `Atmosphere.multipleScattering`, `Atmosphere.multipleScatteringLut(model, width, height, directions, steps, scratch, scratchOffset, outRgb)`, `Atmosphere.multipleScatteringLutScratchSize` |

Both tables are `width × height` texels of interleaved RGB floats, texel `(x, y)` at `3 · (y · width + x)`,
evaluated at texel centers. In the transmittance table, look up a texel with `Atmosphere.transmittanceLutUv`; it
uses Bruneton's half-texel-exact mapping, so the shader must use the same formula. In the multiple-scattering
table, `x` runs over the sun's zenith cosine from −1 to 1 and `y` over altitude from the ground to the top. Its
values are the transfer `Ψ` for unit sun illuminance: multiply by the local scattering coefficient and the sun
illuminance to get radiance. The ray geometry avoids cancellation, so rays that graze the ground are as accurate
as vertical ones.

**Phase functions** say how much light scatters into each angle. `PhaseFunctionsf` writes each one in terms of
`u = cos θ`, the cosine between the light's direction of travel and the scattered direction (`u = 1` is forward).
For a camera ray with direction `d` (from the camera into the scene) and a unit vector `l` toward the light,
`u = d · l`. Each function is normalized to 1 over the sphere.

| Medium | Evaluate | Sample |
|---|---|---|
| Air molecules | `PhaseFunctionsf.rayleigh` | `PhaseFunctionsf.sampleRayleigh` |
| Aerosols, general | `PhaseFunctionsf.henyeyGreenstein` (mean cosine `g` in (−1, 1)) | `PhaseFunctionsf.sampleHenyeyGreenstein` |
| Aerosols, sky models | `PhaseFunctionsf.cornetteShanks` | `PhaseFunctionsf.sampleCornetteShanks` |
| Draine's family (HG with `alpha = 0`, Cornette-Shanks with 1) | `PhaseFunctionsf.draine`, `PhaseFunctionsf.draineMeanCosine` | `PhaseFunctionsf.sampleDraine` |
| Fog and cloud droplets | `PhaseFunctionsf.fog` with `PhaseFunctionsf.fogParameters(diameterMicrometres, out4)` | `PhaseFunctionsf.sampleFog` |

The samplers invert the cumulative distribution exactly, so they map a uniform number `xi` in [0, 1) to `u` and
work with stratified and low-discrepancy sequences. The azimuth is uniform and left to you. `fogParameters`
implements the Jendersie-d'Eon (2023) fit for droplet diameters `0 < d ≤ 50` µm; the published fits are not
continuous across their range boundaries. `AtmosphereModel.mieAnisotropy` is the `g` to pass to
`PhaseFunctionsf.cornetteShanks` for the sky's Mie term.

## Exposure and tonemapping

Physically based lighting produces scene luminance in cd/m² (nits). Exposure scales it to a normalized value, and a
tonemapping curve compresses that into [0, 1] for the display.

- `Exposuref.ev100(aperture, shutterSeconds, iso)` gives the exposure value at ISO 100 from camera settings.
  `Exposuref.ev100FromAverageLuminance(nits)` picks the EV100 that maps the average scene luminance to middle grey.
- `Exposuref.exposureFromEv100(ev100)` is the multiplier for scene luminance, `1 / (1.2 · 2^EV100)`.
- `Exposuref.photometricFromRadiometric` and `Exposuref.radiometricFromPhotometric` convert with
  `Exposuref.LUMINOUS_EFFICACY` (683 lm/W at 555 nm).

`Tonemapf` maps exposed linear sRGB to display-referred linear values in [0, 1]. Encode the result with
`ColorMathf.linearToSrgb` (or a `TransferFunctionsf` curve). Every `dest` may alias the input.

| Operator | Method | Character |
|---|---|---|
| ACES (Narkowicz fit) | `Tonemapf.acesNarkowicz` | Per channel; cheap |
| ACES (Hill fit) | `Tonemapf.acesHill` | Closer to ACES, including its desaturation of bright colors |
| AgX | `Tonemapf.agx` | Hue-stable at high intensity |
| Khronos PBR Neutral | `Tonemapf.khronosPbrNeutral` | Leaves base colors below about 0.8 almost untouched |
| Uchimura (Gran Turismo) | `Tonemapf.uchimura` | Default or explicit parameters; linear mid-tones |
| Lottes | `Tonemapf.lottes` | Published defaults |
| Reinhard | `ColorMathf.reinhardToneMap`, `Tonemapf.reinhardExtended` | Extended form takes a white point |
| DynamisLightEngine's current curve | `Tonemapf.lightEngineExponential` | `1 − exp(−x · exposure)`; for matching the engine only |

## Colorimetry and spectra

Use these when you start from measured spectra, or need color accurate to the CIE definitions.

- `CieObserver` holds the CIE standard observers from the official 1 nm tables: `CieObserver.CIE_1931_2DEG` and
  `CieObserver.CIE_1964_10DEG`. `CieObserver.xyzBar(nm, dest)` returns the color-matching functions, linearly
  interpolated between whole nanometres and zero outside 360 to 830 nm. `CieObserver.at` reads one tabulated
  value.
- `ColorSciencef.cie1931XyzBar` is a fast analytic fit (Wyman et al. 2013) of the 1931 functions. Its maximum
  error against the table is 0.014 (x̄), 0.008 (ȳ) and 0.022 (z̄); use `CieObserver` when that matters.
- `color.Spectra` (not the `dsp` class of the same name) integrates 1 nm spectra (arrays starting at
  `firstNm`) by the CIE summation method.
  `Spectra.tristimulus` gives raw XYZ sums of an emission spectrum. `Spectra.reflectanceToXyz` gives XYZ of a
  reflectance under D65, normalized so a perfect white has `Y = 1`. `Spectra.d65` is the D65 illuminant and
  `Spectra.whitePointD65` its white point.
- `SpectralUpsampling.fit(r, g, b, gamut, coeffsOut)` finds a smooth reflectance spectrum (Jakob and Hanika 2019)
  whose color under D65 is the given linear RGB, for a spectral renderer. It returns `false` when the color is
  outside what a reflectance between 0 and 1 can produce, and then gives the best coefficients found. Evaluate the
  spectrum with `SpectralUpsampling.evaluate(coeffs, nm)` and check it with `SpectralUpsampling.rgbOf`. Only
  gamuts with a D65 white work; the ACES gamuts throw. A fit costs a few tens of microseconds, so do it once per
  material.
- `ColorSciencef.kelvinToLinearRgb` gives the color of a blackbody in linear sRGB, normalized so the largest
  channel is 1, for 1667 to 25000 K. 6500 K is close to, but not the same as, D65 white.
  `ColorSciencef.planckRadiance` is Planck's law in W·sr⁻¹·m⁻²·nm⁻¹.

The CIE tables are in `CieData.java` and are licensed CC BY-SA 4.0, unlike the rest of the library (MIT); see the
[NOTICE](../../NOTICE) file.

## Other renderer helpers

The `renderingmath` package holds small kernels for lookup tables and visibility.

- **Interpolation.** In `renderingmath.Interpolationf` (not the `core` class of the same name),
  `Interpolationf.bilinear` and `Interpolationf.bilinearVec3` interpolate four corner values with weights `tx`,
  `ty`. The cubic forms take four samples and `t` in [0, 1] between the middle two:
  `Interpolationf.cubicHermite` (Catmull-Rom), `Interpolationf.cubicBSpline` and
  `Interpolationf.cubicMitchellNetravali` (with the filter's `b` and `c`). The bicubic forms
  (`Interpolationf.bicubicHermite`, `Interpolationf.bicubicBSpline`, `Interpolationf.bicubicMitchellNetravali`)
  take a 4×4 neighborhood as 16 floats, row by row.
- **Bent-normal cones.** `BentNormalConef.coneAngleFromAo` turns ambient occlusion in [0, 1] into the half-angle of
  a visibility cone with the same solid angle (`2π · AO`). `BentNormalConef.coneSolidAngle` is the cone's solid
  angle, and `BentNormalConef.estimateIntersectionSolidAngle` approximates the overlap of two cones with a shared
  apex, for example the visibility cone and a light's cone.
- **Preintegrated subsurface scattering** (Penner 2011). Describe the skin with `SssProfile`, a sum of Gaussians
  with RGB weights and variances (`SssProfile.singleGaussian` for one lobe; `SssProfile.evaluate` takes a radius
  in millimetres). `SssLutBuilder.build(width, height, profile, maxCurvature, maxArcLength, sampleCount, outRgb)`
  writes an interleaved RGB table with `nDotL` in [0, 1] along `x` and curvature from 0 to `maxCurvature` along
  `y`, at texel centers. A flat surface gives `nDotL`.

## Accuracy, allocation and threading

- **Precision.** Most classes have float and double twins. The `sh` classes without a suffix take float or
  double coefficient sets as overloads. `Atmosphere`, `color.Spectra`, `SpectralUpsampling` and `CieObserver` work in
  double, and `SssLutBuilder` in float. `DfgLutBuilder` integrates in double and writes float tables, and the
  `PhaseFunctionsf` samplers compute in double.
- **Stated accuracy.** `Atmosphere.opticalDepth` integrates to a relative accuracy of about 1e-14.
  `ShRotation` is exact up to rounding. `SpectralUpsampling.fit` solves to about 1e-12. The Fresnel functions are
  the exact equations, not Schlick's approximation. `ShProduct` results are projected back to the input order,
  so they are approximate by design. Order-2 SH capture diffuse irradiance only approximately (the `ShLighting`
  example's Javadoc puts it within a few percent).
- **Reproducibility.** With `-Dvectrix.strictMath=true` the transcendental functions use `StrictMath`, and these
  packages give the same bits on every platform; see [Conventions](../conventions.md#determinism) and
  [Determinism](../determinism.md). The `ShProduct` coefficients are identical on every platform in any mode,
  because they are computed with arithmetic only.
- **Allocation.** Per-pixel and per-probe functions allocate nothing: the BRDF and Fresnel terms, SH projection,
  evaluation, windowing, products and `ShL1`, the phase-function samplers, the direction mappings and the
  `ColorMathf`/`ColorSpacesf`/`ColorSpacesd` conversions. `ShRotation` allocates per-thread scratch on its first
  call in each thread. The SH and LTC table functions take caller-owned scratch and output arrays. The table
  builders `DfgLutBuilder`, `SssLutBuilder` and `Atmosphere.transmittanceLut` allocate small temporaries;
  `Atmosphere.multipleScatteringLut` builds its internal sun-transmittance table in caller scratch
  (`Atmosphere.multipleScatteringLutScratchSize` doubles), and the atmosphere integrators use per-thread scratch, so
  they allocate nothing after the first call on a thread. Run the table builders at load time or offline.
  The Javadoc puts a 32 × 32 `Atmosphere.multipleScatteringLut` at about a second.
- **Threading.** All functions are static and keep no shared mutable state (`ShRotation` and `ShProduct` use
  per-thread scratch), so they are safe from any number of threads. A `ShCoeffs9f` is a mutable object: do not
  write it from two threads at once.

## See also

- [Conventions](../conventions.md): type suffixes, `dest` and aliasing, FAST/STRICT, threading, errors.
- [Package map](../package-map.md): every package and where its guide is.
- [Determinism](../determinism.md): reproducible results across platforms.
- Example: [`ShLighting`](../../examples/src/main/java/org/dynamisengine/vectrix/examples/ShLighting.java), with
  the [examples README](../../examples/README.md) for how to run it.
- Source packages: [`brdf`](../../src/main/java/org/dynamisengine/vectrix/brdf/),
  [`optics`](../../src/main/java/org/dynamisengine/vectrix/optics/),
  [`sh`](../../src/main/java/org/dynamisengine/vectrix/sh/),
  [`spherical`](../../src/main/java/org/dynamisengine/vectrix/spherical/),
  [`ltc`](../../src/main/java/org/dynamisengine/vectrix/ltc/),
  [`atmosphere`](../../src/main/java/org/dynamisengine/vectrix/atmosphere/),
  [`color`](../../src/main/java/org/dynamisengine/vectrix/color/),
  [`renderingmath`](../../src/main/java/org/dynamisengine/vectrix/renderingmath/).
