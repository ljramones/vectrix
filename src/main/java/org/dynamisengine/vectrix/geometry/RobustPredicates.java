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
package org.dynamisengine.vectrix.geometry;

import java.math.BigDecimal;

/**
 * Exact geometric predicates (Shewchuk 1997): the sign of each result is always correct, also for nearly degenerate
 * input where plain floating point gives the wrong answer, which is what keeps Delaunay triangulation, convex hulls
 * and mesh booleans consistent. Each predicate first evaluates in double precision and returns that value when it
 * exceeds Shewchuk's forward error bound; otherwise it recomputes exactly with {@link BigDecimal} (every product and
 * sum of doubles is exact there). The exact path only runs for near-degenerate configurations.
 * <p>
 * Results are the determinants themselves when the fast path is taken; the exact path returns only the sign
 * ({@code -1.0}, {@code 0.0} or {@code 1.0}), so only the sign is guaranteed. Float coordinates convert to double
 * exactly. The fast path does not allocate; the exact path allocates {@link BigDecimal} temporaries.
 * <p>
 * Coordinates must be finite: a NaN or infinite coordinate can send a predicate to the exact path, where it throws
 * {@link NumberFormatException}. The class is stateless and safe to call from any thread.
 *
 * @since 2.2.0
 */
public final class RobustPredicates {
    private static final double EPS = 0x1p-53;
    private static final double CCW_BOUND = (3.0 + 16.0 * EPS) * EPS;
    private static final double O3D_BOUND = (7.0 + 56.0 * EPS) * EPS;
    private static final double ICC_BOUND = (10.0 + 96.0 * EPS) * EPS;
    private static final double ISP_BOUND = (16.0 + 224.0 * EPS) * EPS;

    private RobustPredicates() {
    }

    /**
     * Positive if {@code a, b, c} are in counter-clockwise order, negative if clockwise, zero if collinear: twice the
     * signed area {@code (a - c) × (b - c)}.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @return the orientation determinant, or only its sign when it was computed exactly
     */
    public static double orient2d(double ax, double ay, double bx, double by, double cx, double cy) {
        double detLeft = (ax - cx) * (by - cy);
        double detRight = (ay - cy) * (bx - cx);
        double det = detLeft - detRight;
        double detSum;
        if (detLeft > 0.0) {
            if (detRight <= 0.0) {
                return det;
            }
            detSum = detLeft + detRight;
        } else if (detLeft < 0.0) {
            if (detRight >= 0.0) {
                return det;
            }
            detSum = -detLeft - detRight;
        } else {
            return det;
        }
        if (java.lang.Math.abs(det) >= CCW_BOUND * detSum) {
            return det;
        }
        BigDecimal acx = d(ax).subtract(d(cx)), bcy = d(by).subtract(d(cy));
        BigDecimal acy = d(ay).subtract(d(cy)), bcx = d(bx).subtract(d(cx));
        return acx.multiply(bcy).subtract(acy.multiply(bcx)).signum();
    }

    /**
     * {@link #orient2d(double, double, double, double, double, double)} for float coordinates, which are widened to
     * double exactly.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @return the orientation determinant, or only its sign when it was computed exactly
     */
    public static double orient2d(float ax, float ay, float bx, float by, float cx, float cy) {
        return orient2d((double) ax, ay, bx, by, cx, cy);
    }

    /**
     * Positive if {@code d} lies below the plane through {@code a, b, c} (which appear counter-clockwise seen from
     * above), negative if above, zero if coplanar: the determinant of {@code a - d, b - d, c - d}.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param az the z coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param bz the z coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @param cz the z coordinate of c
     * @param dx the x coordinate of the tested point d
     * @param dy the y coordinate of the tested point d
     * @param dz the z coordinate of the tested point d
     * @return the orientation determinant, or only its sign when it was computed exactly
     */
    public static double orient3d(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy,
            double cz, double dx, double dy, double dz) {
        double adx = ax - dx, bdx = bx - dx, cdx = cx - dx;
        double ady = ay - dy, bdy = by - dy, cdy = cy - dy;
        double adz = az - dz, bdz = bz - dz, cdz = cz - dz;
        double bdxcdy = bdx * cdy, cdxbdy = cdx * bdy;
        double cdxady = cdx * ady, adxcdy = adx * cdy;
        double adxbdy = adx * bdy, bdxady = bdx * ady;
        double det = adz * (bdxcdy - cdxbdy) + bdz * (cdxady - adxcdy) + cdz * (adxbdy - bdxady);
        double permanent = (java.lang.Math.abs(bdxcdy) + java.lang.Math.abs(cdxbdy)) * java.lang.Math.abs(adz)
                + (java.lang.Math.abs(cdxady) + java.lang.Math.abs(adxcdy)) * java.lang.Math.abs(bdz)
                + (java.lang.Math.abs(adxbdy) + java.lang.Math.abs(bdxady)) * java.lang.Math.abs(cdz);
        if (java.lang.Math.abs(det) > O3D_BOUND * permanent || permanent == 0.0) {
            return det;
        }
        BigDecimal[] r = {
            d(ax).subtract(d(dx)), d(ay).subtract(d(dy)), d(az).subtract(d(dz)),
            d(bx).subtract(d(dx)), d(by).subtract(d(dy)), d(bz).subtract(d(dz)),
            d(cx).subtract(d(dx)), d(cy).subtract(d(dy)), d(cz).subtract(d(dz))};
        return det3(r).signum();
    }

