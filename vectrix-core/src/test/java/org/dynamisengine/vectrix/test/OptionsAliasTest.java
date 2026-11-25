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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;

/** Each inherited {@code joml.*} property also reads as {@code vectrix.*}, which wins when both are set. */
class OptionsAliasTest {

    private static int decimals(String vectrix, String joml) throws Exception {
        String sv = set("vectrix.format.decimals", vectrix);
        String sj = set("joml.format.decimals", joml);
        try (URLClassLoader cl = IsolatedClasses.loader()) {
            return cl.loadClass("org.dynamisengine.vectrix.core.Options").getField("numberFormatDecimals").getInt(null);
        } finally {
            set("vectrix.format.decimals", sv);
            set("joml.format.decimals", sj);
        }
    }

    private static String set(String key, String value) {
        return value == null ? System.clearProperty(key) : System.setProperty(key, value);
    }

    @Test
    void vectrixNameJomlNameAndPrecedence() throws Exception {
        assertEquals(3, decimals(null, null));
        assertEquals(5, decimals(null, "5"));
        assertEquals(6, decimals("6", null));
        assertEquals(7, decimals("7", "5"));
    }
}
