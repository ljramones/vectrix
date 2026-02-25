/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */

package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Math;
import org.vectrix.core.Matrix3f;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.vectrix.physics.InertiaTensorf;
import org.vectrix.physics.PbdConstraintsf;
import org.vectrix.physics.PdControllersf;
import org.vectrix.physics.SpringDamperf;

class PhysicsMathTest {
    @Test
    void angularErrorIdentityToRotationY() {
        Quaternionf from = new Quaternionf();
        Quaternionf to = new Quaternionf().rotationY(Math.PI_OVER_2_f);
        Vector3f error = Quaternionf.angularError(from, to, new Vector3f());
        assertEquals(0.0f, error.x, 1E-6f);
        assertEquals(Math.PI_OVER_2_f, error.y, 1E-6f);
        assertEquals(0.0f, error.z, 1E-6f);
    }

    @Test
    void inertiaTensorPrimitiveValues() {
        Vector3f sphere = InertiaTensorf.sphere(2.0f, 3.0f, new Vector3f());
        assertEquals(7.2f, sphere.x, 1E-6f);
        assertEquals(7.2f, sphere.y, 1E-6f);
        assertEquals(7.2f, sphere.z, 1E-6f);

        Vector3f box = InertiaTensorf.box(3.0f, 1.0f, 2.0f, 3.0f, new Vector3f());
        assertEquals(13.0f, box.x, 1E-6f);
        assertEquals(10.0f, box.y, 1E-6f);
        assertEquals(5.0f, box.z, 1E-6f);
    }

    @Test
    void pdTorqueAndClamp() {
        Vector3f torque = PdControllersf.torque(
                new Vector3f(2.0f, -1.0f, 0.5f), 3.0f,
                new Vector3f(1.0f, 0.0f, -1.0f), 2.0f,
                new Vector3f());
        assertEquals(4.0f, torque.x, 1E-6f);
        assertEquals(-3.0f, torque.y, 1E-6f);
        assertEquals(3.5f, torque.z, 1E-6f);

        Vector3f clamped = PdControllersf.clampTorque(new Vector3f(6.0f, 8.0f, 0.0f), 5.0f, new Vector3f());
        assertEquals(3.0f, clamped.x, 1E-6f);
        assertEquals(4.0f, clamped.y, 1E-6f);
        assertEquals(0.0f, clamped.z, 1E-6f);
    }

    @Test
    void springDamperBasics() {
        assertEquals(-8.0f, SpringDamperf.force(2.0f, 3.0f, 1.0f, 2.0f), 1E-6f);
        assertEquals(6.0f, SpringDamperf.suspensionForce(0.5f, 1.0f, 20.0f, 2.0f, 2.0f), 1E-6f);
        float[] velOut = {0.0f};
        float next = SpringDamperf.criticallyDamped(1.0f, 0.0f, 0.0f, 10.0f, 0.016f, velOut);
        assertEquals(true, next < 1.0f);
    }

    @Test
    void pbdDistanceAndVolume() {
        Vector3f da = PbdConstraintsf.distanceConstraint(
                new Vector3f(0, 0, 0), 1.0f,
                new Vector3f(2, 0, 0), 1.0f,
                1.0f, 1.0f, new Vector3f());
        assertEquals(0.5f, da.x, 1E-6f);
        assertEquals(0.0f, da.y, 1E-6f);
        assertEquals(0.0f, da.z, 1E-6f);

        Vector3f d0 = new Vector3f(), d1 = new Vector3f(), d2 = new Vector3f(), d3 = new Vector3f();
        float volume = PbdConstraintsf.volumeConstraint(
                new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1),
                1.0f / 6.0f, 1.0f, d0, d1, d2, d3);
        assertEquals(1.0f / 6.0f, volume, 1E-6f);
    }

    @Test
    void pbdShapeMatchingWritesGoal() {
        Vector3f[] current = {new Vector3f(1, 0, 0), new Vector3f(2, 0, 0)};
        Vector3f[] rest = {new Vector3f(0, 0, 0), new Vector3f(1, 0, 0)};
        float[] masses = {1.0f, 1.0f};
        Vector3f[] goals = {new Vector3f(), new Vector3f()};
        Matrix3f rot = new Matrix3f();
        PbdConstraintsf.shapeMatchingGoal(current, rest, masses, rot, goals);
        assertEquals(1.0f, goals[0].x, 1E-6f);
        assertEquals(2.0f, goals[1].x, 1E-6f);
    }
}
