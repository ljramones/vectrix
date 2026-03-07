module org.dynamisengine.vectrix {
    exports org.dynamisengine.vectrix.core;
    exports org.dynamisengine.vectrix.affine;
    exports org.dynamisengine.vectrix.color;
    exports org.dynamisengine.vectrix.curve;
    exports org.dynamisengine.vectrix.curve.scalar;
    exports org.dynamisengine.vectrix.curve.vec2;
    exports org.dynamisengine.vectrix.curve.vec3;
    exports org.dynamisengine.vectrix.curve.vec4;
    exports org.dynamisengine.vectrix.easing;

    exports org.dynamisengine.vectrix.geometry;
    exports org.dynamisengine.vectrix.sampling;
    exports org.dynamisengine.vectrix.hash;
    exports org.dynamisengine.vectrix.sdf;

    exports org.dynamisengine.vectrix.gpu;
    exports org.dynamisengine.vectrix.renderingmath;
    exports org.dynamisengine.vectrix.optics;
    exports org.dynamisengine.vectrix.ltc;
    exports org.dynamisengine.vectrix.sh;

    exports org.dynamisengine.vectrix.fft;
    exports org.dynamisengine.vectrix.simd;
    exports org.dynamisengine.vectrix.soa;

    exports org.dynamisengine.vectrix.physics;

    exports org.dynamisengine.vectrix.experimental to
        org.dynamisphysics.ode4j,
        org.dynamisphysics.jolt,
        org.dynamislightengine;
}
