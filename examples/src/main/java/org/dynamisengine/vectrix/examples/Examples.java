// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Runs an example by name: {@code java -p ... -m org.dynamisengine.vectrix.examples <name>}, or all of them. */
public final class Examples {

    static final Map<String, Consumer<String[]>> ALL = new LinkedHashMap<>();

    static {
        ALL.put("first-steps", FirstSteps::main);
        ALL.put("culling", SceneCulling::main);
        ALL.put("camera", CameraDepth::main);
        ALL.put("large-world", LargeWorld::main);
        ALL.put("animation", Animation::main);
        ALL.put("sh-lighting", ShLighting::main);
        ALL.put("signal", SignalProcessing::main);
        ALL.put("kalman", KalmanTracking::main);
        ALL.put("network", TinyNetwork::main);
    }

    private Examples() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Examples: " + String.join(", ", ALL.keySet()) + " (or 'all')");
            return;
        }
        for (Map.Entry<String, Consumer<String[]>> e : ALL.entrySet()) {
            if (args[0].equals("all") || args[0].equals(e.getKey())) {
                System.out.println("== " + e.getKey());
                e.getValue().accept(new String[0]);
            }
        }
    }
}
