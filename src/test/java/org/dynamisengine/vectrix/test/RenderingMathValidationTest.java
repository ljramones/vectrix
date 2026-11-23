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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.vectrix.atmosphere.Atmosphere;
import org.dynamisengine.vectrix.atmosphere.AtmosphereModel;
import org.dynamisengine.vectrix.brdf.DfgLutBuilder;
import org.dynamisengine.vectrix.color.CieObserver;
import org.dynamisengine.vectrix.color.ColorGamut;
import org.dynamisengine.vectrix.color.ColorSpacesd;
import org.dynamisengine.vectrix.color.ColorSpacesf;
import org.dynamisengine.vectrix.color.TransferFunctionsd;
import org.dynamisengine.vectrix.color.TransferFunctionsf;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.core.Vector4f;
import org.dynamisengine.vectrix.ltc.LtcEvald;
import org.dynamisengine.vectrix.ltc.LtcEvalf;
import org.dynamisengine.vectrix.optics.Fresneld;
import org.dynamisengine.vectrix.optics.Fresnelf;
import org.dynamisengine.vectrix.optics.Iord;
import org.dynamisengine.vectrix.optics.Iorf;
import org.dynamisengine.vectrix.optics.ThinFilmd;
import org.dynamisengine.vectrix.optics.ThinFilmf;
import org.dynamisengine.vectrix.renderingmath.Interpolationd;
import org.dynamisengine.vectrix.renderingmath.Interpolationf;
import org.dynamisengine.vectrix.sampling.AliasTable;
import org.dynamisengine.vectrix.sh.ShCoeffs16d;
import org.dynamisengine.vectrix.sh.ShCoeffs16f;
import org.dynamisengine.vectrix.sh.ShCoeffs9d;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShConvolution;
import org.dynamisengine.vectrix.sh.ShProduct;
import org.dynamisengine.vectrix.sh.ShWindowing;
import org.dynamisengine.vectrix.spherical.DirectionMappingsd;
import org.dynamisengine.vectrix.spherical.DirectionMappingsf;
import org.dynamisengine.vectrix.spherical.SphericalGaussiansd;
import org.dynamisengine.vectrix.spherical.SphericalGaussiansf;
import org.junit.jupiter.api.Test;

/**
 * The 3.0 fixes in the rendering-math packages (sh, brdf, atmosphere, sampling, optics, spherical, color, ltc,
 * renderingmath): arguments checked up front, NaN and zero cases defined, and the twin APIs completed.
 */
class RenderingMathValidationTest {

    private static final AtmosphereModel EARTH = AtmosphereModel.earth();

    // ---------------------------------------------------------------- sh

    @Test
    void shCoefficientAccessorsCheckIndexAndChannel() {
        ShCoeffs9f c9 = new ShCoeffs9f();
        c9.set(8, 2, 3f);
        assertEquals(3f, c9.c[8 * 3 + 2]);
        assertEquals(3f, c9.get(8, 2));
        // index 0, channel 5 used to read coefficient 1's blue channel silently
        assertThrows(IllegalArgumentException.class, () -> c9.get(0, 5));
        assertThrows(IllegalArgumentException.class, () -> c9.get(9, 0));
        assertThrows(IllegalArgumentException.class, () -> c9.set(-1, 0, 1f));
        ShCoeffs9d c9d = new ShCoeffs9d();
        assertThrows(IllegalArgumentException.class, () -> c9d.set(0, 3, 1.0));

        ShCoeffs16f a = new ShCoeffs16f().set(15, 1, 2f).set(4, 0, -1f);
        assertEquals(2f, a.c[15 * 3 + 1]);
        ShCoeffs16f b = new ShCoeffs16f().set(a);
        assertArrayEquals(a.c, b.c);
        assertEquals(-1f, b.get(4, 0));
        assertEquals(0f, b.zero().get(15, 1));
        assertThrows(IllegalArgumentException.class, () -> a.get(16, 0));
        ShCoeffs16d ad = new ShCoeffs16d().set(15, 2, 5.0);
        ShCoeffs16d bd = new ShCoeffs16d().set(ad);
        assertEquals(5.0, bd.get(15, 2));
        assertEquals(0.0, bd.zero().c[15 * 3 + 2]);
        assertThrows(IllegalArgumentException.class, () -> ad.set(0, -1, 1.0));
    }

