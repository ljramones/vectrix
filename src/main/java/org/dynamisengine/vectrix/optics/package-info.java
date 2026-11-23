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
 * Optics and material math including IOR/Fresnel models, thin-film interference, and spectral sampling.
 * Refractive indices are relative and dimensionless; wavelengths and film thicknesses are in nanometers.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static functions, safe to call from any thread.
 * Allocation: no method allocates, except the deprecated {@code Fresnel*.conductor} overload that takes an
 * {@code fft} complex number. Determinism: the Fresnel equations use only arithmetic and square roots, and the
 * thin-film phase uses {@code AccurateMath.cos}, so results match across platforms under
 * {@code -Dvectrix.strictMath}. Errors: a non-positive refractive index or wavelength, a negative extinction
 * coefficient or film thickness, and an invalid stratum throw {@code IllegalArgumentException}; cosines and random
 * numbers are clamped to {@code [0, 1]}, and NaN inputs are not rejected. Precision and naming: {@code ...d}
 * classes are double, {@code ...f} twins are float with the same methods.
 */
package org.dynamisengine.vectrix.optics;
