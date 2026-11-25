/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
 * Stochastic and low-discrepancy sampling for Monte Carlo integration, TAA jitter and procedural point sets:
 * <ul>
 * <li>low-discrepancy sequences ({@link org.dynamisengine.vectrix.sampling.SobolSequence},
 * {@link org.dynamisengine.vectrix.sampling.HaltonSequence},
 * {@link org.dynamisengine.vectrix.sampling.R2Sequence});</li>
 * <li>warps from the unit square to disks, hemispheres, spheres and cones with their densities, and the
 * multiple-importance-sampling heuristics ({@link org.dynamisengine.vectrix.sampling.Warpf},
 * {@link org.dynamisengine.vectrix.sampling.Warpd});</li>
 * <li>GGX visible-normal sampling ({@link org.dynamisengine.vectrix.sampling.GgxSamplingf},
 * {@link org.dynamisengine.vectrix.sampling.GgxSamplingd}) and solid-angle sampling of triangle and rectangle lights
 * ({@link org.dynamisengine.vectrix.sampling.SphericalSamplingf},
 * {@link org.dynamisengine.vectrix.sampling.SphericalSamplingd});</li>
 * <li>discrete distributions ({@link org.dynamisengine.vectrix.sampling.AliasTable});</li>
 * <li>seeded point-set generators inherited from JOML (Poisson disk, best candidate, stratified, spiral, uniform)
 * that report points through {@link org.dynamisengine.vectrix.sampling.Callback2d} or
 * {@link org.dynamisengine.vectrix.sampling.Callback3d}, and Gaussian kernels
 * ({@link org.dynamisengine.vectrix.sampling.Convolution}).</li>
 * </ul>
 * Directions are in a local frame with the normal on {@code +z}; uniform variates are in {@code [0, 1)}.
 * <p>
 * <b>Contract.</b> Threading: the sequence, warp and GGX/spherical sampling classes are stateless and safe to call
 * from any thread; an {@code AliasTable} may be sampled from many threads once built, but {@code build} must not run
 * concurrently with it; the seeded generator objects hold a random number generator and belong to one thread.
 * Allocation: the static functions and {@code AliasTable.build}/{@code sample} do not allocate; the
 * {@code AliasTable} constructor and the JOML-derived generators (Poisson disk, best candidate) allocate their
 * working structures. Determinism: the Sobol, Halton and R2 values are exact integer or IEEE arithmetic and give the
 * same bits everywhere; the warps and light sampling use {@code AccurateMath}, so they match across platforms under
 * {@code -Dvectrix.strictMath}; the seeded generators give the same points for the same seed and arguments.
 * Errors: the sequences and {@code AliasTable} throw {@code IllegalArgumentException} for a negative index, an
 * unsupported dimension or base, bad batch offsets (checked before any element is written) or invalid weights; the
 * warps and GGX/spherical sampling do not validate, and degenerate inputs give NaN or infinite results. Precision
 * and naming: {@code ...f} classes are float and {@code ...d} classes are double; the sequences offer both through
 * float methods and {@code ...Double} methods or {@code Vector2f}/{@code Vector2d} overloads.
 */
package org.dynamisengine.vectrix.sampling;
