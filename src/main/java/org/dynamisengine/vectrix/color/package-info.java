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
 * Color math for renderers, texture processing and material pipelines: the sRGB curve, luminance and the sRGB/XYZ
 * matrices ({@link org.dynamisengine.vectrix.color.ColorMathf}), conversions between RGB gamuts with white-point
 * adaptation ({@link org.dynamisengine.vectrix.color.ColorSpacesf}), HDR transfer functions, camera exposure,
 * tonemapping operators, perceptual spaces and color differences (OKLab, CIELAB, HSV, HSL, CIEDE2000), CIE observers
 * and spectra, blackbody color, and RGB-to-spectrum upsampling.
 * <p>
 * All RGB values are linear unless a method says otherwise; the sRGB curve and the transfer functions convert to
 * and from encoded signals. White is D65 except in the ACES gamuts.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless and safe to call from any thread; {@code SpectralUpsampling}
 * builds its per-gamut tables on first use under a lock. Allocation: the per-color methods allocate nothing, except
 * {@code ColorSpacesd.adaptationMatrix} (small temporary arrays, for arbitrary white points) and
 * {@code SpectralUpsampling.fit}; the gamut conversion matrices are computed once.
 * Determinism: transcendentals go through {@code AccurateMath}, so results match across platforms under
 * {@code -Dvectrix.strictMath=true}. Errors: values are not validated or clamped unless a method says so, and NaN
 * propagates; {@code ColorScience*.kelvinToLinearRgb} throws {@link java.lang.IllegalArgumentException} for a null
 * {@code dest} or a temperature that is not finite and positive, and {@code SpectralUpsampling} throws it for the
 * ACES gamuts. Precision and naming: classes ending in {@code f} are float and those ending in {@code d} are double,
 * with the same methods; some float methods compute internally in double. {@code CieObserver}, {@code Spectra} and
 * {@code SpectralUpsampling} are double only, and {@code ColorGamut} and {@code ColorSpacesd.Adaptation} are shared
 * by both precisions. Every {@code dest} may alias an input.
 */
package org.dynamisengine.vectrix.color;
