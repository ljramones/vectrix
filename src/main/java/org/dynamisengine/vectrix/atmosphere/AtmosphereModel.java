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
package org.dynamisengine.vectrix.atmosphere;

/**
 * An Earth-like atmosphere: a spherical shell between {@code bottomRadius} and {@code topRadius} (metres) holding
 * Rayleigh scattering and Mie scattering/absorption with exponential density profiles, and ozone absorption with a
 * tent profile ({@code max(0, 1 − |h − ozoneCenterAltitude| / ozoneHalfWidth)}). Coefficients are RGB, per metre.
 * Immutable: arrays are copied in and out.
 *
 * @param bottomRadius ground radius
 * @param topRadius radius of the top of the atmosphere
 * @param rayleighScaleHeight Rayleigh density scale height
 * @param mieScaleHeight Mie density scale height
 * @param mieAnisotropy Mie phase function asymmetry (Cornette-Shanks {@code g})
 * @param ozoneCenterAltitude altitude of peak ozone density
 * @param ozoneHalfWidth half-width of the ozone tent
 * @param rayleighScattering Rayleigh scattering coefficients at sea level (RGB)
 * @param mieScattering Mie scattering coefficients at sea level (RGB)
 * @param mieExtinction Mie extinction (scattering plus absorption) at sea level (RGB)
 * @param ozoneAbsorption ozone absorption at peak density (RGB)
 * @param groundAlbedo Lambertian ground albedo (RGB)
 * @since 2.3.0
 */
public record AtmosphereModel(double bottomRadius, double topRadius, double rayleighScaleHeight,
        double mieScaleHeight, double mieAnisotropy, double ozoneCenterAltitude, double ozoneHalfWidth,
        double[] rayleighScattering, double[] mieScattering, double[] mieExtinction, double[] ozoneAbsorption,
        double[] groundAlbedo) {

    /** Validates and copies the arrays. */
    public AtmosphereModel {
        if (!(bottomRadius > 0.0) || !(topRadius > bottomRadius) || !(rayleighScaleHeight > 0.0)
                || !(mieScaleHeight > 0.0) || !(ozoneHalfWidth > 0.0) || !(mieAnisotropy > -1.0 && mieAnisotropy < 1.0)) {
            throw new IllegalArgumentException("atmosphere geometry or profile");
        }
        rayleighScattering = rgb(rayleighScattering, "rayleighScattering");
        mieScattering = rgb(mieScattering, "mieScattering");
        mieExtinction = rgb(mieExtinction, "mieExtinction");
        ozoneAbsorption = rgb(ozoneAbsorption, "ozoneAbsorption");
        groundAlbedo = rgb(groundAlbedo, "groundAlbedo");
        for (int c = 0; c < 3; c++) {
            if (mieExtinction[c] < mieScattering[c]) {
                throw new IllegalArgumentException("Mie extinction below Mie scattering");
            }
        }
    }

    private static double[] rgb(double[] v, String name) {
        if (v == null || v.length != 3) {
            throw new IllegalArgumentException(name + " needs 3 components");
        }
        return v.clone();
    }

    /**
     * Earth, as in Hillaire 2020 ("A Scalable and Production Ready Sky and Atmosphere Rendering Technique") and
     * Bruneton 2017: radii 6360 / 6460 km, Rayleigh (5.802, 13.558, 33.1)·10⁻⁶ m⁻¹ with an 8 km scale height, Mie
     * scattering 3.996·10⁻⁶ and extinction 4.40·10⁻⁶ m⁻¹ with a 1.2 km scale height and {@code g = 0.8}, ozone
     * (0.650, 1.881, 0.085)·10⁻⁶ m⁻¹ peaking at 25 km between 10 and 40 km, and ground albedo 0.3.
     */
    public static AtmosphereModel earth() {
        return new AtmosphereModel(6_360_000.0, 6_460_000.0, 8_000.0, 1_200.0, 0.8, 25_000.0, 15_000.0,
                new double[] {5.802e-6, 13.558e-6, 33.1e-6}, new double[] {3.996e-6, 3.996e-6, 3.996e-6},
                new double[] {4.440e-6, 4.440e-6, 4.440e-6}, new double[] {0.650e-6, 1.881e-6, 0.085e-6},
                new double[] {0.3, 0.3, 0.3});
    }

    @Override
    public double[] rayleighScattering() {
        return rayleighScattering.clone();
    }

    @Override
    public double[] mieScattering() {
        return mieScattering.clone();
    }

    @Override
    public double[] mieExtinction() {
        return mieExtinction.clone();
    }

    @Override
    public double[] ozoneAbsorption() {
        return ozoneAbsorption.clone();
    }

    @Override
    public double[] groundAlbedo() {
        return groundAlbedo.clone();
    }

    /** Rayleigh scattering of channel {@code c}. */
    public double rayleighScattering(int c) {
        return rayleighScattering[c];
    }

    /** Mie scattering of channel {@code c}. */
    public double mieScattering(int c) {
        return mieScattering[c];
    }

    /** Mie extinction of channel {@code c}. */
    public double mieExtinction(int c) {
        return mieExtinction[c];
    }

    /** Ozone absorption of channel {@code c}. */
    public double ozoneAbsorption(int c) {
        return ozoneAbsorption[c];
    }

    /** Ground albedo of channel {@code c}. */
    public double groundAlbedo(int c) {
        return groundAlbedo[c];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AtmosphereModel m && bottomRadius == m.bottomRadius && topRadius == m.topRadius
                && rayleighScaleHeight == m.rayleighScaleHeight && mieScaleHeight == m.mieScaleHeight
                && mieAnisotropy == m.mieAnisotropy && ozoneCenterAltitude == m.ozoneCenterAltitude
                && ozoneHalfWidth == m.ozoneHalfWidth && java.util.Arrays.equals(rayleighScattering, m.rayleighScattering)
                && java.util.Arrays.equals(mieScattering, m.mieScattering)
                && java.util.Arrays.equals(mieExtinction, m.mieExtinction)
                && java.util.Arrays.equals(ozoneAbsorption, m.ozoneAbsorption)
                && java.util.Arrays.equals(groundAlbedo, m.groundAlbedo);
    }

    @Override
    public int hashCode() {
        int h = java.util.Objects.hash(bottomRadius, topRadius, rayleighScaleHeight, mieScaleHeight, mieAnisotropy,
                ozoneCenterAltitude, ozoneHalfWidth);
        h = 31 * h + java.util.Arrays.hashCode(rayleighScattering);
        h = 31 * h + java.util.Arrays.hashCode(mieScattering);
        h = 31 * h + java.util.Arrays.hashCode(mieExtinction);
        h = 31 * h + java.util.Arrays.hashCode(ozoneAbsorption);
        return 31 * h + java.util.Arrays.hashCode(groundAlbedo);
    }

    @Override
    public String toString() {
        return "AtmosphereModel[bottomRadius=" + bottomRadius + ", topRadius=" + topRadius + ", rayleigh="
                + java.util.Arrays.toString(rayleighScattering) + ", mie=" + java.util.Arrays.toString(mieScattering)
                + ", ozone=" + java.util.Arrays.toString(ozoneAbsorption) + "]";
    }
}
