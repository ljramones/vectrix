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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.affine.DualQuatTransformd;
import org.dynamisengine.vectrix.affine.RigidTransformd;
import org.dynamisengine.vectrix.affine.Transformd;
import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector2L;
import org.dynamisengine.vectrix.core.Vector3L;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4L;
import org.dynamisengine.vectrix.soa.TransformSoAd;
import org.dynamisengine.vectrix.world.CameraRelative;
import org.dynamisengine.vectrix.world.WorldPos;
import org.junit.jupiter.api.Test;

/** Large-world precision: WorldPos against BigDecimal at 1e6 to 1e9 m, long-vector arithmetic, batch kernels. */
class LargeWorldTest {

    private static final int BITS = 10; // 1024 m cells
    private static final BigDecimal SIZE = BigDecimal.valueOf(1024);

    /** An exactly represented WorldPos for the decimal position {@code p} (offset rounded to double once). */
    private static WorldPos exact(BigDecimal x, BigDecimal y, BigDecimal z) {
        BigDecimal[] in = {x, y, z};
        long[] cell = new long[3];
        double[] off = new double[3];
        for (int i = 0; i < 3; i++) {
            BigDecimal c = in[i].divide(SIZE, 0, RoundingMode.HALF_EVEN);
            cell[i] = c.longValueExact();
            off[i] = in[i].subtract(c.multiply(SIZE)).doubleValue();
        }
        return new WorldPos(BITS).set(new Vector3L(cell[0], cell[1], cell[2]), off[0], off[1], off[2]);
    }

    @Test
    void relativePositionsStayExactFarFromTheOrigin() {
        SplittableRandom rnd = new SplittableRandom(1);
        for (double distance : new double[] {1e6, 1e7, 1e8, 1e9}) {
            double worstFloat = 0, worstNaiveFloat = 0, worstNaiveDouble = 0, worstDouble = 0;
            for (int i = 0; i < 2000; i++) {
                BigDecimal base = new BigDecimal(distance).add(new BigDecimal(rnd.nextDouble(-5000, 5000)));
                BigDecimal px = base.add(new BigDecimal("0." + (100000000L + rnd.nextLong(899999999L))));
                BigDecimal qx = base.add(new BigDecimal(rnd.nextDouble(-200, 200))).add(new BigDecimal("0.000123456789"));
                BigDecimal py = new BigDecimal(-distance).add(new BigDecimal(rnd.nextDouble(-50, 50)));
                BigDecimal qy = py.add(new BigDecimal(rnd.nextDouble(-3, 3)));
                WorldPos p = exact(px, py, BigDecimal.ONE), q = exact(qx, qy, BigDecimal.TEN);
                double exactDx = px.subtract(qx).doubleValue(), exactDy = py.subtract(qy).doubleValue();
                Vector3f rel = p.sub(q, new Vector3f());
                Vector3d reld = p.sub(q, new Vector3d());
                float fx = (float) exactDx, fy = (float) exactDy;
                assertTrue(Math.abs(rel.x - exactDx) <= Math.ulp(fx), distance + ": x " + rel.x + " vs " + exactDx);
                assertTrue(Math.abs(rel.y - exactDy) <= Math.ulp(fy), distance + ": y");
                assertEquals(-9f, rel.z, 0f);
                worstFloat = Math.max(worstFloat, Math.abs(rel.x - exactDx) / Math.ulp(fx));
                worstDouble = Math.max(worstDouble, Math.abs(reld.x - exactDx));
                worstNaiveFloat = Math.max(worstNaiveFloat, Math.abs((px.floatValue() - qx.floatValue()) - exactDx));
                worstNaiveDouble = Math.max(worstNaiveDouble, Math.abs((px.doubleValue() - qx.doubleValue()) - exactDx));
            }
            System.out.printf("%.0e m: WorldPos float %.2f ulp, double %.1e m; naive float %.1e m, naive double %.1e m%n",
                    distance, worstFloat, worstDouble, worstNaiveFloat, worstNaiveDouble);
            assertTrue(worstDouble < 1e-12, "double relative error " + worstDouble);
            assertTrue(worstDouble < worstNaiveDouble || distance < 1e7);
        }
    }