    @Test
    void shProductChecksLengthsAndIndices() {
        ShCoeffs9f a = new ShCoeffs9f().set(0, 0, 1f);
        ShCoeffs9f dest = new ShCoeffs9f().set(3, 1, 7f);
        assertThrows(IllegalArgumentException.class, () -> ShProduct.multiplyScalarL2(a, new float[8], dest));
        assertEquals(7f, dest.get(3, 1), "dest unchanged");
        assertThrows(IllegalArgumentException.class,
                () -> ShProduct.multiplyScalarL3(new ShCoeffs16d(), new double[15], new ShCoeffs16d()));
        assertThrows(IllegalArgumentException.class, () -> ShProduct.gaunt(16, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ShProduct.gaunt(0, -1, 0));
        assertEquals(1.0 / (2.0 * Math.sqrt(Math.PI)), ShProduct.gaunt(0, 0, 0), 1e-15);
    }

    @Test
    void shWindowingRejectsNullWindowAndNegativeWidth() {
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.weight(null, 1, 3.0));
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.weight(null, 0, 3.0));
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.weight(ShWindowing.Window.HANN, 1, -3.0));
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.weight(ShWindowing.Window.HANN, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.weight(ShWindowing.Window.HANN, -1, 3.0));
        assertEquals(0.0, ShWindowing.weight(ShWindowing.Window.LANCZOS, 1, 0.0));
        assertEquals(1.0, ShWindowing.weight(ShWindowing.Window.LANCZOS, 0, 0.0));
        ShCoeffs9f in = new ShCoeffs9f().set(4, 0, 1f);
        ShCoeffs9f dest = new ShCoeffs9f().set(4, 0, 9f);
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.windowL2(in, null, 3f, dest));
        assertThrows(IllegalArgumentException.class, () -> ShWindowing.windowL2(in, ShWindowing.Window.HANN, -1f, dest));
        assertEquals(9f, dest.get(4, 0), "dest unchanged");
    }

    @Test
    void shArrayEvaluationMatchesVectorEvaluation() {
        ShCoeffs9f c = new ShCoeffs9f();
        for (int i = 0; i < ShCoeffs9f.SIZE; i++) {
            c.c[i] = (i * 7 % 11) / 10f - 0.5f;
        }
        Vector3f dir = new Vector3f(0.36f, 0.48f, 0.8f);
        Vector3f v = ShConvolution.evaluateL2(c, dir, new Vector3f());
        float[] rgb = new float[3];
        ShConvolution.evaluateL2(c, dir.x, dir.y, dir.z, new float[9], rgb);
        assertEquals(v.x, rgb[0], 1e-6f);
        assertEquals(v.y, rgb[1], 1e-6f);
        assertEquals(v.z, rgb[2], 1e-6f);
    }

    // ---------------------------------------------------------------- brdf

    @Test
    void dfgIntegrationValidatesUpFront() {
        double[] ab = {-1, -1};
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.integrate(0.5, 0.5, 16, null, ab));
        assertThrows(IllegalArgumentException.class,
                () -> DfgLutBuilder.integrate(0.5, 0.5, 0, DfgLutBuilder.Model.HEIGHT_CORRELATED_SMITH, ab));
        assertThrows(IllegalArgumentException.class,
                () -> DfgLutBuilder.integrate(0.0, 0.5, 16, DfgLutBuilder.Model.SCHLICK_GGX_KARIS, ab));
        assertThrows(IllegalArgumentException.class,
                () -> DfgLutBuilder.integrate(Double.NaN, 0.5, 16, DfgLutBuilder.Model.SCHLICK_GGX_KARIS, ab));
        assertThrows(IllegalArgumentException.class,
                () -> DfgLutBuilder.integrate(0.5, -0.1, 16, DfgLutBuilder.Model.SCHLICK_GGX_KARIS, ab));
        assertThrows(IllegalArgumentException.class,
                () -> DfgLutBuilder.integrate(0.5, 0.5, 16, DfgLutBuilder.Model.SCHLICK_GGX_KARIS, new double[1]));
        assertArrayEquals(new double[] {-1, -1}, ab, "outAb unchanged");
        // roughness 0 is the mirror limit: a = 1 - Fc, b = Fc with Fc = (1 - nDotV)^5
        for (DfgLutBuilder.Model m : DfgLutBuilder.Model.values()) {
            DfgLutBuilder.integrate(0.4, 0.0, 64, m, ab);
            double fc = Math.pow(0.6, 5);
            assertEquals(1 - fc, ab[0], 1e-12, m.name());
            assertEquals(fc, ab[1], 1e-12, m.name());
        }
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.integrateCloth(0.5, 0.0, 16));
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.integrateCloth(0.5, 0.5, 0));
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.integrateCloth(0.0, 0.5, 16));
        float[] out = {5f, 5f, 5f};
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.buildCloth(2, 16, out));
        assertArrayEquals(new float[] {5f, 5f, 5f}, out, "out unchanged");
        assertThrows(IllegalArgumentException.class, () -> DfgLutBuilder.buildCloth(0, 16, out));
    }

    // ---------------------------------------------------------------- atmosphere

    @Test
    void atmosphereRejectsNegativeLengthAndSmallTables() {
        double r = EARTH.bottomRadius() + 1000.0;
        double[] acc = {1, 2, 3};
        // a negative length used to integrate the segment behind the start point
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.densityIntegrals(EARTH, r, 0.5, -10.0, acc));
        assertThrows(IllegalArgumentException.class,
                () -> Atmosphere.densityIntegrals(EARTH, r, 0.5, Double.NaN, acc));
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.opticalDepth(EARTH, r, 0.5, -1.0, acc));
        assertArrayEquals(new double[] {1, 2, 3}, acc, "acc unchanged");
        Atmosphere.densityIntegrals(EARTH, r, 0.5, 0.0, acc);
        assertArrayEquals(new double[] {1, 2, 3}, acc, "a zero length adds nothing");

        double[] uv = new double[2];
        // a width or height of 1 used to divide by zero
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.transmittanceLutUv(EARTH, r, 0.5, 1, 64, uv));
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.transmittanceLutUv(EARTH, r, 0.5, 64, 1, uv));
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.transmittanceLutRMu(EARTH, 0.5, 0.5, 1, 1, uv));
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.transmittanceLut(EARTH, 1, 8, new float[24]));
        float[] small = {4f};
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.transmittanceLut(EARTH, 2, 2, small));
        assertEquals(4f, small[0]);
        assertThrows(IllegalArgumentException.class, () -> Atmosphere.multipleScattering(EARTH, r, 0.5, 0, 4,
                new double[3]));
        double[] scratch = new double[Atmosphere.multipleScatteringLutScratchSize()];
        assertThrows(IllegalArgumentException.class,
                () -> Atmosphere.multipleScatteringLut(EARTH, 2, 2, 4, 4, scratch, 1, new float[12]));
        assertThrows(IllegalArgumentException.class,
                () -> Atmosphere.multipleScatteringLut(EARTH, 0, 2, 4, 4, scratch, 0, new float[12]));
        assertThrows(IllegalArgumentException.class,
                () -> Atmosphere.multipleScatteringLut(EARTH, 2, 2, 4, 4, scratch, 0, new float[11]));
    }

    @Test
    void multipleScatteringLutUsesScratchAtItsOffset() {
        int offset = 7;
        double[] scratch = new double[offset + Atmosphere.multipleScatteringLutScratchSize()];
        float[] lut = Atmosphere.multipleScatteringLut(EARTH, 1, 1, 16, 8, scratch, offset, new float[3]);
        double r = EARTH.bottomRadius() + 0.5 * (EARTH.topRadius() - EARTH.bottomRadius());
        double[] exact = Atmosphere.multipleScattering(EARTH, r, 0.0, 16, 8, new double[3]);
        for (int c = 0; c < 3; c++) {
            assertEquals(exact[c], lut[c], 1e-3 * exact[c] + 1e-12);
        }
        for (int i = 0; i < offset; i++) {
            assertEquals(0.0, scratch[i], "scratch before the offset untouched");
        }
    }

    @Test
    void atmosphereModelRejectsNanAndOutOfRangeFields() {
        double[] rgb = {1e-6, 1e-6, 1e-6};
        double[] albedo = {0.3, 0.3, 0.3};
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, 0.8,
                Double.NaN, 15000, rgb, rgb, rgb, rgb, albedo));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, Double.POSITIVE_INFINITY, 8000,
                1200, 0.8, 25000, 15000, rgb, rgb, rgb, rgb, albedo));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, 0.8,
                25000, 15000, new double[] {Double.NaN, 0, 0}, rgb, rgb, rgb, albedo));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, 0.8,
                25000, 15000, rgb, rgb, rgb, new double[] {-1e-6, 0, 0}, albedo));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, 0.8,
                25000, 15000, rgb, rgb, new double[] {Double.POSITIVE_INFINITY, 1, 1}, rgb, albedo));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, 0.8,
                25000, 15000, rgb, rgb, rgb, rgb, new double[] {1.5, 0.3, 0.3}));
        assertThrows(IllegalArgumentException.class, () -> new AtmosphereModel(6.36e6, 6.46e6, 8000, 1200, Double.NaN,
                25000, 15000, rgb, rgb, rgb, rgb, albedo));
    }

    // ---------------------------------------------------------------- sampling, optics

    @Test
    void aliasTableSampleChecksU() {
        AliasTable t = new AliasTable(4).build(new float[] {1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class, () -> t.sample(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> t.sample(1.5f));
        assertThrows(IllegalArgumentException.class, () -> t.sample(Float.NaN));
        int last = t.sample(1f);
        assertTrue(last >= 0 && last < 4);
    }

    @Test
    void opticsRejectsNanIndices() {
        assertThrows(IllegalArgumentException.class, () -> Iorf.etaRatio(Float.NaN, 1f));
        assertThrows(IllegalArgumentException.class, () -> Iord.schlickF0(1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Iorf.schlickF0FromEtaRatio(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Fresnelf.dielectric(0.5f, 1f, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> Fresneld.conductor(0.5, Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Fresneld.conductor(0.5, 1.0, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ThinFilmf.reflectance(1f, 1.33f, 1.5f, Float.NaN, 0.8f, 550f));
        assertThrows(IllegalArgumentException.class,
                () -> ThinFilmd.reflectance(1.0, 1.33, 1.5, 300.0, 0.8, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ThinFilmd.reflectanceRgb(Double.NaN, 1.33, 1.5, 300.0, 0.8, new Vector3d()));
        assertFalse(Float.isNaN(Fresnelf.dielectric(0.5f, 1f, 1.5f)));
    }

    // ---------------------------------------------------------------- spherical

    @Test
    void sgIrradianceIsExactForWideLobes() {
        Vector3d z = new Vector3d(0, 0, 1);
        double[][] q = ShExtrasTest.sphereQuadrature(400, 256);
        Vector3d v = new Vector3d();
        for (double lambda : new double[] {0.0, 1e-6, 0.01, 0.3, 0.999}) {
            for (double angle = 0; angle <= Math.PI; angle += Math.PI / 6) {
                Vector3d n = new Vector3d(Math.sin(angle), 0, Math.cos(angle));
                double ref = 0;
                for (double[] p : q) {
                    v.set(p[0], p[1], p[2]);
                    ref += p[3] * SphericalGaussiansd.evaluate(z, lambda, 2.0, v) * Math.max(0, n.dot(v));
                }
                double e = SphericalGaussiansd.irradianceFitted(z, lambda, 2.0, n);
                // the quadrature itself is only good to about 1e-5 for the kink of the clamped cosine
                assertEquals(ref, e, 1e-4 * ref, "lambda " + lambda + ", angle " + angle);
                float ef = SphericalGaussiansf.irradianceFitted(new Vector3f(0, 0, 1), (float) lambda, 2f,
                        new Vector3f((float) n.x, (float) n.y, (float) n.z));
                assertEquals(e, ef, 1e-5 * e + 1e-6);
            }
        }
        // sharpness 0 is a constant environment: irradiance π a for every normal
        assertEquals(2.0 * Math.PI, SphericalGaussiansd.irradianceFitted(z, 0.0, 2.0, new Vector3d(1, 0, 0)), 1e-14);
    }

    @Test
    void sgFitTakesOffsetsAndChecksUpFront() {
        float[] dirs = {9, 9, 9, 0, 0, 1, 0, 0.6f, 0.8f, 0.6f, 0, 0.8f};
        float[] w = {7, 1, 0.5f, 0.25f};
        Vector4f a = new Vector4f(), b = new Vector4f();
        float ampA = SphericalGaussiansf.fit(dirs, 3, w, 1, 3, a);
        float ampB = SphericalGaussiansf.fit(java.util.Arrays.copyOfRange(dirs, 3, 12), 0,
                java.util.Arrays.copyOfRange(w, 1, 4), 0, 3, b);
        assertEquals(ampB, ampA);
        assertEquals(b, a);
        Vector4f untouched = new Vector4f(1, 2, 3, 4);
        assertThrows(IllegalArgumentException.class, () -> SphericalGaussiansf.fit(dirs, 4, w, 1, 3, untouched));
        assertThrows(IllegalArgumentException.class, () -> SphericalGaussiansf.fit(dirs, 0, w, 2, 3, untouched));
        assertThrows(IllegalArgumentException.class, () -> SphericalGaussiansf.fit(dirs, -1, w, 0, 1, untouched));
        assertThrows(IllegalArgumentException.class, () -> SphericalGaussiansf.fit(dirs, 0, w, 0, -1, untouched));
        assertEquals(new Vector4f(1, 2, 3, 4), untouched);
        double[] dd = {0, 0, 1};
        Vector4d dest = new Vector4d();
        assertEquals(0.0, SphericalGaussiansd.fit(dd, 0, new double[] {0}, 0, 1, dest));
        assertEquals(new Vector4d(0, 0, 1, 0), dest);
        assertThrows(IllegalArgumentException.class, () -> SphericalGaussiansd.fit(dd, 1, new double[1], 0, 1, dest));
    }

    @Test
    void hemiOctahedralEncodeFoldsTheLowerHemisphere() {
        Vector2f up = DirectionMappingsf.hemiOctahedralEncode(0.3f, -0.4f, 0.5f, new Vector2f());
        Vector2f down = DirectionMappingsf.hemiOctahedralEncode(0.3f, -0.4f, -0.5f, new Vector2f());
        assertEquals(up, down);
        Vector2d upd = DirectionMappingsd.hemiOctahedralEncode(0.3, -0.4, 0.5, new Vector2d());
        Vector2d downd = DirectionMappingsd.hemiOctahedralEncode(0.3, -0.4, -0.5, new Vector2d());
        assertEquals(upd, downd);
        Vector3d back = DirectionMappingsd.hemiOctahedralDecode(downd.x, downd.y, new Vector3d());
        assertTrue(back.distance(new Vector3d(0.3, -0.4, 0.5).normalize()) < 1e-15);
        // the result stays inside the square
        assertTrue(Math.abs(downd.x) <= 1 && Math.abs(downd.y) <= 1);
    }

    // ---------------------------------------------------------------- color

    @Test
    void pqDecodeClampsAboveOne() {
        assertEquals(TransferFunctionsd.pqDecode(1.0), TransferFunctionsd.pqDecode(1.5));
        assertEquals(TransferFunctionsd.pqDecode(1.0), TransferFunctionsd.pqDecode(3.0)); // used to be NaN
        assertEquals(1.0, TransferFunctionsd.pqDecode(1.0), 1e-12);
        assertEquals(TransferFunctionsf.pqDecode(1f), TransferFunctionsf.pqDecode(2.5f));
        assertEquals(0.0, TransferFunctionsd.pqDecode(-1.0));
    }

    @Test
    void cieObserverChecksTheChannel() {
        assertThrows(IllegalArgumentException.class, () -> CieObserver.CIE_1931_2DEG.at(500, 3));
        assertThrows(IllegalArgumentException.class, () -> CieObserver.CIE_1964_10DEG.at(100, -1));
        assertEquals(0.0, CieObserver.CIE_1931_2DEG.at(100, 1));
        assertTrue(CieObserver.CIE_1931_2DEG.at(555, 1) > 0.99);
    }

    @Test
    void colorSpacesTwinsHaveTheSameMethods() {
        Vector3dc d65 = new Vector3d(0.95047, 1.0, 1.08883);
        Vector3dc aces = new Vector3d(0.32168 / 0.33767, 1.0, (1 - 0.32168 - 0.33767) / 0.33767);
        for (ColorSpacesd.Adaptation a : ColorSpacesd.Adaptation.values()) {
            double[] md = ColorSpacesd.adaptationMatrix(d65, aces, a, new double[9]);
            float[] mf = ColorSpacesf.adaptationMatrix(new Vector3f(d65), new Vector3f(aces), a, new float[9]);
            double[] dFromF = ColorSpacesd.adaptationMatrix(new Vector3d(new Vector3f(d65)),
                    new Vector3d(new Vector3f(aces)), a, new double[9]);
            for (int i = 0; i < 9; i++) {
                assertEquals((float) dFromF[i], mf[i], 0f);
                assertEquals(md[i], mf[i], 1e-6);
            }
        }
        // the adaptation matrix composes into the table's conversion matrix
        double[] toXyz = ColorSpacesd.toXyzMatrix(ColorGamut.SRGB, new double[9]);
        double[] fromXyz = ColorSpacesd.fromXyzMatrix(ColorGamut.ACES_CG, new double[9]);
        double[] adapt = ColorSpacesd.adaptationMatrix(d65, aces, ColorSpacesd.Adaptation.BRADFORD, new double[9]);
        double[] expected = ColorSpacesd.conversionMatrix(ColorGamut.SRGB, ColorGamut.ACES_CG,
                ColorSpacesd.Adaptation.BRADFORD, new double[9]);
        double[] composed = mul3(fromXyz, mul3(adapt, toXyz));
        assertArrayEquals(expected, composed, 1e-12);

        Vector3f rgb = new Vector3f(0.2f, 0.5f, 0.7f);
        for (ColorGamut g : ColorGamut.values()) {
            Vector3d xd = ColorSpacesd.toXyz(new Vector3d(rgb), g, new Vector3d());
            Vector3f xf = ColorSpacesf.toXyz(rgb, g, new Vector3f());
            assertTrue(new Vector3d(xf).distance(xd) < 1e-6, g.name());
            Vector3f back = ColorSpacesf.fromXyz(xf, g, new Vector3f());
            assertTrue(back.distance(rgb) < 1e-5f, g.name());
            float[] mf = ColorSpacesf.fromXyzMatrix(g, new float[9]);
            double[] mdd = ColorSpacesd.fromXyzMatrix(g, new double[9]);
            for (int i = 0; i < 9; i++) {
                assertEquals((float) mdd[i], mf[i], 0f);
            }
        }
    }

    private static double[] mul3(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j];
            }
        }
        return r;
    }

    // ---------------------------------------------------------------- ltc, renderingmath

    @Test
    void ltcClippedFormFactorsAgreeWithTheUnclippedOneAboveTheHorizon() {
        Vector3f v0 = new Vector3f(-0.5f, -0.5f, 1), v1 = new Vector3f(0.5f, -0.5f, 1);
        Vector3f v2 = new Vector3f(0.5f, 0.5f, 1), v3 = new Vector3f(-0.5f, 0.5f, 1);
        assertEquals(LtcEvalf.formFactorRect(v0, v1, v2, v3), LtcEvalf.formFactorRectClipped(v0, v1, v2, v3), 1e-6f);
        assertEquals(LtcEvalf.formFactorRectClipped(v0, v1, v2, v3),
                LtcEvalf.formFactorPolygonClipped(new Vector3fc[] {v0, v1, v2, v3}, 4), 0f);
        // half below the horizon: the clipped light is the upper half
        Vector3d w0 = new Vector3d(-0.5, 1, -0.5), w1 = new Vector3d(0.5, 1, -0.5);
        Vector3d w2 = new Vector3d(0.5, 1, 0.5), w3 = new Vector3d(-0.5, 1, 0.5);
        Vector3d h0 = new Vector3d(-0.5, 1, 0), h1 = new Vector3d(0.5, 1, 0);
        double clippedA = LtcEvald.formFactorRectClipped(w3, w2, w1, w0);
        assertEquals(LtcEvald.formFactorRect(w3, w2, h1, h0), clippedA, 1e-15);
        double clippedB = LtcEvald.formFactorRectClipped(w0, w1, w2, w3);
        assertEquals(LtcEvald.formFactorRect(h1, w2, w3, h0), clippedB, 1e-15);
        assertTrue(Math.max(clippedA, clippedB) > 0);
        assertEquals(0.0, LtcEvald.formFactorPolygonClipped(new Vector3dc[] {new Vector3d(0, 0, -1),
            new Vector3d(1, 0, -1), new Vector3d(0, 1, -1)}, 3));
    }

    @Test
    void bilinearVec3TakesReadOnlyVectorsAndHasADoubleTwin() {
        Vector3fc q00 = new Vector3f(0, 1, 2), q10 = new Vector3f(1, 3, 5), q01 = new Vector3f(2, 0, -1);
        Vector3fc q11 = new Vector3f(4, 4, 4);
        Vector3f f = Interpolationf.bilinearVec3(q00, q10, q01, q11, 0.25f, 0.75f, new Vector3f());
        assertEquals(Interpolationf.bilinear(0, 1, 2, 4, 0.25f, 0.75f), f.x, 0f);
        Vector3dc d00 = new Vector3d(q00), d10 = new Vector3d(q10), d01 = new Vector3d(q01), d11 = new Vector3d(q11);
        Vector3d d = Interpolationd.bilinearVec3(d00, d10, d01, d11, 0.25, 0.75, new Vector3d());
        assertEquals(Interpolationd.bilinear(1, 3, 0, 4, 0.25, 0.75), d.y, 0.0);
        assertEquals(Interpolationd.bilinear(2, 5, -1, 4, 0.25, 0.75), d.z, 0.0);
        Vector3d alias = new Vector3d(d00);
        assertEquals(d, Interpolationd.bilinearVec3(alias, d10, d01, d11, 0.25, 0.75, alias));
    }
}
