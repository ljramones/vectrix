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
package org.dynamisengine.vectrix.world;

import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaternionfc;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Camera-relative conversion of world transforms to single precision for GPU upload. Rendering far from the origin
 * in float fails because the translation is rounded before the camera is subtracted (64 m steps at 10⁹ m); here the
 * camera is subtracted first, exactly or in double precision, and the result is rounded once, so a nearby object
 * keeps sub-millimetre placement wherever the camera is. Output is the engine's {@code AFFINE_3X4_ROW_MAJOR} layout
 * (12 floats per transform, see {@link org.dynamisengine.vectrix.affine.PackedAffineArray}).
 *
 * @since 2.3.0
 */
public final class CameraRelative {
    private CameraRelative() {
    }

    /**
     * Converts {@code count} double-precision transforms in the 3×4 row-major layout ({@code in}, 12 doubles each: rows
     * {@code [r00 r01 r02 tx, r10 r11 r12 ty, r20 r21 r22 tz]}) to camera-relative floats in {@code out}: the linear
     * part is rounded to float, the translation is {@code t − camera} in double, then rounded. Transforms {@code [0,
     * count)} are converted from index 0; the arrays are not checked up front.
     *
     * @param in the world transforms, {@code 12·count} doubles
     * @param count the number of transforms
     * @param camera the camera position in world coordinates, in metres
     * @param out receives {@code 12·count} floats in the same layout
     * @return {@code out}
     */
    public static float[] toCameraRelative(double[] in, int count, Vector3dc camera, float[] out) {
        double cx = camera.x(), cy = camera.y(), cz = camera.z();
        for (int i = 0; i < count; i++) {
            int o = 12 * i;
            for (int r = 0; r < 3; r++) {
                int b = o + 4 * r;
                out[b] = (float) in[b];
                out[b + 1] = (float) in[b + 1];
                out[b + 2] = (float) in[b + 2];
            }
            out[o + 3] = (float) (in[o + 3] - cx);
            out[o + 7] = (float) (in[o + 7] - cy);
            out[o + 11] = (float) (in[o + 11] - cz);
        }
        return out;
    }

    /**
     * Converts {@code count} transforms whose positions are world cells and offsets ({@code cells}, 3 longs each;
     * {@code offsets}, 3 doubles each, in {@code camera}'s cell size) and whose linear parts are 3×3 row-major floats
     * ({@code linear}, 9 each) to camera-relative floats in {@code out}. The translation is the exact
     * {@link WorldPos#sub(WorldPos, org.dynamisengine.vectrix.core.Vector3d)} difference, rounded once: within a float
     * ulp at any distance from the origin. The offsets need not be canonical. Transforms {@code [0, count)} are
     * converted from index 0; the arrays are not checked up front.
     *
     * @param cells the cell index of each transform, {@code 3·count} longs ({@code x, y, z})
     * @param offsets the offset from each cell's center, {@code 3·count} doubles, in metres
     * @param linear the linear part of each transform, {@code 9·count} floats, row-major
     * @param count the number of transforms
     * @param camera the camera position; its cell size applies to {@code cells}
     * @param out receives {@code 12·count} floats in the packed 3×4 layout
     * @return {@code out}
     * @throws ArithmeticException if a cell difference overflows {@code long}
     */
    public static float[] toCameraRelative(long[] cells, double[] offsets, float[] linear, int count, WorldPos camera,
            float[] out) {
        int bits = camera.cellBits();
        for (int i = 0; i < count; i++) {
            int o = 12 * i, l = 9 * i, c = 3 * i;
            for (int r = 0; r < 3; r++) {
                out[o + 4 * r] = linear[l + 3 * r];
                out[o + 4 * r + 1] = linear[l + 3 * r + 1];
                out[o + 4 * r + 2] = linear[l + 3 * r + 2];
            }
            out[o + 3] = (float) diff(cells[c], offsets[c], camera.cell.x, camera.offset.x, bits);
            out[o + 7] = (float) diff(cells[c + 1], offsets[c + 1], camera.cell.y, camera.offset.y, bits);
            out[o + 11] = (float) diff(cells[c + 2], offsets[c + 2], camera.cell.z, camera.offset.z, bits);
        }
        return out;
    }

    /** {@code (ca − cb) 2^bits + (oa − ob)}, rounded once (TwoSum keeps the offset difference's error). */
    static double diff(long ca, double oa, long cb, double ob, int bits) {
        long dc = java.lang.Math.subtractExact(ca, cb);
        double big = java.lang.Math.scalb((double) dc, bits);
        double s = oa - ob;
        double bb = s - oa;
        double small = (oa - (s - bb)) + (-ob - bb);
        double hi = big + s;
        double bb2 = hi - big;
        double lo = (big - (hi - bb2)) + (s - bb2);
        return hi + (lo + small);
    }

    /**
     * Returns the camera-relative model matrix of one object: non-uniform scale, then rotation, at world position
     * {@code position}, relative to {@code camera} ({@code dest = T(position − camera) R S}).
     *
     * @param position the object's world position
     * @param rotation the object's rotation, a unit quaternion
     * @param scale the object's scale
     * @param camera the camera position, with the same cell size as {@code position}
     * @param dest receives the matrix
     * @return {@code dest}
     * @throws IllegalArgumentException if the cell sizes differ
     * @throws ArithmeticException if the cell difference overflows {@code long}
     */
    public static Matrix4x3f modelMatrix(WorldPos position, Quaternionfc rotation, Vector3fc scale, WorldPos camera,
            Matrix4x3f dest) {
        int bits = camera.cellBits();
        if (position.cellBits() != bits) {
            throw new IllegalArgumentException("cell sizes differ");
        }
        float tx = (float) diff(position.cell.x, position.offset.x, camera.cell.x, camera.offset.x, bits);
        float ty = (float) diff(position.cell.y, position.offset.y, camera.cell.y, camera.offset.y, bits);
        float tz = (float) diff(position.cell.z, position.offset.z, camera.cell.z, camera.offset.z, bits);
        return dest.translationRotateScale(tx, ty, tz, rotation.x(), rotation.y(), rotation.z(), rotation.w(),
                scale.x(), scale.y(), scale.z());
    }
}
