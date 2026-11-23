// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.junit.jupiter.api.Test;

/** Each example's results checked against an independent computation, so an example cannot silently go wrong. */
class ExamplesTest {

    @Test
    void firstSteps() {
        FirstSteps.Result r = FirstSteps.run();
        assertTrue(r.moved().equals(new Vector3f(10, 0, -2), 1e-5f), r.moved().toString());
        assertTrue(r.direction().equals(new Vector3f(-1, 0, 0), 1e-6f), r.direction().toString());
        assertEquals(60f, r.angleDegrees(), 1e-3f);
        assertTrue(r.strictRejectedBadInput());
    }

    @Test
    void sceneCullingAgreesWithFrustumIntersection() {
        SceneCulling.Result r = SceneCulling.run();
        Matrix4f viewProj = Projectionsf.reverseZInfinite((float) Math.toRadians(60), 16f / 9f, 0.1f, new Matrix4f())
            .mul(new Matrix4f().lookAt(0, 2, 0, 0, 2, -1, 0, 1, 0));
        FrustumIntersection fi = new FrustumIntersection().set(viewProj, false, DepthRange.REVERSED_ZERO_TO_ONE);
        int visible = 0;
        for (int i = 0; i < r.count(); i++) {
            boolean in = fi.testAab(r.worldMin()[3 * i], r.worldMin()[3 * i + 1], r.worldMin()[3 * i + 2],
                r.worldMax()[3 * i], r.worldMax()[3 * i + 1], r.worldMax()[3 * i + 2]);
            assertEquals(in, r.classification()[i] != FrustumIntersection.OUTSIDE, "object " + i);
            visible += in ? 1 : 0;
        }
        assertEquals(visible, r.visible());
        assertTrue(r.visible() > 0 && r.visible() < r.count(), "some objects in view, some not: " + r.visible());
    }

    @Test
    void onlyReverseZResolvesACentimetreAtFiveKilometres() {
        CameraDepth.Result r = CameraDepth.run();
        for (CameraDepth.Precision p : r.precision()) {
            assertEquals(p.range() == DepthRange.REVERSED_ZERO_TO_ONE, p.resolvesOneCentimetre(), p.range().name());
        }
        CameraDepth.Precision rev = r.precision()[DepthRange.REVERSED_ZERO_TO_ONE.ordinal()];
        assertEquals(CameraDepth.DISTANCE, rev.roundTripDistance(), 0.01f);
        float[] s = r.cascadeSplits();
        assertEquals(CameraDepth.NEAR, s[0], 0f);
        assertEquals(200f, s[4], 0f);
        for (int i = 1; i < s.length; i++) {
            assertTrue(s[i] > s[i - 1]);
        }
    }

    @Test
    void largeWorldIsExactWherePlainTypesAreNot() {
        LargeWorld.Result r = LargeWorld.run();
        assertEquals(new Vector3f(0.123456789f, 1.5f, -3.25f), r.exact());
        assertFalse(r.viaDouble().x == 0.123456789f, "doubles lose digits at 1e9 m");
        assertEquals(0f, r.viaFloat().x, "floats lose the whole offset at 1e9 m");
        Vector3f t = r.model().getTranslation(new Vector3f());
        assertEquals(r.exact(), t);
    }

