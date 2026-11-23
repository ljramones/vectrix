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
package org.dynamisengine.vectrix.color;

import org.dynamisengine.vectrix.core.Vector3d;

/**
 * CIE standard colorimetric observers from the CIE's official 1 nm tables (360 to 830 nm), exact at whole
 * nanometres and linearly interpolated between them; zero outside the tabulated range. For a fast analytic
 * approximation of the 1931 functions see {@link ColorScienced#cie1931XyzBar}.
 *
 * @since 2.3.0
 */
public enum CieObserver {
    /** CIE 1931 2-degree standard observer. */
    CIE_1931_2DEG(CieData.CMF_1931),
    /** CIE 1964 10-degree standard observer. */
    CIE_1964_10DEG(CieData.CMF_1964);

    /** First tabulated wavelength (nm). */
    public static final int MIN_NM = 360;
    /** Last tabulated wavelength (nm). */
    public static final int MAX_NM = 830;

    private final double[] table;

    CieObserver(double[] table) {
        this.table = table;
    }

    /**
     * Returns the color-matching functions {@code (x̄, ȳ, z̄)} at a wavelength: exact at whole nanometres and linearly
     * interpolated between them.
     *
     * @param nm the wavelength in nanometres; outside [{@link #MIN_NM}, {@link #MAX_NM}], or NaN, gives zero
     * @param dest receives (x̄, ȳ, z̄)
     * @return {@code dest}
     */
    public Vector3d xyzBar(double nm, Vector3d dest) {
        if (!(nm >= MIN_NM && nm <= MAX_NM)) {
            return dest.set(0.0, 0.0, 0.0);
        }
        double f = nm - MIN_NM;
        int i = (int) f;
        if (i == MAX_NM - MIN_NM) {
            return dest.set(table[3 * i], table[3 * i + 1], table[3 * i + 2]);
        }
        double t = f - i;
        int a = 3 * i, b = a + 3;
        return dest.set(table[a] + t * (table[b] - table[a]), table[a + 1] + t * (table[b + 1] - table[a + 1]),
                table[a + 2] + t * (table[b + 2] - table[a + 2]));
    }

    /**
     * Returns one tabulated value of the color-matching functions.
     *
     * @param nm the wavelength in whole nanometres; outside [{@link #MIN_NM}, {@link #MAX_NM}] gives 0
     * @param c the component: 0 for x̄, 1 for ȳ, 2 for z̄; not checked, so other values read a neighboring
     *          wavelength's entry or throw {@code ArrayIndexOutOfBoundsException}
     * @return the tabulated value
     */
    public double at(int nm, int c) {
        return nm < MIN_NM || nm > MAX_NM ? 0.0 : table[3 * (nm - MIN_NM) + c];
    }
}