    /**
     * Positive if {@code d} lies inside the circle through {@code a, b, c} (counter-clockwise), negative if outside,
     * zero if cocircular. If {@code a, b, c} are clockwise, the sign is reversed.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @param dx the x coordinate of the tested point d
     * @param dy the y coordinate of the tested point d
     * @return the incircle determinant, or only its sign when it was computed exactly
     */
    public static double incircle(double ax, double ay, double bx, double by, double cx, double cy, double dx,
            double dy) {
        double adx = ax - dx, bdx = bx - dx, cdx = cx - dx;
        double ady = ay - dy, bdy = by - dy, cdy = cy - dy;
        double bdxcdy = bdx * cdy, cdxbdy = cdx * bdy;
        double alift = adx * adx + ady * ady;
        double cdxady = cdx * ady, adxcdy = adx * cdy;
        double blift = bdx * bdx + bdy * bdy;
        double adxbdy = adx * bdy, bdxady = bdx * ady;
        double clift = cdx * cdx + cdy * cdy;
        double det = alift * (bdxcdy - cdxbdy) + blift * (cdxady - adxcdy) + clift * (adxbdy - bdxady);
        double permanent = (java.lang.Math.abs(bdxcdy) + java.lang.Math.abs(cdxbdy)) * alift
                + (java.lang.Math.abs(cdxady) + java.lang.Math.abs(adxcdy)) * blift
                + (java.lang.Math.abs(adxbdy) + java.lang.Math.abs(bdxady)) * clift;
        if (java.lang.Math.abs(det) > ICC_BOUND * permanent || permanent == 0.0) {
            return det;
        }
        BigDecimal eadx = d(ax).subtract(d(dx)), eady = d(ay).subtract(d(dy));
        BigDecimal ebdx = d(bx).subtract(d(dx)), ebdy = d(by).subtract(d(dy));
        BigDecimal ecdx = d(cx).subtract(d(dx)), ecdy = d(cy).subtract(d(dy));
        BigDecimal[] r = {eadx, eady, eadx.multiply(eadx).add(eady.multiply(eady)),
            ebdx, ebdy, ebdx.multiply(ebdx).add(ebdy.multiply(ebdy)),
            ecdx, ecdy, ecdx.multiply(ecdx).add(ecdy.multiply(ecdy))};
        return det3(r).signum();
    }