    @Test
    void exactSplitRebaseAndCells() {
        SplittableRandom rnd = new SplittableRandom(2);
        Vector3d back = new Vector3d();
        for (int i = 0; i < 10000; i++) {
            double x = rnd.nextDouble(-1e12, 1e12), y = rnd.nextDouble(-1, 1), z = Math.scalb(rnd.nextDouble(), 40);
            WorldPos p = new WorldPos(BITS).set(x, y, z);
            assertTrue(Math.abs(p.offset.x) <= 512 && Math.abs(p.offset.y) <= 512 && Math.abs(p.offset.z) <= 512);
            assertEquals(new Vector3d(x, y, z), p.toDouble(back), "split is exact");
            assertEquals((long) Math.rint(x / 1024), p.cell.x);
            // moving by whole cells changes the difference by exactly that many cells
            WorldPos q = new WorldPos(p).addCells(new Vector3L(-3, 7, 0));
            assertEquals(new Vector3d(3 * 1024, -7 * 1024, 0), p.sub(q, new Vector3d()));
            // add then subtract the same metres: the offset is back to within an ulp of the cell size
            WorldPos r = new WorldPos(p).add(1234.5, -0.25, 3e6).add(-1234.5, 0.25, -3e6);
            assertTrue(r.sub(p, new Vector3d()).length() <= 3 * Math.ulp(1024.0));
        }
        assertThrows(IllegalArgumentException.class, () -> new WorldPos(10).sub(new WorldPos(11), new Vector3d()));
        assertEquals(new Vector3d(-1.5, 0, 2048), new WorldPos(BITS).sub(1.5, 0, -2048, new Vector3d()));
    }

    @Test
    void longVectorOverflowPolicyAndFloorDivision() {
        assertEquals(new Vector3L(-1, -1, 0), new Vector3L(-1, -4, 3).floorDiv(4));
        assertEquals(new Vector3L(3, 0, 3), new Vector3L(-1, -4, 3).floorMod(4));
        assertEquals(new Vector3L(0, -1, 0), new Vector3L(-1, -4, 3).div(4)); // truncating, by contrast
        assertEquals(new Vector2L(-2, 1), new Vector2L(-5, 7).floorDiv(4));
        assertEquals(new Vector4L(3, 3, 0, 1), new Vector4L(-5, -1, 4, 9).floorMod(4));
        assertEquals(Long.MIN_VALUE, new Vector3L(Long.MAX_VALUE, 0, 0).add(new Vector3L(1, 0, 0)).x, "add wraps");
        assertThrows(ArithmeticException.class, () -> new Vector3L(Long.MAX_VALUE, 0, 0).addExact(new Vector3L(1, 0, 0)));
        assertThrows(ArithmeticException.class, () -> new Vector2L(Long.MIN_VALUE, 0).subExact(new Vector2L(1, 0)));
        assertThrows(ArithmeticException.class, () -> new Vector4L(1L << 62, 0, 0, 0).mulExact(2));
        assertEquals(new Vector3L(6, -9, 12), new Vector3L(2, -3, 4).mulExact(3, new Vector3L()));
        assertThrows(ArithmeticException.class, () -> new WorldPos(BITS).set(new Vector3L(Long.MAX_VALUE, 0, 0), 2048,
                0, 0));
    }

