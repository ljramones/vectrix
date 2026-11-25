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
 * {@code ulp(cellSize / 2)} (1.1e-13 m for 1024 m cells) everywhere, at 10⁹ m from the origin as at the origin, where a
 * plain double has 1.2e-7 m and a float 64 m.
 * <p>
 * Exactness: with power-of-two cells and the centered offset range, splitting any double into cell and offset
 * ({@link #set(double, double, double)}) and re-canonicalizing ({@link #normalize()}) are exact (the offset never
 * grows in magnitude, so it never needs more bits than the input had). {@link #sub(WorldPos, Vector3d)} forms
 * {@code Δcell · cellSize + Δoffset} and rounds once (the offset difference's own rounding error is carried along by
 * TwoSum), so relative positions are correctly rounded to within an ulp regardless of distance from the origin.
 * <p>
 * Cell arithmetic is exact: a cell index that would overflow {@code long}, or an offset too large or not finite to
 * rebase, throws {@link ArithmeticException}. Positions with different cell sizes cannot be combined
 * ({@link IllegalArgumentException}). A {@code WorldPos} is mutable and not thread-safe.
 *
 * @since 2.3.0
 */
public final class WorldPos {
    /** Cell index along each axis (exact integer arithmetic; overflow throws). */
    public final Vector3L cell = new Vector3L();
    /**
     * Offset from {@code cell · cellSize} in metres, canonical in {@code [−cellSize/2, cellSize/2]} after
     * {@link #normalize()}.
     */
    public final Vector3d offset = new Vector3d();

    private final int cellBits;
    private final double cellSize;

    /**
     * Creates a position at the origin with cells of {@code 2^cellBits} metres.
     *
     * @param cellBits log2 of the cell size in metres, in {@code [-32, 40]}
     * @throws IllegalArgumentException if {@code cellBits} is out of range
     */
    public WorldPos(int cellBits) {
        if (cellBits < -32 || cellBits > 40) {
            throw new IllegalArgumentException("cellBits " + cellBits);
        }
        this.cellBits = cellBits;
        this.cellSize = java.lang.Math.scalb(1.0, cellBits);
    }

    /**
     * Creates a copy of {@code other}, with the same cell size.
     *
     * @param other the position to copy
     */
    public WorldPos(WorldPos other) {
        this(other.cellBits);
        set(other);
    }

    /**
     * Returns log2 of the cell size.
     *
     * @return the cell size exponent, in {@code [-32, 40]}
     */
    public int cellBits() {
        return cellBits;
    }

    /**
     * Returns the cell size.
     *
     * @return {@code 2^cellBits}, in metres
     */
    public double cellSize() {
        return cellSize;
    }

    private void check(WorldPos other) {
        if (other.cellBits != cellBits) {
            throw new IllegalArgumentException("cell sizes differ: 2^" + cellBits + " vs 2^" + other.cellBits);
        }
    }

    /**
     * Copies the cell and offset of {@code other}.
     *
     * @param other the position to copy, with the same cell size
     * @return this
     * @throws IllegalArgumentException if the cell sizes differ
     */
    public WorldPos set(WorldPos other) {
        check(other);
        cell.set(other.cell);
        offset.set(other.offset);
        return this;
    }

    /**
     * Sets this to the double-precision position {@code (x, y, z)}, split exactly into cell and offset.
     *
     * @param x the x coordinate in metres
     * @param y the y coordinate in metres
     * @param z the z coordinate in metres
     * @return this
     * @throws ArithmeticException if a coordinate is not finite (see {@link #normalize()}); this is then unchanged
     */
    public WorldPos set(double x, double y, double z) {
        return rebase(0L, 0L, 0L, x, y, z);
    }

    /**
     * Sets the cell and the offset. The offset may be any value; it is canonicalized with {@link #normalize()}.
     *
     * @param cell the cell index
     * @param ox the x offset from the cell's center, in metres
     * @param oy the y offset from the cell's center, in metres
     * @param oz the z offset from the cell's center, in metres
     * @return this
     * @throws ArithmeticException if the offset cannot be rebased (see {@link #normalize()}); this is then unchanged
     */
    public WorldPos set(Vector3Lc cell, double ox, double oy, double oz) {
        return rebase(cell.x(), cell.y(), cell.z(), ox, oy, oz);
    }

    /** Whole cells in {@code o}: {@code rint(o / 2^cellBits)}, checked to fit a {@code long} with room to spare. */
    private long cellsIn(double o) {
        double c = java.lang.Math.rint(java.lang.Math.scalb(o, -cellBits));
        if (!(java.lang.Math.abs(c) < 0x1.0p62)) {
            throw new ArithmeticException("offset too large to rebase: " + o);
        }
        return (long) c;
    }

    /** The offset left after moving {@code c} whole cells out of {@code o}. */
    private double rest(double o, long c) {
        // c = rint(o / 2^k) and rest = o - c 2^k, both exact: |rest| <= min(|o|, size/2) and rest is a multiple of
        // ulp(o) (or exactly 0 once ulp(o) exceeds the cell size), so it is representable
        return o - java.lang.Math.scalb((double) c, cellBits);
    }

    /**
     * Sets this to cell {@code (cx, cy, cz)} plus offset {@code (ox, oy, oz)}, canonicalized. Every axis is computed
     * before anything is written, so a throw leaves this unchanged.
     */
    private WorldPos rebase(long cx, long cy, long cz, double ox, double oy, double oz) {
        long dx = cellsIn(ox);
        long dy = cellsIn(oy);
        long dz = cellsIn(oz);
        long nx = java.lang.Math.addExact(cx, dx);
        long ny = java.lang.Math.addExact(cy, dy);
        long nz = java.lang.Math.addExact(cz, dz);
        double rx = rest(ox, dx);
        double ry = rest(oy, dy);
        double rz = rest(oz, dz);
        cell.set(nx, ny, nz);
        offset.set(rx, ry, rz);
        return this;
    }

    /**
     * Moves whole cells from the offset into the cell index, exactly; the offset ends in {@code [-size/2, size/2]}.
     * All three axes are computed before anything is written, so if it throws, this is unchanged.
     *
     * @return this
     * @throws ArithmeticException if an offset is not finite or is {@code 2^62} cells or more, or a cell index
     *     overflows
     */
    public WorldPos normalize() {
        return rebase(cell.x, cell.y, cell.z, offset.x, offset.y, offset.z);
    }

    /**
     * Moves by {@code (dx, dy, dz)} metres (one rounding, at offset resolution) and renormalizes.
     *
     * @param dx the x displacement in metres
     * @param dy the y displacement in metres
     * @param dz the z displacement in metres
     * @return this
     * @throws ArithmeticException if the result cannot be rebased (see {@link #normalize()}); this is then unchanged
     */
    public WorldPos add(double dx, double dy, double dz) {
        return rebase(cell.x, cell.y, cell.z, offset.x + dx, offset.y + dy, offset.z + dz);
    }

    /**
     * Moves by whole cells, exactly.
     *
     * @param cells the number of cells to move along each axis
     * @return this
     * @throws ArithmeticException if a cell index overflows; this is then unchanged
     */
    public WorldPos addCells(Vector3Lc cells) {
        cell.addExact(cells);
        return this;
    }

    private double axisDiff(long ca, long cb, double oa, double ob) {
        return CameraRelative.diff(ca, oa, cb, ob, cellBits);
    }

    /**
     * Returns this minus {@code other} in metres, correctly rounded to double (within an ulp) at any distance.
     *
     * @param other the position to subtract, with the same cell size
     * @param dest receives the difference
     * @return {@code dest}
     * @throws IllegalArgumentException if the cell sizes differ
     * @throws ArithmeticException if the cell difference overflows {@code long}
     */
    public Vector3d sub(WorldPos other, Vector3d dest) {
        check(other);
        return dest.set(axisDiff(cell.x, other.cell.x, offset.x, other.offset.x),
                axisDiff(cell.y, other.cell.y, offset.y, other.offset.y),
                axisDiff(cell.z, other.cell.z, offset.z, other.offset.z));
    }

    /**
     * Returns this minus {@code other} rounded to single precision: the camera-relative position for rendering. Within
     * one float ulp of the exact difference, at 10⁹ m from the origin as anywhere else.
     *
     * @param other the position to subtract, usually the camera, with the same cell size
     * @param dest receives the difference in metres
     * @return {@code dest}
     * @throws IllegalArgumentException if the cell sizes differ
     * @throws ArithmeticException if the cell difference overflows {@code long}
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

    /**
     * Returns this position minus the double-precision point {@code p} (split exactly into cell and offset), rounded
     * once.
     *
     * @param px the x coordinate of the point in metres
     * @param py the y coordinate of the point in metres
     * @param pz the z coordinate of the point in metres
     * @param dest receives the difference in metres
     * @return {@code dest}
     * @throws ArithmeticException if a coordinate is not finite or too large, or the cell difference overflows
     */
    public Vector3d sub(double px, double py, double pz, Vector3d dest) {
        return dest.set(axisDiffPoint(cell.x, offset.x, px), axisDiffPoint(cell.y, offset.y, py),
                axisDiffPoint(cell.z, offset.z, pz));
    }

    /**
     * Returns the position as one double per axis ({@code cell · size + offset}, rounded once): lossy far from the
     * origin.
     *
     * @param dest receives the position in metres
     * @return {@code dest}
     */
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
