/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
/**
 * BRDF building blocks for physically based shading: microfacet specular terms (GGX, Smith, Schlick, sheen,
 * clearcoat), diffuse lobes, the split-sum DFG lookup table, and LightEngine-compatible variants of the current shader
 * formulas.
 * <p>
 * All functions take cosines (dot products of unit vectors), not vectors. The GGX functions in
 * {@link org.dynamisengine.vectrix.brdf.Microfacetf} take the width {@code alpha}, while the diffuse and
 * LightEngine-compatible functions take perceptual roughness. Excluding back-facing directions is the caller's job.
 * <p>
 * <b>Contract.</b> Threading: every class is a stateless set of static functions, safe to call from any thread.
 * Allocation: no function allocates, except {@link org.dynamisengine.vectrix.brdf.DfgLutBuilder#build}, which
 * allocates a two-element scratch array once per call. Determinism: transcendentals go through
 * {@link org.dynamisengine.vectrix.core.AccurateMath} (and square roots are exact), so results match across platforms
 * under {@code -Dvectrix.strictMath=true}; the DFG tables use a fixed Hammersley sequence, not random numbers.
 * Errors: the shading functions do not validate their inputs and do not throw; degenerate inputs give 0, infinity or
 * NaN as each method states. The {@code DfgLutBuilder} methods check their arguments up front and throw
 * {@code IllegalArgumentException} for a non-positive size or sample count, a null model, a cosine or roughness
 * outside its range, or a too-small output array. Precision and naming: {@code ...f} classes are float, {@code ...d} classes are double;
 * {@code DfgLutBuilder} integrates in double and stores float tables.
 */
package org.dynamisengine.vectrix.brdf;