    @Test
    void cameraRelativeKernels() {
        SplittableRandom rnd = new SplittableRandom(3);
        int n = 64;
        double[] in = new double[12 * n];
        long[] cells = new long[3 * n];
        double[] offs = new double[3 * n];
        float[] lin = new float[9 * n];
        WorldPos cam = new WorldPos(BITS).set(3.7e8, -1.2e9, 4.4e7);
        WorldPos[] pos = new WorldPos[n];
        for (int i = 0; i < n; i++) {
            pos[i] = new WorldPos(cam).add(rnd.nextDouble(-500, 500), rnd.nextDouble(-500, 500), rnd.nextDouble(-500, 500));
            Vector3d t = pos[i].toDouble(new Vector3d());
            for (int k = 0; k < 9; k++) {
                lin[9 * i + k] = (float) rnd.nextDouble(-1, 1);
            }
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    in[12 * i + 4 * r + c] = lin[9 * i + 3 * r + c];
                }
            }
            in[12 * i + 3] = t.x;
            in[12 * i + 7] = t.y;
            in[12 * i + 11] = t.z;
            cells[3 * i] = pos[i].cell.x;
            cells[3 * i + 1] = pos[i].cell.y;
            cells[3 * i + 2] = pos[i].cell.z;
            offs[3 * i] = pos[i].offset.x;
            offs[3 * i + 1] = pos[i].offset.y;
            offs[3 * i + 2] = pos[i].offset.z;
        }
        float[] fromDouble = CameraRelative.toCameraRelative(in, n, cam.toDouble(new Vector3d()), new float[12 * n]);
        float[] fromCells = CameraRelative.toCameraRelative(cells, offs, lin, n, cam, new float[12 * n]);
        Vector3f rel = new Vector3f();
        for (int i = 0; i < n; i++) {
            pos[i].sub(cam, rel);
            assertEquals(rel.x, fromCells[12 * i + 3], 0f);
            assertEquals(rel.y, fromCells[12 * i + 7], 0f);
            assertEquals(rel.z, fromCells[12 * i + 11], 0f);
            // the double path rounds the positions to double first (6e-8 m here): at most one float ulp apart
            assertEquals(rel.x, fromDouble[12 * i + 3], Math.ulp(rel.x));
            assertEquals(lin[9 * i + 5], fromCells[12 * i + 6], 0f);
        }
        Matrix4x3f m = CameraRelative.modelMatrix(pos[0], new Quaternionf().rotateY(0.3f), new Vector3f(2, 2, 2), cam,
                new Matrix4x3f());
        assertTrue(m.transformPosition(new Vector3f()).distance(pos[0].sub(cam, new Vector3f())) == 0f);
        // naive float: the same object placed through float world coordinates
        Vector3d world = pos[0].toDouble(new Vector3d());
        Vector3d camD = cam.toDouble(new Vector3d());
        float naive = (float) world.x - (float) camD.x;
        System.out.printf("object at %.1f m from the camera, 1.2e9 m from the origin: naive float x %.3f, "
                + "camera-relative %.6f%n", rel.length(), naive, pos[0].sub(cam, new Vector3f()).x);
    }

    @Test
    void doubleAffineTwins() {
        Transformd parent = new Transformd(new Vector3d(1e9, 2, -3), new Quaterniond().rotateZ(0.4),
                new Vector3d(2, 2, 2));
        Transformd child = new Transformd(new Vector3d(0.125, 0, 0), new Quaterniond(), new Vector3d(1, 1, 1));
        Transformd world = Transformd.compose(parent, child, new Transformd());
        // child offset 0.125 scaled by 2 and rotated: survives at 1e9 m in double (a float would round to 64 m)
        assertEquals(1e9 + 0.25 * Math.cos(0.4), world.translation.x, 1e-6);
        Transformf pf = new Transformf(new Vector3f(1e9f, 2, -3), new Quaternionf().rotateZ(0.4f), new Vector3f(2, 2, 2));
        Transformf wf = Transformf.compose(pf, new Transformf(new Vector3f(0.125f, 0, 0), new Quaternionf(),
                new Vector3f(1, 1, 1)), new Transformf());
        assertEquals(1e9f, wf.translation.x, 0f); // the float version loses the child offset entirely
        RigidTransformd rigid = new RigidTransformd();
        rigid.rotation.set(0, 0, 2, 0);
        rigid.translation.set(1e9, 0.5, 0);
        DualQuatTransformd dq = new DualQuatTransformd().setFromRigid(rigid);
        assertTrue(dq.getTranslation(new Vector3d()).distance(1e9, 0.5, 0) < 1e-6);
        TransformSoAd soa = new TransformSoAd(4);
        soa.set(2, world);
        assertEquals(world.translation.x, soa.get(2, new Transformd()).translation.x, 0.0);
    }
}
