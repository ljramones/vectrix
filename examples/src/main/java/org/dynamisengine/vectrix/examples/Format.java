// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import java.util.Locale;

import org.dynamisengine.vectrix.core.Vector3fc;

/** Plain decimal formatting for the examples' output (the types' {@code toString} uses scientific notation). */
final class Format {

    private Format() {
    }

    static String vector(Vector3fc v, int decimals) {
        String f = "%." + decimals + "f";
        return String.format(Locale.ROOT, "(" + f + ", " + f + ", " + f + ")", v.x(), v.y(), v.z());
    }
}
