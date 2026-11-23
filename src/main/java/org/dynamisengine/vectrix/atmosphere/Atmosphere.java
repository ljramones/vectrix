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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Atmosphere math in double precision (precomputation; DynamisSky owns rendering policy). Positions are given as
 * {@code (r, mu)}: distance {@code r} from the planet center and {@code mu}, the cosine between the ray direction and
 * the local zenith. Distances, radii and altitudes are in metres; RGB results go into the first three elements of the
 * caller's {@code out} array, which is also returned. The ray-geometry and density functions do not validate their
 * inputs; the integrals and tables check their lengths, sizes and counts up front and throw
 * {@code IllegalArgumentException}. The integrals use a per-thread scratch area, so after the first call on a thread
 * they do not allocate.
 * <ul>
 * <li>Geometry without cancellation: distances to the top and the ground use the product forms of the quadratic's
 * roots, and altitudes along a ray use {@code h = (r(t)² − R²) / (r(t) + R)}, so a ray grazing the ground at 1 mm
 * altitude is as accurate as a vertical one.</li>
 * <li>{@link #opticalDepth}: adaptive Gauss-Kronrod (7-15) integration of the three density profiles to a relative
 * accuracy of about 1e-14 (each component against its own total), split exactly where the ray crosses the ozone
 * profile's kinks, and never chasing accuracy below the integrand's own evaluation noise (a few ulps per metre).</li>
 * <li>{@link #transmittanceLut}: Bruneton's (2017) parameterization, as used by Hillaire 2020, with
 * {@link #transmittanceLutUv} / {@link #transmittanceLutRMu} for lookups.</li>
 * <li>{@link #multipleScattering} / {@link #multipleScatteringLut}: Hillaire's 2020 multiple-scattering transfer
 * {@code Ψ = L₂ / (1 − f_ms)} (an isotropic-phase approximation by design), integrated over a Fibonacci sphere of
 * directions with energy-conserving ray-march steps.</li>
 * </ul>
 *
 * @since 2.3.0
 */
public final class Atmosphere {
    private Atmosphere() {
    }

    // ---------------------------------------------------------------- densities and coefficients

    /**
     * Returns the relative Rayleigh density at altitude {@code h}: {@code exp(−h / rayleighScaleHeight)}, 1 at the
     * ground.
     *
     * @param m the atmosphere
     * @param h the altitude above the ground, in metres
     * @return the density relative to the ground
     */
    public static double rayleighDensity(AtmosphereModel m, double h) {
        return AccurateMath.exp(-h / m.rayleighScaleHeight());
    }

    /**
     * Returns the relative Mie density at altitude {@code h}: {@code exp(−h / mieScaleHeight)}, 1 at the ground.
     *
     * @param m the atmosphere
     * @param h the altitude above the ground, in metres
     * @return the density relative to the ground
     */
    public static double mieDensity(AtmosphereModel m, double h) {
        return AccurateMath.exp(-h / m.mieScaleHeight());
    }

    /**
     * Returns the relative ozone density at altitude {@code h}: the tent profile
     * {@code max(0, 1 − |h − ozoneCenterAltitude| / ozoneHalfWidth)}, 1 at its center.
     *
     * @param m the atmosphere
     * @param h the altitude above the ground, in metres
     * @return the density relative to the peak, in [0, 1]
     */
    public static double ozoneDensity(AtmosphereModel m, double h) {
        return java.lang.Math.max(0.0, 1.0 - java.lang.Math.abs(h - m.ozoneCenterAltitude()) / m.ozoneHalfWidth());
    }

    /**
     * Computes the extinction coefficients at altitude {@code h}: Rayleigh scattering, Mie extinction and ozone
     * absorption, each scaled by its density.
     *
     * @param m the atmosphere
     * @param h the altitude above the ground, in metres
     * @param out receives the RGB coefficients, per metre, in elements 0 to 2
     * @return {@code out}
     */
    public static double[] extinction(AtmosphereModel m, double h, double[] out) {
        double dr = rayleighDensity(m, h), dm = mieDensity(m, h), dz = ozoneDensity(m, h);
        for (int c = 0; c < 3; c++) {
            out[c] = m.rayleighScattering(c) * dr + m.mieExtinction(c) * dm + m.ozoneAbsorption(c) * dz;
        }
        return out;
    }

    /**
     * Computes the scattering coefficients (Rayleigh plus Mie) at altitude {@code h}.
     *
     * @param m the atmosphere
     * @param h the altitude above the ground, in metres
     * @param out receives the RGB coefficients, per metre, in elements 0 to 2
     * @return {@code out}
     */
    public static double[] scattering(AtmosphereModel m, double h, double[] out) {
        double dr = rayleighDensity(m, h), dm = mieDensity(m, h);
        for (int c = 0; c < 3; c++) {
            out[c] = m.rayleighScattering(c) * dr + m.mieScattering(c) * dm;
        }
        return out;
    }

    // ---------------------------------------------------------------- geometry

    /** {@code r²(μ² − 1) + R²} without cancellation: {@code (R − r)(R + r) + (rμ)²}. */
    private static double discriminant(double r, double mu, double radius) {
        double rmu = r * mu;
        return (radius - r) * (radius + r) + rmu * rmu;
    }

    /**
     * Returns the distance from {@code (r, mu)} to the top of the atmosphere along the ray, ignoring the ground.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, at most {@code topRadius}
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @return the distance, in metres, at least 0
     */
    public static double distanceToTop(AtmosphereModel m, double r, double mu) {
        double top = m.topRadius();
        double s = java.lang.Math.sqrt(java.lang.Math.max(discriminant(r, mu, top), 0.0));
        double rmu = r * mu;
        // the positive root; for an upward ray -rμ + s cancels, so use (R² − r²) / (rμ + s)
        return rmu > 0.0 ? java.lang.Math.max((top - r) * (top + r), 0.0) / (rmu + s) : s - rmu;
    }

    /**
     * Returns whether the ray from {@code (r, mu)} hits the ground: it points downward and passes within
     * {@code bottomRadius} of the center.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @return true if the ray hits the ground
     */
    public static boolean intersectsGround(AtmosphereModel m, double r, double mu) {
        return mu < 0.0 && discriminant(r, mu, m.bottomRadius()) >= 0.0;
    }

    /**
     * Returns the distance from {@code (r, mu)} to the ground along the ray. Only meaningful if
     * {@link #intersectsGround} is true for the same arguments.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, at least {@code bottomRadius}
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 0)
     * @return the distance to the nearer ground intersection, in metres
     */
    public static double distanceToGround(AtmosphereModel m, double r, double mu) {
        double b = m.bottomRadius();
        double s = java.lang.Math.sqrt(java.lang.Math.max(discriminant(r, mu, b), 0.0));
        // the nearer root -rμ - s cancels near grazing; it equals (r² − B²) / (-rμ + s)
        return java.lang.Math.max((r - b) * (r + b), 0.0) / (s - r * mu);
    }

    /**
     * Returns the distance from {@code (r, mu)} to the atmosphere's boundary along the ray: the ground if the ray hits
     * it, else the top.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, in [{@code bottomRadius}, {@code topRadius}]
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @return the distance, in metres
     */
    public static double distanceToBoundary(AtmosphereModel m, double r, double mu) {
        return intersectsGround(m, r, mu) ? distanceToGround(m, r, mu) : distanceToTop(m, r, mu);
    }

    /**
     * Returns the altitude above the ground after travelling {@code t} along the ray from {@code (r, mu)}, computed
     * as {@code (r(t)² − R²) / (r(t) + R)} so that it stays accurate near the ground.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center at the ray's start, in metres
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @param t the distance travelled along the ray, in metres
     * @return the altitude, in metres; negative below the ground
     */
    public static double altitudeAt(AtmosphereModel m, double r, double mu, double t) {
        double b = m.bottomRadius();
        double r2MinusB2 = t * (t + 2.0 * r * mu) + (r - b) * (r + b);
        double rt = java.lang.Math.sqrt(java.lang.Math.max(r2MinusB2 + b * b, 0.0));
        return r2MinusB2 / (rt + b);
    }

    // ---------------------------------------------------------------- optical depth

    private static final double[] XGK = {0.991455371120812639206854697526329, 0.949107912342758524526189684047851,
        0.864864423359769072789712788640926, 0.741531185599394439863864773280788, 0.586087235467691130294144845693013,
        0.405845151377397166906606412076961, 0.207784955007898467600689403773245, 0.0};
    private static final double[] WGK = {0.022935322010529224963732008058970, 0.063092092629978553290700663189204,
        0.104790010322250183839876322541518, 0.140653259715525918745189590510238, 0.169004726639267902826583426598550,
        0.190350578064785409913256402421014, 0.204432940075298892414161999234649, 0.209482141084727828012999174891714};
    private static final double[] WG = {0.129484966168869693270611432679082, 0.279705391489276667901467771423780,
        0.381830050505118944950369775488975, 0.417959183673469387755102040816327};
    private static final double REL_TOL = 1e-14;
    private static final int MAX_DEPTH = 40;
    /** Hard budget of subdivisions per integral, so no input can recurse exponentially. */
    private static final int MAX_PANELS = 1 << 14;
    /** Absolute error floor per metre of ray for the O(1) normalized densities: a few ulps. */
    private static final double NOISE_PER_METRE = 4.0 * 0x1.0p-52;

    /** Per-thread working memory of the integrals, so they do not allocate after the first call on a thread. */
    private static final class Scratch {
        final double[] cuts = new double[8];
        final double[] kg = new double[6];
        final double[] floor = new double[3];
        final double[] sum = new double[7];
        final double[] depth = new double[3];
        final double[] ext = new double[3];
        final double[] sca = new double[3];
        final double[] tSun = new double[3];
        final double[] uv = new double[2];
    }

    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    /**
     * Integrates the relative densities {@code ∫ρ dt} (Rayleigh, Mie, ozone) along the ray from {@code (r, mu)} for a
     * length {@code length} and adds them to {@code acc[0..2]}, by the adaptive Gauss-Kronrod scheme described on the
     * class. A length of 0 adds nothing. Uses a per-thread scratch area, so it does not allocate after the first call
     * on a thread.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center at the ray's start, in metres
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @param length the length of the ray segment, in metres, at least 0 and finite
     * @param acc the Rayleigh, Mie and ozone integrals (in metres) are added to elements 0, 1 and 2
     * @return {@code acc}
     * @throws IllegalArgumentException if {@code length} is negative, NaN or infinite, or {@code acc} has fewer than
     *         3 elements (checked before anything is written)
     */
    public static double[] densityIntegrals(AtmosphereModel m, double r, double mu, double length, double[] acc) {
        if (!(length >= 0.0) || length == Double.POSITIVE_INFINITY) {
            throw new IllegalArgumentException("length must be finite and at least 0: " + length);
        }
        checkLength(acc, 3, "acc");
        Scratch sc = SCRATCH.get();
        // breakpoints where the ray crosses the ozone profile's kinks (bottom, center, top of the tent)
        double[] cuts = sc.cuts;
        int n = 0;
        cuts[n++] = 0.0;
        for (int k = -1; k <= 1; k++) {
            double level = k == 0 ? m.ozoneCenterAltitude()
                    : k < 0 ? m.ozoneCenterAltitude() - m.ozoneHalfWidth() : m.ozoneCenterAltitude() + m.ozoneHalfWidth();
            double rad = m.bottomRadius() + level;
            double disc = discriminant(r, mu, rad);
            if (disc < 0.0) {
                continue;
            }
            double s = java.lang.Math.sqrt(disc);
            double t0 = -r * mu - s, t1 = -r * mu + s;
            if (t0 > 0.0 && t0 < length) {
                cuts[n++] = t0;
            }
            if (t1 > 0.0 && t1 < length) {
                cuts[n++] = t1;
            }
        }
        cuts[n++] = length;
        java.util.Arrays.sort(cuts, 0, n);
        // a coarse first pass sets each component's global error floor (per metre of ray)
        double[] kg = sc.kg, floor = sc.floor, sum = sc.sum;
        java.util.Arrays.fill(floor, 0.0);
        java.util.Arrays.fill(sum, 0.0);
        for (int i = 0; i + 1 < n; i++) {
            if (cuts[i + 1] > cuts[i]) {
                kronrod(m, r, mu, cuts[i], cuts[i + 1], kg);
                for (int c = 0; c < 3; c++) {
                    floor[c] += java.lang.Math.abs(kg[c]);
                }
            }
        }
        for (int c = 0; c < 3; c++) {
            // relative to the component's own total, but never below the integrand's evaluation noise: densities
            // are O(1) and the altitude carries an absolute error of about 1e-11 m on Earth-sized radii
            floor[c] = java.lang.Math.max(length > 0.0 ? REL_TOL * floor[c] / length : 0.0, NOISE_PER_METRE);
        }
        // sum holds Neumaier-compensated sums: values, compensations, panel count
        for (int i = 0; i + 1 < n; i++) {
            if (cuts[i + 1] > cuts[i]) {
                adaptive(m, r, mu, cuts[i], cuts[i + 1], 0, floor, kg, sum);
            }
        }
        for (int c = 0; c < 3; c++) {
            acc[c] += sum[c] + sum[c + 3];
        }
        return acc;
    }

    private static void checkLength(double[] a, int needed, String name) {
        if (a.length < needed) {
            throw new IllegalArgumentException(name + " needs at least " + needed + " elements: " + a.length);
        }
    }

    /** Gauss-Kronrod 15 (into {@code out[0..2]}) and embedded Gauss 7 ({@code out[3..5]}) on [a, b]. */
    private static void kronrod(AtmosphereModel m, double r, double mu, double a, double b, double[] out) {
        double c = 0.5 * (a + b), hw = 0.5 * (b - a);
        double k0 = 0, k1 = 0, k2 = 0, g0 = 0, g1 = 0, g2 = 0;
        double h = altitudeAt(m, r, mu, c);
        double d0 = rayleighDensity(m, h), d1 = mieDensity(m, h), d2 = ozoneDensity(m, h);
        k0 += WGK[7] * d0;
        k1 += WGK[7] * d1;
        k2 += WGK[7] * d2;
        g0 += WG[3] * d0;
        g1 += WG[3] * d1;
        g2 += WG[3] * d2;
        for (int j = 0; j < 7; j++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                h = altitudeAt(m, r, mu, c + sgn * hw * XGK[j]);
                d0 = rayleighDensity(m, h);
                d1 = mieDensity(m, h);
                d2 = ozoneDensity(m, h);
                k0 += WGK[j] * d0;
                k1 += WGK[j] * d1;
                k2 += WGK[j] * d2;
                if ((j & 1) == 1) {
                    g0 += WG[j >> 1] * d0;
                    g1 += WG[j >> 1] * d1;
                    g2 += WG[j >> 1] * d2;
                }
            }
        }
        out[0] = k0 * hw;
        out[1] = k1 * hw;
        out[2] = k2 * hw;
        out[3] = g0 * hw;
        out[4] = g1 * hw;
        out[5] = g2 * hw;
    }

    /**
     * Recursive bisection: a panel is accepted when every component is converged relative to itself, or its error
     * is negligible against that component's whole-ray integral ({@code floor} per metre times the panel length).
     */
    private static void adaptive(AtmosphereModel m, double r, double mu, double a, double b, int depth,
            double[] floor, double[] kg, double[] acc) {
        kronrod(m, r, mu, a, b, kg);
        double width = b - a;
        boolean ok = true;
        for (int c = 0; c < 3 && ok; c++) {
            double err = java.lang.Math.abs(kg[c] - kg[c + 3]);
            ok = err <= REL_TOL * java.lang.Math.abs(kg[c]) || err <= floor[c] * width;
        }
        if (ok || depth >= MAX_DEPTH || ++acc[6] > MAX_PANELS) {
            add(acc, 0, kg[0]);
            add(acc, 1, kg[1]);
            add(acc, 2, kg[2]);
            return;
        }
        double c = 0.5 * (a + b);
        adaptive(m, r, mu, a, c, depth + 1, floor, kg, acc);
        adaptive(m, r, mu, c, b, depth + 1, floor, kg, acc);
    }

    /** Neumaier summation: {@code acc[c]} is the sum, {@code acc[c + 3]} the running compensation. */
    private static void add(double[] acc, int c, double v) {
        double s = acc[c], t = s + v;
        acc[c + 3] += java.lang.Math.abs(s) >= java.lang.Math.abs(v) ? (s - t) + v : (v - t) + s;
        acc[c] = t;
    }

    /**
     * Computes the optical depth from {@code (r, mu)} to the atmosphere's boundary (the ground if the ray hits it).
     * Does not allocate after the first call on a thread, like {@link #densityIntegrals}.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, in [{@code bottomRadius}, {@code topRadius}]
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @param out receives the dimensionless RGB optical depth in elements 0 to 2
     * @return {@code out}
     * @throws IllegalArgumentException if {@code out} has fewer than 3 elements, or the distance to the boundary is
     *         NaN (an {@code r} or {@code mu} that is NaN)
     */
    public static double[] opticalDepth(AtmosphereModel m, double r, double mu, double[] out) {
        return opticalDepth(m, r, mu, distanceToBoundary(m, r, mu), out);
    }

    /**
     * Computes the optical depth along the first {@code length} metres of the ray from {@code (r, mu)}. Does not
     * allocate after the first call on a thread, like {@link #densityIntegrals}.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @param length the length of the ray segment, in metres, at least 0 and finite
     * @param out receives the dimensionless RGB optical depth in elements 0 to 2
     * @return {@code out}
     * @throws IllegalArgumentException if {@code length} is negative, NaN or infinite, or {@code out} has fewer than 3
     *         elements (checked before anything is written)
     */
    public static double[] opticalDepth(AtmosphereModel m, double r, double mu, double length, double[] out) {
        checkLength(out, 3, "out");
        double[] acc = SCRATCH.get().depth;
        acc[0] = 0.0;
        acc[1] = 0.0;
        acc[2] = 0.0;
        densityIntegrals(m, r, mu, length, acc);
        for (int c = 0; c < 3; c++) {
            out[c] = m.rayleighScattering(c) * acc[0] + m.mieExtinction(c) * acc[1] + m.ozoneAbsorption(c) * acc[2];
        }
        return out;
    }

    /**
     * Computes the transmittance from {@code (r, mu)} to the top of the atmosphere; 0 if the ray hits the ground.
     * Does not allocate after the first call on a thread, like
     * {@link #opticalDepth(AtmosphereModel, double, double, double, double[])}.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, in [{@code bottomRadius}, {@code topRadius}]
     * @param mu the cosine between the ray direction and the local zenith, in [-1, 1]
     * @param out receives the RGB transmittance, in [0, 1], in elements 0 to 2
     * @return {@code out}
     * @throws IllegalArgumentException if {@code out} has fewer than 3 elements, or the distance to the top is NaN
     *         (an {@code r} or {@code mu} that is NaN)
     */
    public static double[] transmittanceToTop(AtmosphereModel m, double r, double mu, double[] out) {
        checkLength(out, 3, "out");
        if (intersectsGround(m, r, mu)) {
            out[0] = out[1] = out[2] = 0.0;
            return out;
        }
        opticalDepth(m, r, mu, distanceToTop(m, r, mu), out);
        for (int c = 0; c < 3; c++) {
            out[c] = AccurateMath.exp(-out[c]);
        }
        return out;
    }

    // ---------------------------------------------------------------- transmittance LUT (Bruneton mapping)

    private static void checkTableSize(int width, int height) {
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("table width and height must be at least 2: " + width + " x " + height);
        }
    }

    private static void checkTableOut(float[] outRgb, int width, int height) {
        if (outRgb.length < 3L * width * height) {
            throw new IllegalArgumentException("outRgb must hold 3 * width * height floats: " + outRgb.length);
        }
    }

    private static double texCoord(double x, int size) {
        return 0.5 / size + x * (1.0 - 1.0 / size);
    }

    private static double unitFromTexCoord(double u, int size) {
        return (u - 0.5 / size) / (1.0 - 1.0 / size);
    }

    /**
     * Texture coordinates {@code (u, v)} of the transmittance LUT texel for {@code (r, mu)} (a ray that does not hit
     * the ground), for a {@code width × height} table, with Bruneton's half-texel-exact mapping: {@code u} maps the
     * distance to the top between its minimum and maximum for that {@code r}, and {@code v} maps
     * {@code √(r² − bottomRadius²)}, each onto [0.5 / size, 1 − 0.5 / size].
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, in [{@code bottomRadius}, {@code topRadius}]
     * @param mu the cosine between the ray direction and the local zenith, for a ray that does not hit the ground
     * @param width the table width in texels, at least 2
     * @param height the table height in texels, at least 2
     * @param out receives {@code u} in element 0 and {@code v} in element 1
     * @return {@code out}
     * @throws IllegalArgumentException if {@code width} or {@code height} is below 2 or {@code out} has fewer than 2
     *         elements
     */
    public static double[] transmittanceLutUv(AtmosphereModel m, double r, double mu, int width, int height,
            double[] out) {
        checkTableSize(width, height);
        checkLength(out, 2, "out");
        double b = m.bottomRadius(), top = m.topRadius();
        double hh = java.lang.Math.sqrt((top - b) * (top + b));
        double rho = java.lang.Math.sqrt(java.lang.Math.max((r - b) * (r + b), 0.0));
        double d = distanceToTop(m, r, mu);
        double dMin = top - r, dMax = rho + hh;
        double xMu = dMax > dMin ? (d - dMin) / (dMax - dMin) : 0.0;
        out[0] = texCoord(xMu, width);
        out[1] = texCoord(rho / hh, height);
        return out;
    }

    /**
     * Inverts {@link #transmittanceLutUv}: returns {@code (r, mu)} for texture coordinates {@code (u, v)}, with
     * {@code mu} clamped to [-1, 1].
     *
     * @param m the atmosphere
     * @param u the horizontal texture coordinate, in [0.5 / width, 1 − 0.5 / width]
     * @param v the vertical texture coordinate, in [0.5 / height, 1 − 0.5 / height]
     * @param width the table width in texels, at least 2
     * @param height the table height in texels, at least 2
     * @param out receives {@code r} (metres) in element 0 and {@code mu} in element 1
     * @return {@code out}
     * @throws IllegalArgumentException if {@code width} or {@code height} is below 2 or {@code out} has fewer than 2
     *         elements
     */
    public static double[] transmittanceLutRMu(AtmosphereModel m, double u, double v, int width, int height,
            double[] out) {
        checkTableSize(width, height);
        checkLength(out, 2, "out");
        double b = m.bottomRadius(), top = m.topRadius();
        double hh = java.lang.Math.sqrt((top - b) * (top + b));
        double xMu = unitFromTexCoord(u, width), xR = unitFromTexCoord(v, height);
        double rho = hh * xR;
        double r = java.lang.Math.sqrt(rho * rho + b * b);
        double dMin = top - r, dMax = rho + hh;
        double d = dMin + xMu * (dMax - dMin);
        double mu = d == 0.0 ? 1.0 : ((hh - rho) * (hh + rho) - d * d) / (2.0 * r * d);
        out[0] = r;
        out[1] = java.lang.Math.max(-1.0, java.lang.Math.min(1.0, mu));
        return out;
    }

    /**
     * Builds the transmittance LUT: {@code width × height} texels of interleaved RGB floats, texel {@code (x, y)} at
     * index {@code 3 (y width + x)}, each the {@link #transmittanceToTop} of the {@code (r, mu)} that
     * {@link #transmittanceLutRMu} gives for its center. Allocates two small arrays per call.
     *
     * @param m the atmosphere
     * @param width the table width in texels, at least 2
     * @param height the table height in texels, at least 2
     * @param outRgb receives the table, starting at index 0; length at least {@code 3 width height}
     * @return {@code outRgb}
     * @throws IllegalArgumentException if {@code width} or {@code height} is below 2 or {@code outRgb} is too small
     *         (checked before anything is written)
     */
    public static float[] transmittanceLut(AtmosphereModel m, int width, int height, float[] outRgb) {
        checkTableSize(width, height);
        checkTableOut(outRgb, width, height);
        double[] rm = new double[2], t = new double[3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                transmittanceLutRMu(m, (x + 0.5) / width, (y + 0.5) / height, width, height, rm);
                transmittanceToTop(m, rm[0], rm[1], t);
                int o = 3 * (y * width + x);
                outRgb[o] = (float) t[0];
                outRgb[o + 1] = (float) t[1];
                outRgb[o + 2] = (float) t[2];
            }
        }
        return outRgb;
    }

    // ---------------------------------------------------------------- multiple scattering (Hillaire 2020)

    /** Width of the internal sun-transmittance table of {@link #multipleScatteringLut}. */
    private static final int SUN_W = 256;
    /** Height of the internal sun-transmittance table of {@link #multipleScatteringLut}. */
    private static final int SUN_H = 128;

    /** Fills {@code t[off ..]} with the double-precision sun-transmittance table, RGB interleaved. */
    private static void buildSunTable(AtmosphereModel m, double[] t, int off) {
        Scratch sc = SCRATCH.get();
        double[] rm = sc.uv, tr = sc.tSun;
        for (int y = 0; y < SUN_H; y++) {
            for (int x = 0; x < SUN_W; x++) {
                transmittanceLutRMu(m, (x + 0.5) / SUN_W, (y + 0.5) / SUN_H, SUN_W, SUN_H, rm);
                transmittanceToTop(m, rm[0], rm[1], tr);
                System.arraycopy(tr, 0, t, off + 3 * (y * SUN_W + x), 3);
            }
        }
    }

    /**
     * Sun transmittance at {@code (r, mu)}: integrated exactly when {@code t} is null, else looked up (bilinear) in
     * the table at {@code t[off ..]}.
     */
    private static void sunTransmittance(AtmosphereModel m, double[] t, int off, double r, double mu, double[] uv,
            double[] out) {
        if (t == null) {
            transmittanceToTop(m, r, mu, out);
            return;
        }
        if (intersectsGround(m, r, mu)) {
            out[0] = out[1] = out[2] = 0.0;
            return;
        }
        int w = SUN_W, h = SUN_H;
        transmittanceLutUv(m, java.lang.Math.min(r, m.topRadius()), mu, w, h, uv);
        double fx = java.lang.Math.max(0.0, java.lang.Math.min(uv[0] * w - 0.5, w - 1.0));
        double fy = java.lang.Math.max(0.0, java.lang.Math.min(uv[1] * h - 0.5, h - 1.0));
        int x0 = java.lang.Math.min((int) fx, w - 2), y0 = java.lang.Math.min((int) fy, h - 2);
        double ax = fx - x0, ay = fy - y0;
        for (int c = 0; c < 3; c++) {
            double t00 = t[off + 3 * (y0 * w + x0) + c], t10 = t[off + 3 * (y0 * w + x0 + 1) + c];
            double t01 = t[off + 3 * ((y0 + 1) * w + x0) + c], t11 = t[off + 3 * ((y0 + 1) * w + x0 + 1) + c];
            out[c] = (t00 * (1 - ax) + t10 * ax) * (1 - ay) + (t01 * (1 - ax) + t11 * ax) * ay;
        }
    }

    private static void checkCounts(int directions, int steps) {
        if (directions <= 0 || steps <= 0) {
            throw new IllegalArgumentException("directions and steps must be positive: " + directions + ", " + steps);
        }
    }

    /**
     * The multiple-scattering transfer {@code Ψ_ms} (RGB) at radius {@code r} for a sun with zenith cosine
     * {@code muSun} and unit illuminance (Hillaire 2020, eq. 10): second-order isotropic scattering {@code L₂}
     * including the Lambertian ground bounce, divided by {@code 1 − f_ms}. Integrated over {@code directions}
     * Fibonacci-sphere directions with {@code steps} ray-march steps each; sun transmittance is integrated exactly.
     * Multiply by the local scattering coefficient and the sun illuminance to get radiance. Uses a per-thread scratch
     * area, so it does not allocate after the first call on a thread.
     *
     * @param m the atmosphere
     * @param r the distance from the planet center, in metres, in [{@code bottomRadius}, {@code topRadius}]
     * @param muSun the cosine of the sun's zenith angle, in [-1, 1]
     * @param directions the number of sphere directions, positive
     * @param steps the number of ray-march steps per direction, positive
     * @param out receives the RGB transfer in elements 0 to 2
     * @return {@code out}
     * @throws IllegalArgumentException if {@code directions} or {@code steps} is not positive or {@code out} has fewer
     *         than 3 elements (checked before anything is written)
     */
    public static double[] multipleScattering(AtmosphereModel m, double r, double muSun, int directions, int steps,
            double[] out) {
        checkCounts(directions, steps);
        checkLength(out, 3, "out");
        return multipleScattering(m, r, muSun, directions, steps, null, 0, out);
    }

    private static double[] multipleScattering(AtmosphereModel m, double r, double muSun, int directions, int steps,
            double[] sunTable, int sunOff, double[] out) {
        double sx = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - muSun * muSun)), sz = muSun;
        Scratch sc = SCRATCH.get();
        double[] ext = sc.ext, sca = sc.sca, tSun = sc.tSun, uv = sc.uv;
        double l0 = 0, l1 = 0, l2 = 0, f0 = 0, f1 = 0, f2 = 0;
        double golden = java.lang.Math.PI * (3.0 - java.lang.Math.sqrt(5.0));
        for (int i = 0; i < directions; i++) {
            double wz = 1.0 - (i + 0.5) * 2.0 / directions;
            double wr = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - wz * wz));
            double phi = golden * i;
            double wx = wr * AccurateMath.cos(phi), wy = wr * AccurateMath.sin(phi);
            // the point is (0, 0, r): the ray's zenith cosine is wz
            boolean ground = intersectsGround(m, r, wz);
            double tEnd = ground ? distanceToGround(m, r, wz) : distanceToTop(m, r, wz);
            double dt = tEnd / steps;
            double tr0 = 1, tr1 = 1, tr2 = 1;
            double dl0 = 0, dl1 = 0, dl2 = 0, df0 = 0, df1 = 0, df2 = 0;
            for (int s = 0; s < steps; s++) {
                double t = (s + 0.5) * dt;
                double px = wx * t, py = wy * t, pz = r + wz * t;
                double rp = java.lang.Math.sqrt(px * px + py * py + pz * pz);
                double h = altitudeAt(m, r, wz, t);
                extinction(m, h, ext);
                scattering(m, h, sca);
                sunTransmittance(m, sunTable, sunOff, rp, (px * sx + pz * sz) / rp, uv, tSun);
                double s0 = segment(ext[0], dt), s1 = segment(ext[1], dt), s2 = segment(ext[2], dt);
                double pu = 1.0 / (4.0 * java.lang.Math.PI);
                dl0 += tr0 * sca[0] * tSun[0] * pu * s0;
                dl1 += tr1 * sca[1] * tSun[1] * pu * s1;
                dl2 += tr2 * sca[2] * tSun[2] * pu * s2;
                df0 += tr0 * sca[0] * s0;
                df1 += tr1 * sca[1] * s1;
                df2 += tr2 * sca[2] * s2;
                tr0 *= AccurateMath.exp(-ext[0] * dt);
                tr1 *= AccurateMath.exp(-ext[1] * dt);
                tr2 *= AccurateMath.exp(-ext[2] * dt);
            }
            if (ground) {
                double px = wx * tEnd, py = wy * tEnd, pz = r + wz * tEnd;
                double rp = java.lang.Math.sqrt(px * px + py * py + pz * pz);
                double cosSun = (px * sx + pz * sz) / rp;
                sunTransmittance(m, sunTable, sunOff, rp, cosSun, uv, tSun);
                double lambert = java.lang.Math.max(cosSun, 0.0) / java.lang.Math.PI;
                dl0 += tr0 * tSun[0] * lambert * m.groundAlbedo(0);
                dl1 += tr1 * tSun[1] * lambert * m.groundAlbedo(1);
                dl2 += tr2 * tSun[2] * lambert * m.groundAlbedo(2);
            }
            l0 += dl0;
            l1 += dl1;
            l2 += dl2;
            f0 += df0;
            f1 += df1;
            f2 += df2;
        }
        // uniform sphere directions: ∫ X(ω) p_u dω = (1/N) Σ X_i
        double inv = 1.0 / directions;
        out[0] = l0 * inv / (1.0 - f0 * inv);
        out[1] = l1 * inv / (1.0 - f1 * inv);
        out[2] = l2 * inv / (1.0 - f2 * inv);
        return out;
    }

    /** {@code ∫₀^dt exp(−σ t) dt = (1 − e^(−σ dt)) / σ}, stable as {@code σ dt → 0}. */
    private static double segment(double sigma, double dt) {
        double x = sigma * dt;
        if (x < 1e-6) {
            return dt * (1.0 - 0.5 * x + x * x / 6.0);
        }
        return (1.0 - AccurateMath.exp(-x)) / sigma;
    }

    /**
     * Returns the number of doubles of scratch that {@link #multipleScatteringLut} needs: room for its internal
     * 256 × 128 RGB sun-transmittance table (98 304 doubles, about 0.8 MB).
     *
     * @return the scratch size, in doubles
     * @since 3.0.0
     */
    public static int multipleScatteringLutScratchSize() {
        return 3 * SUN_W * SUN_H;
    }

    /**
     * Builds Hillaire's multiple-scattering LUT ({@code width} over {@code muSun} in [−1, 1], {@code height} over
     * altitude from ground to top; texel centers; RGB interleaved). Sun transmittance comes from an internal
     * 256 × 128 double-precision transmittance table (bilinear) instead of per-sample integration, which keeps a
     * 32 × 32 table at about a second; {@link #multipleScattering} integrates it exactly. Texel {@code (x, y)} is at
     * index {@code 3 (y width + x)} and covers {@code muSun = −1 + 2 (x + 0.5) / width} and
     * {@code r = bottomRadius + (topRadius − bottomRadius) (y + 0.5) / height}. The internal table is built (with
     * {@link #transmittanceToTop}) into the caller's {@code scratch} on every call, so the call does not allocate after
     * the first call on a thread; reuse the scratch array across calls.
     *
     * @param m the atmosphere
     * @param width the number of texels over {@code muSun}, positive
     * @param height the number of texels over altitude, positive
     * @param directions the number of sphere directions per texel, positive
     * @param steps the number of ray-march steps per direction, positive
     * @param scratch working memory; its contents are overwritten
     * @param scratchOffset the first element of {@code scratch} to use; {@link #multipleScatteringLutScratchSize()}
     *                      elements from there are used
     * @param outRgb receives the table, starting at index 0; length at least {@code 3 width height}
     * @return {@code outRgb}
     * @throws IllegalArgumentException if {@code width}, {@code height}, {@code directions} or {@code steps} is not
     *         positive, {@code scratchOffset} is negative, or {@code scratch} or {@code outRgb} is too small (checked
     *         before anything is written)
     * @since 3.0.0
     */
    public static float[] multipleScatteringLut(AtmosphereModel m, int width, int height, int directions, int steps,
            double[] scratch, int scratchOffset, float[] outRgb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be positive: " + width + " x " + height);
        }
        checkCounts(directions, steps);
        if (scratchOffset < 0 || (long) scratchOffset + multipleScatteringLutScratchSize() > scratch.length) {
            throw new IllegalArgumentException("scratch needs multipleScatteringLutScratchSize() elements from offset "
                    + scratchOffset + ": " + scratch.length);
        }
        checkTableOut(outRgb, width, height);
        buildSunTable(m, scratch, scratchOffset);
        double[] v = SCRATCH.get().depth;
        for (int y = 0; y < height; y++) {
            double r = m.bottomRadius() + (m.topRadius() - m.bottomRadius()) * (y + 0.5) / height;
            for (int x = 0; x < width; x++) {
                double muSun = -1.0 + 2.0 * (x + 0.5) / width;
                multipleScattering(m, r, muSun, directions, steps, scratch, scratchOffset, v);
                int o = 3 * (y * width + x);
                outRgb[o] = (float) v[0];
                outRgb[o + 1] = (float) v[1];
                outRgb[o + 2] = (float) v[2];
            }
        }
        return outRgb;
    }
}
