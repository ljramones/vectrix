/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.dynamisengine.vectrix.bench;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.atmosphere.Atmosphere;
import org.dynamisengine.vectrix.atmosphere.AtmosphereModel;
import org.dynamisengine.vectrix.camera.ShadowCascadesf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.world.CameraRelative;
import org.dynamisengine.vectrix.world.WorldPos;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Large-world upload, cascade splits and atmosphere evaluation (2.3.0 packages). */
@State(Scope.Benchmark)
public class WorldCameraBenchmark extends LatencyBenchmark {
    @Param({"4096"})
    public int count;

    private double[] worldD;
    private long[] cells;
    private double[] offsets;
    private float[] linear, out, splits;
    private final Vector3d cameraD = new Vector3d(1.0e7, 2.5e3, -3.0e6);
    private WorldPos camera;
    private final AtmosphereModel earth = AtmosphereModel.earth();
    private final double[] rgb = new double[3];
    private double mu = 0.3;

    @Setup
    public void setup() {
        SplittableRandom r = new SplittableRandom(7);
        worldD = new double[12 * count];
        cells = new long[3 * count];
        offsets = new double[3 * count];
        linear = new float[9 * count];
        out = new float[12 * count];
        splits = new float[5];
        camera = new WorldPos(12).set(cameraD.x, cameraD.y, cameraD.z);
        WorldPos p = new WorldPos(12);
        for (int i = 0; i < count; i++) {
            double x = cameraD.x + r.nextDouble(-5e3, 5e3), y = cameraD.y + r.nextDouble(-5e2, 5e2);
            double z = cameraD.z + r.nextDouble(-5e3, 5e3);
            linear[9 * i] = linear[9 * i + 4] = linear[9 * i + 8] = 1;
            worldD[12 * i] = worldD[12 * i + 5] = worldD[12 * i + 10] = 1;
            worldD[12 * i + 3] = x;
            worldD[12 * i + 7] = y;
            worldD[12 * i + 11] = z;
            p.set(x, y, z);
            cells[3 * i] = p.cell.x;
            cells[3 * i + 1] = p.cell.y;
            cells[3 * i + 2] = p.cell.z;
            offsets[3 * i] = p.offset.x;
            offsets[3 * i + 1] = p.offset.y;
            offsets[3 * i + 2] = p.offset.z;
        }
    }

    @Benchmark
    public float[] cameraRelativeDouble() {
        return CameraRelative.toCameraRelative(worldD, count, cameraD, out);
    }

    @Benchmark
    public float[] cameraRelativeCells() {
        return CameraRelative.toCameraRelative(cells, offsets, linear, count, camera, out);
    }

    @Benchmark
    public float[] practicalSplits() {
        return ShadowCascadesf.practicalSplits(0.1f, 2000f, 4, 0.7f, splits);
    }

    @Benchmark
    public double[] transmittanceToTop() {
        mu = mu > 0.9 ? -0.05 : mu + 0.013; // vary the direction so nothing is hoisted
        return Atmosphere.transmittanceToTop(earth, earth.bottomRadius() + 1500.0, mu, rgb);
    }
}
