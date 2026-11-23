/*
 * The MIT License
 *
 * Copyright (c) 2017-2024 JOML
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
package org.dynamisengine.vectrix.core;

import java.text.NumberFormat;

/**
 * Internal runtime helpers: raw bit conversion, FMA dispatch flag and number formatting.
 * 
 * @author Kai Burjack
 */
@Internal("Implementation helpers; may change in any release.")
public final class Runtime {

    /**
     * Whether {@link Math#fma(float, float, float)} delegates to {@link java.lang.Math#fma(float, float, float)}.
     * Always available on the supported JDKs (25+); controlled only by the {@code joml.useMathFma} option.
     */
    public static final boolean HAS_Math_fma = Options.USE_MATH_FMA;

    private Runtime() {
    }

    public static int floatToIntBits(float flt) {
        return Float.floatToRawIntBits(flt);
    }

    public static long doubleToLongBits(double dbl) {
        return Double.doubleToRawLongBits(dbl);
    }

    public static String formatNumbers(String str) {
        StringBuilder res = new StringBuilder(str.length() + 16);
        int eIndex = Integer.MIN_VALUE;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == 'E') {
                eIndex = i;
            } else if (Character.isDigit(c) && eIndex == i - 1) {
                res.append('+');
            }
            res.append(c);
        }
        return res.toString();
    }

    public static String format(double number, NumberFormat format) {
        if (Double.isNaN(number)) {
            return padLeft(format, " NaN");
        } else if (Double.isInfinite(number)) {
            return padLeft(format, number > 0.0 ? " +Inf" : " -Inf");
        }
        return format.format(number);
    }

    private static String padLeft(NumberFormat format, String str) {
        int len = format.format(0.0).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len - str.length() + 1; i++) {
            sb.append(" ");
        }
        return sb.append(str).toString();
    }

    public static boolean equals(float a, float b, float delta) {
        return Float.floatToIntBits(a) == Float.floatToIntBits(b) || Math.abs(a - b) <= delta;
    }

    public static boolean equals(double a, double b, double delta) {
        return Double.doubleToLongBits(a) == Double.doubleToLongBits(b) || Math.abs(a - b) <= delta;
    }

}
