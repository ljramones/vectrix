/**
 * Vectrix: math for real-time graphics, simulation, signal processing and machine learning. Every package is
 * exported; see {@code docs/package-map.md} for what each one holds and how they depend on each other.
 */
module org.dynamisengine.vectrix.render {
    requires transitive org.dynamisengine.vectrix;

    exports org.dynamisengine.vectrix.atmosphere;
    exports org.dynamisengine.vectrix.brdf;
    exports org.dynamisengine.vectrix.camera;
    exports org.dynamisengine.vectrix.color;
    exports org.dynamisengine.vectrix.gpu;
    exports org.dynamisengine.vectrix.ltc;
    exports org.dynamisengine.vectrix.optics;
    exports org.dynamisengine.vectrix.renderingmath;
    exports org.dynamisengine.vectrix.sampling;
    exports org.dynamisengine.vectrix.sh;
    exports org.dynamisengine.vectrix.spherical;
}