    /**
     * Positive if {@code e} lies inside the sphere through {@code a, b, c, d} (with {@code orient3d(a, b, c, d) > 0}),
     * negative if outside, zero if cospherical. If {@code orient3d(a, b, c, d) < 0}, the sign is reversed.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param az the z coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param bz the z coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @param cz the z coordinate of c
     * @param dx the x coordinate of d
     * @param dy the y coordinate of d
     * @param dz the z coordinate of d
     * @param ex the x coordinate of the tested point e
     * @param ey the y coordinate of the tested point e
     * @param ez the z coordinate of the tested point e
     * @return the insphere determinant, or only its sign when it was computed exactly
     */
    public static double insphere(double ax, double ay, double az, double bx, double by, double bz, double cx,
            double cy, double cz, double dx, double dy, double dz, double ex, double ey, double ez) {
        double aex = ax - ex, bex = bx - ex, cex = cx - ex, dex = dx - ex;
        double aey = ay - ey, bey = by - ey, cey = cy - ey, dey = dy - ey;
        double aez = az - ez, bez = bz - ez, cez = cz - ez, dez = dz - ez;
        double ab = aex * bey - bex * aey;
        double bc = bex * cey - cex * bey;
        double cd = cex * dey - dex * cey;
        double da = dex * aey - aex * dey;
        double ac = aex * cey - cex * aey;
        double bd = bex * dey - dex * bey;
        double abc = aez * bc - bez * ac + cez * ab;
        double bcd = bez * cd - cez * bd + dez * bc;
        double cda = cez * da + dez * ac + aez * cd;
        double dab = dez * ab + aez * bd + bez * da;
        double alift = aex * aex + aey * aey + aez * aez;
        double blift = bex * bex + bey * bey + bez * bez;
        double clift = cex * cex + cey * cey + cez * cez;
        double dlift = dex * dex + dey * dey + dez * dez;
        double det = (dlift * abc - clift * dab) + (blift * cda - alift * bcd);
        double aezp = java.lang.Math.abs(aez), bezp = java.lang.Math.abs(bez);
        double cezp = java.lang.Math.abs(cez), dezp = java.lang.Math.abs(dez);
        double aexbeyp = java.lang.Math.abs(aex * bey), bexaeyp = java.lang.Math.abs(bex * aey);
        double bexceyp = java.lang.Math.abs(bex * cey), cexbeyp = java.lang.Math.abs(cex * bey);
        double cexdeyp = java.lang.Math.abs(cex * dey), dexceyp = java.lang.Math.abs(dex * cey);
        double dexaeyp = java.lang.Math.abs(dex * aey), aexdeyp = java.lang.Math.abs(aex * dey);
        double aexceyp = java.lang.Math.abs(aex * cey), cexaeyp = java.lang.Math.abs(cex * aey);
        double bexdeyp = java.lang.Math.abs(bex * dey), dexbeyp = java.lang.Math.abs(dex * bey);
        double abp = aexbeyp + bexaeyp, bcp = bexceyp + cexbeyp, cdp = cexdeyp + dexceyp;
        double dap = dexaeyp + aexdeyp, acp = aexceyp + cexaeyp, bdp = bexdeyp + dexbeyp;
        double permanent = ((cdp * bezp + bdp * cezp + bcp * dezp) * alift)
                + ((dap * cezp + acp * dezp + cdp * aezp) * blift)
                + ((abp * dezp + bdp * aezp + dap * bezp) * clift)
                + ((bcp * aezp + acp * bezp + abp * cezp) * dlift);
        if (java.lang.Math.abs(det) > ISP_BOUND * permanent || permanent == 0.0) {
            return det;
        }
        BigDecimal[][] p = new BigDecimal[4][];
        double[][] pts = {{ax, ay, az}, {bx, by, bz}, {cx, cy, cz}, {dx, dy, dz}};
        for (int i = 0; i < 4; i++) {
            BigDecimal x = d(pts[i][0]).subtract(d(ex)), y = d(pts[i][1]).subtract(d(ey)), z = d(pts[i][2]).subtract(d(ez));
            p[i] = new BigDecimal[] {x, y, z, x.multiply(x).add(y.multiply(y)).add(z.multiply(z))};
        }
        // det of the 4x4 [x y z lift] by cofactor expansion along the lift column; sign as in Shewchuk's insphere
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            BigDecimal[] minor = new BigDecimal[9];
            int k = 0;
            for (int r = 0; r < 4; r++) {
                if (r == i) {
                    continue;
                }
                minor[k++] = p[r][0];
                minor[k++] = p[r][1];
                minor[k++] = p[r][2];
            }
            BigDecimal term = p[i][3].multiply(det3(minor));
            // cofactor sign for column 3 (0-based) and row i: (-1)^(i + 3)
            sum = (i % 2 == 0) ? sum.subtract(term) : sum.add(term);
        }
        return sum.signum();
    }

    /**
     * {@link #orient3d(double, double, double, double, double, double, double, double, double, double, double, double)
     * orient3d} for float coordinates, which are widened to double exactly.
     *
     * @param ax the x coordinate of a
     * @param ay the y coordinate of a
     * @param az the z coordinate of a
     * @param bx the x coordinate of b
     * @param by the y coordinate of b
     * @param bz the z coordinate of b
     * @param cx the x coordinate of c
     * @param cy the y coordinate of c
     * @param cz the z coordinate of c
     * @param dx the x coordinate of the tested point d
     * @param dy the y coordinate of the tested point d
     * @param dz the z coordinate of the tested point d
     * @return the orientation determinant, or only its sign when it was computed exactly
     */
    public static double orient3d(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
            float cz, float dx, float dy, float dz) {
        return orient3d((double) ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
    }

    private static BigDecimal d(double v) {
        return new BigDecimal(v);
    }

    private static BigDecimal det3(BigDecimal[] m) {
        return m[0].multiply(m[4].multiply(m[8]).subtract(m[5].multiply(m[7])))
                .subtract(m[1].multiply(m[3].multiply(m[8]).subtract(m[5].multiply(m[6]))))
                .add(m[2].multiply(m[3].multiply(m[7]).subtract(m[4].multiply(m[6]))));
    }
}