    @Test
    void everyExampleRunsFromTheCommandLine() {
        PrintStream out = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf));
        try {
            Examples.main(new String[] {"all"});
        } finally {
            System.setOut(out);
        }
        String text = buf.toString();
        for (String name : Examples.ALL.keySet()) {
            assertTrue(text.contains("== " + name), name);
        }
    }

    @Test
    void animationReachesTheTargetAndSkinsTheWristOntoIt() {
        Animation.Result r = Animation.run();
        assertEquals(0f, r.missedBy(), 1e-6f);
        assertTrue(r.wrist().equals(Animation.TARGET, 1e-5f), r.wrist().toString());
        assertEquals(1f, r.elbow().length(), 1e-5f, "upper arm keeps its length");
        assertEquals(1f, r.wrist().distance(r.elbow()), 1e-5f, "forearm keeps its length");
        int last = Animation.VERTICES - 1;
        assertTrue(new Vector3f(r.x()[last], r.y()[last], r.z()[last]).equals(r.wrist(), 1e-5f), "skinned wrist");
        assertTrue(new Vector3f(r.x()[0], r.y()[0], r.z()[0]).equals(new Vector3f(), 1e-6f), "skinned shoulder");
    }

    @Test
    void shIrradianceMatchesTheIntegral() {
        ShLighting.Result r = ShLighting.run();
        float[][] normals = {{0, 1, 0}, {1, 0, 0}, {0, -1, 0}};
        float[][] sh = {r.up(), r.side(), r.down()};
        for (int k = 0; k < 3; k++) {
            double[] exact = irradiance(normals[k]);
            for (int c = 0; c < 3; c++) {
                assertEquals(exact[c], sh[k][c], 0.03 * exact[c] + 0.01, "normal " + k + " channel " + c);
            }
        }
    }

    /** E(n) = ∫ L(ω) max(n·ω, 0) dω by a fine midpoint rule. */
    private static double[] irradiance(float[] n) {
        double[] e = new double[3];
        float[] rgb = new float[3];
        int rows = 400;
        int cols = 800;
        for (int i = 0; i < rows; i++) {
            double t0 = Math.PI * i / rows;
            double t1 = Math.PI * (i + 1) / rows;
            double t = 0.5 * (t0 + t1);
            double dOmega = (Math.cos(t0) - Math.cos(t1)) * 2 * Math.PI / cols;
            for (int j = 0; j < cols; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / cols;
                double x = Math.sin(t) * Math.cos(phi);
                double y = Math.cos(t);
                double z = Math.sin(t) * Math.sin(phi);
                double cos = Math.max(0, n[0] * x + n[1] * y + n[2] * z);
                ShLighting.sky((float) x, (float) y, (float) z, rgb);
                for (int c = 0; c < 3; c++) {
                    e[c] += rgb[c] * cos * dOmega;
                }
            }
        }
        return e;
    }

    @Test
    void filtersRemoveTheUpperToneAndKeepTheLowerOne() {
        SignalProcessing.Result r = SignalProcessing.run();
        assertEquals(50, r.peakBinHz());
        // sin of amplitude 1 has mean power 1/2 (-3 dB); amplitude 0.5 has 1/8 (-9 dB)
        assertEquals(-3.0, r.tone50Db(), 0.3);
        assertEquals(-9.0, r.tone120Db(), 0.3);
        assertEquals(r.tone50Db(), r.fir50Db(), 0.1, "FIR pass band is flat");
        assertEquals(r.tone50Db(), r.iir50Db(), 0.2, "Butterworth pass band is flat");
        assertTrue(r.tone120Db() - r.fir120Db() > 60, "FIR stop band: at least 60 dB down");
        // digital 6th-order Butterworth (bilinear transform): |H(f)|² = 1 / (1 + (tan(πf) / tan(πf0))^12)
        double ratio = Math.tan(Math.PI * 0.120) / Math.tan(Math.PI * 0.080);
        assertEquals(10 * Math.log10(1 + Math.pow(ratio, 12)), r.tone120Db() - r.iir120Db(), 0.3);
    }

    @Test
    void kalmanBeatsTheRawMeasurementsAndRejectsTheOutlier() {
        KalmanTracking.Result r = KalmanTracking.run();
        assertEquals(KalmanTracking.NOISE, r.measurementRms(), 0.06, "measurement noise as generated");
        assertTrue(r.filterRms() < r.measurementRms() / 3, "filter error " + r.filterRms());
        assertEquals(KalmanTracking.TRUE_VELOCITY, r.velocity(), 0.1);
        assertEquals(1, r.rejected(), "only the wild measurement is gated out");
    }

    @Test
    void networkLearnsTheCircle() {
        TinyNetwork.Result r = TinyNetwork.run();
        assertTrue(r.lastLoss() < r.firstLoss() / 5, r.firstLoss() + " -> " + r.lastLoss());
        assertTrue(r.testAccuracy() > 0.95f, "accuracy " + r.testAccuracy());
    }
}
