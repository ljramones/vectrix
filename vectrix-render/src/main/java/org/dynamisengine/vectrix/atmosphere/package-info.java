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
 * Atmosphere and participating-media math (DynamisSky owns the rendering policy): phase functions with exact
 * sampling (Rayleigh, Henyey-Greenstein, Cornette-Shanks, Draine, and the Jendersie-d'Eon fog/cloud Mie
 * approximation), an Earth-like atmosphere model with ozone, numerically exact optical depth, and the transmittance
 * and multiple-scattering lookup tables of Hillaire 2020 with Bruneton's parameterization.
 * <p>
 * <b>Contract.</b> Threading: {@link org.dynamisengine.vectrix.atmosphere.AtmosphereModel} is an immutable record and
 * the other classes are stateless static functions, all safe to use from any thread. Allocation: the phase functions
 * and the density, coefficient and ray-geometry functions of {@link org.dynamisengine.vectrix.atmosphere.Atmosphere}
 * do not allocate; {@code Atmosphere.densityIntegrals}, {@code opticalDepth}, {@code transmittanceToTop} and
 * {@code multipleScattering} use a per-thread scratch area and do not allocate after the first call on a thread,
 * {@code multipleScatteringLut} takes caller scratch, {@code transmittanceLut} allocates two small arrays per call,
 * and the {@code AtmosphereModel} array accessors return copies. The integrals and tables are meant for
 * precomputation, not per-pixel use. Determinism: all transcendentals go
 * through {@link org.dynamisengine.vectrix.core.AccurateMath} and the integration and sampling schemes are fixed (no
 * random numbers), so results match across platforms under {@code -Dvectrix.strictMath=true}. Errors: the
 * {@code AtmosphereModel} constructor throws {@code IllegalArgumentException} for an invalid geometry, profile or
 * coefficient array (NaN and infinity included); the integrals and table functions throw it for a negative or NaN
 * length, a table size below 2, a non-positive count or a too-small array, checked before anything is written; the
 * density and ray-geometry functions do not validate their arguments, and out-of-range inputs give meaningless
 * results or NaN rather than exceptions. Precision and naming: {@code Atmosphere} and {@code AtmosphereModel} are double, and
 * their tables are stored as float; {@code PhaseFunctionsd} is double and {@code PhaseFunctionsf} returns float but
 * computes in double.
 */
package org.dynamisengine.vectrix.atmosphere;
