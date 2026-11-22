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

import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3Lc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * A position in a large world: an integer {@link #cell} of size {@code 2^cellBits} metres and a double-precision
 * {@link #offset} from the cell's center point {@code cell · cellSize}, kept canonical in
 * {@code [−cellSize/2, cellSize/2]} per axis. Resolution is the offset's: at most
 * {@code ulp(cellSize / 2)} (5.7e-14 m for 1 km cells) everywhere, at 10⁹ m from the origin as at the origin, where a
 * plain double has 1.2e-7 m and a float 64 m.
 * <p>
 * Exactness: with power-of-two cells and the centered offset range, splitting any double into cell and offset
 * ({@link #set(double, double, double)}) and re-canonicalizing ({@link #normalize()}) are exact (the offset never
 * grows in magnitude, so it never needs more bits than the input had). {@link #sub(WorldPos, Vector3d)} forms
 * {@code Δcell · cellSize + Δoffset} and rounds once (the offset difference's own rounding error is carried along by
 * TwoSum), so relative positions are correctly rounded to within an ulp regardless of distance from the origin.
 *
 * @since 2.3.0
 */
public final class WorldPos {
    /** Cell index (exact integer arithmetic; overflow throws). */
    public final Vector3L cell = new Vector3L();
    /** Offset from {@code cell · cellSize}, canonical in {@code [−cellSize/2, cellSize/2]} after {@link #normalize()}. */
    public final Vector3d offset = new Vector3d();

    private final int cellBits;
    private final double cellSize;

    /** A position at the origin with cells of {@code 2^cellBits} metres ({@code -32 ≤ cellBits ≤ 40}). */
    public WorldPos(int cellBits) {
        if (cellBits < -32 || cellBits > 40) {
            throw new IllegalArgumentException("cellBits " + cellBits);
        }
        this.cellBits = cellBits;
        this.cellSize = java.lang.Math.scalb(1.0, cellBits);
    }

    /** Copies {@code other}, which must use the same cell size. */
    public WorldPos(WorldPos other) {
        this(other.cellBits);
        set(other);
    }

    /** log2 of the cell size. */
    public int cellBits() {
        return cellBits;
    }

    /** Cell size in metres. */
    public double cellSize() {
        return cellSize;
    }

    private void check(WorldPos other) {
        if (other.cellBits != cellBits) {
            throw new IllegalArgumentException("cell sizes differ: 2^" + cellBits + " vs 2^" + other.cellBits);
        }
    }

    public WorldPos set(WorldPos other) {
        check(other);
        cell.set(other.cell);
        offset.set(other.offset);
        return this;
    }

    /** Sets this to the double-precision position {@code (x, y, z)}, split exactly into cell and offset. */
    public WorldPos set(double x, double y, double z) {
        cell.set(0, 0, 0);
        offset.set(x, y, z);
        return normalize();
    }

    /** Sets cell and offset (any offset; it is canonicalized). */
    public WorldPos set(Vector3Lc cell, double ox, double oy, double oz) {
        this.cell.set(cell);
        offset.set(ox, oy, oz);
        return normalize();
    }

    private long split(double o, int axis) {
        // c = rint(o / 2^k) and rest = o - c 2^k, both exact: |rest| <= min(|o|, size/2) and rest is a multiple of
        // ulp(o) (or exactly 0 once ulp(o) exceeds the cell size), so it is representable
        double c = java.lang.Math.rint(java.lang.Math.scalb(o, -cellBits));
        if (!(java.lang.Math.abs(c) < 0x1.0p62)) {
            throw new ArithmeticException("offset too large to rebase: " + o);
        }
        double rest = o - java.lang.Math.scalb(c, cellBits);
        switch (axis) {
            case 0:
                offset.x = rest;
                break;
            case 1:
                offset.y = rest;
                break;
            default:
                offset.z = rest;
                break;
        }
        return (long) c;
    }

    /** Moves whole cells from the offset into the cell index, exactly; the offset ends in {@code [−size/2, size/2]}. */
    public WorldPos normalize() {
        long cx = split(offset.x, 0), cy = split(offset.y, 1), cz = split(offset.z, 2);
        cell.set(java.lang.Math.addExact(cell.x, cx), java.lang.Math.addExact(cell.y, cy),
                java.lang.Math.addExact(cell.z, cz));
        return this;
    }

    /** Moves by {@code (dx, dy, dz)} metres (one rounding, at offset resolution) and renormalizes. */
    public WorldPos add(double dx, double dy, double dz) {
        offset.add(dx, dy, dz);
        return normalize();
    }

    /** Moves by whole cells, exactly. */
    public WorldPos addCells(Vector3Lc cells) {
        cell.addExact(cells);
        return this;
    }

    private double axisDiff(long ca, long cb, double oa, double ob) {
        return CameraRelative.diff(ca, oa, cb, ob, cellBits);
    }

    /** This minus {@code other} in metres, correctly rounded to double (within an ulp) at any distance. */
    public Vector3d sub(WorldPos other, Vector3d dest) {
        check(other);
        return dest.set(axisDiff(cell.x, other.cell.x, offset.x, other.offset.x),
                axisDiff(cell.y, other.cell.y, offset.y, other.offset.y),
                axisDiff(cell.z, other.cell.z, offset.z, other.offset.z));
    }

    /**
     * This minus {@code other} rounded to single precision: the camera-relative position for rendering. Within one
     * float ulp of the exact difference, at 10⁹ m from the origin as anywhere else.
     */
    public Vector3f sub(WorldPos other, Vector3f dest) {
        check(other);
        return dest.set((float) axisDiff(cell.x, other.cell.x, offset.x, other.offset.x),
                (float) axisDiff(cell.y, other.cell.y, offset.y, other.offset.y),
                (float) axisDiff(cell.z, other.cell.z, offset.z, other.offset.z));
    }

    private double axisDiffPoint(long ca, double oa, double p) {
        double c = java.lang.Math.floor(java.lang.Math.scalb(p, -cellBits));
        if (!(java.lang.Math.abs(c) < 0x1.0p62)) {
            throw new ArithmeticException("coordinate too large: " + p);
        }
        return axisDiff(ca, (long) c, oa, p - java.lang.Math.scalb(c, cellBits));
    }

    /** This position minus the double-precision point {@code p} (split exactly into cell and offset), rounded once. */
    public Vector3d sub(double px, double py, double pz, Vector3d dest) {
        return dest.set(axisDiffPoint(cell.x, offset.x, px), axisDiffPoint(cell.y, offset.y, py),
                axisDiffPoint(cell.z, offset.z, pz));
    }

    /** The position as one double per axis ({@code cell · size + offset}, rounded once): lossy far from the origin. */
    public Vector3d toDouble(Vector3d dest) {
        return dest.set(java.lang.Math.scalb((double) cell.x, cellBits) + offset.x,
                java.lang.Math.scalb((double) cell.y, cellBits) + offset.y,
                java.lang.Math.scalb((double) cell.z, cellBits) + offset.z);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorldPos w && w.cellBits == cellBits && w.cell.equals(cell) && w.offset.equals(offset);
    }

    @Override
    public int hashCode() {
        return (31 * cellBits + cell.hashCode()) * 31 + offset.hashCode();
    }

    @Override
    public String toString() {
        return "WorldPos[cell=" + cell + " x 2^" + cellBits + ", offset=" + offset + "]";
    }
}
