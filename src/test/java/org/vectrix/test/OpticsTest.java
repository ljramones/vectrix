/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;
import org.vectrix.fft.Complexd;
import org.vectrix.fft.Complexf;
import org.vectrix.optics.Fresneld;
import org.vectrix.optics.Fresnelf;
import org.vectrix.optics.Iord;
import org.vectrix.optics.Iorf;
import org.vectrix.optics.ThinFilmd;
import org.vectrix.optics.ThinFilmf;

class OpticsTest {
    @Test
    void schlickF0MatchesKnownGlassAirValue() {
        assertEquals(0.04f, Iorf.schlickF0(1.0f, 1.5f), 1E-6f);
        assertEquals(0.04, Iord.schlickF0(1.0, 1.5), 1E-12);
    }

    @Test
    void dielectricAtNormalIncidenceMatchesF0() {
        float f0f = Iorf.schlickF0(1.0f, 1.5f);
        double f0d = Iord.schlickF0(1.0, 1.5);
        assertEquals(f0f, Fresnelf.dielectric(1.0f, 1.0f, 1.5f), 1E-6f);
        assertEquals(f0d, Fresneld.dielectric(1.0, 1.0, 1.5), 1E-12);
    }

    @Test
    void conductorReflectanceIsBounded() {
        float rf = Fresnelf.conductor(0.5f, new Complexf(0.2f, 3.0f));
        double rd = Fresneld.conductor(0.5, new Complexd(0.2, 3.0));
        assertTrue(rf >= 0.0f && rf <= 1.0f);
        assertTrue(rd >= 0.0 && rd <= 1.0);
    }

    @Test
    void thinFilmChangesColorWithThickness() {
        Vector3f thin0 = ThinFilmf.reflectanceRgb(1.0f, 1.38f, 1.5f, 0.0f, 1.0f, new Vector3f());
        Vector3f thin300 = ThinFilmf.reflectanceRgb(1.0f, 1.38f, 1.5f, 300.0f, 1.0f, new Vector3f());
        assertNotEquals(thin0.x, thin300.x);
        assertNotEquals(thin0.y, thin300.y);
        assertNotEquals(thin0.z, thin300.z);
        assertNotEquals(thin300.x, thin300.y);
        assertNotEquals(thin300.y, thin300.z);
        assertTrue(thin300.x >= 0.0f && thin300.x <= 1.0f);
        assertTrue(thin300.y >= 0.0f && thin300.y <= 1.0f);
        assertTrue(thin300.z >= 0.0f && thin300.z <= 1.0f);
    }

    @Test
    void floatDoubleParity() {
        float ff = Fresnelf.dielectric(0.73f, 1.0f, 1.45f);
        double fd = Fresneld.dielectric(0.73, 1.0, 1.45);
        assertEquals((double) ff, fd, 1E-6);

        Vector3f vf = ThinFilmf.reflectanceRgb(1.0f, 1.4f, 1.5f, 250.0f, 0.6f, new Vector3f());
        Vector3d vd = ThinFilmd.reflectanceRgb(1.0, 1.4, 1.5, 250.0, 0.6, new Vector3d());
        assertEquals((double) vf.x, vd.x, 2E-5);
        assertEquals((double) vf.y, vd.y, 2E-5);
        assertEquals((double) vf.z, vd.z, 2E-5);
    }
}
