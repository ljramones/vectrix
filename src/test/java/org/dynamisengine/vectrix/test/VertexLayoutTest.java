/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.dynamisengine.vectrix.gpu.ShaderLayout;
import org.dynamisengine.vectrix.gpu.ShaderLayout.Block;
import org.dynamisengine.vectrix.gpu.ShaderLayout.Rules;
import org.dynamisengine.vectrix.gpu.VertexAttribute;
import org.dynamisengine.vectrix.gpu.VertexAttributeFormat;
import org.dynamisengine.vectrix.gpu.VertexLayout;

class VertexLayoutTest {
    @Test
    void validVertexLayout() {
        VertexLayout layout = VertexLayout.ofInterleaved(32,
                new VertexAttribute("position", 3, VertexAttributeFormat.FLOAT32, 0),
                new VertexAttribute("normal", 3, VertexAttributeFormat.SNORM16, 12),
                new VertexAttribute("uv", 2, VertexAttributeFormat.FLOAT16, 20),
                new VertexAttribute("color", 4, VertexAttributeFormat.UNORM8, 24));
        assertNotNull(layout.attribute("position"));
        assertEquals(32, layout.strideBytes);
    }

    @Test
    void rejectsOverlap() {
        assertThrows(IllegalArgumentException.class, () -> VertexLayout.ofInterleaved(16,
                new VertexAttribute("a", 4, VertexAttributeFormat.FLOAT32, 0),
                new VertexAttribute("b", 2, VertexAttributeFormat.FLOAT32, 8)));
    }

    @Test
    void rejectsMisalignment() {
        assertThrows(IllegalArgumentException.class, () -> VertexLayout.ofInterleaved(16,
                new VertexAttribute("a", 3, VertexAttributeFormat.FLOAT32, 2)));
    }

    @Test
    void rejectsNullAttributesAndUnalignedStride() {
        VertexAttribute pos = new VertexAttribute("position", 3, VertexAttributeFormat.FLOAT32, 0);
        // a null attribute was a NullPointerException
        assertThrows(IllegalArgumentException.class, () -> VertexLayout.ofInterleaved(16, pos, null));
        assertThrows(IllegalArgumentException.class, () -> new VertexLayout(16, Arrays.asList(pos, null)));
        assertThrows(IllegalArgumentException.class, () -> VertexLayout.ofInterleaved(16, (VertexAttribute[]) null));
        // a 14-byte stride puts the floats of the second vertex at byte 14: misaligned
        assertThrows(IllegalArgumentException.class, () -> VertexLayout.ofInterleaved(14, pos));
        assertEquals(12, VertexLayout.ofInterleaved(12, pos).strideBytes);
        VertexAttribute bytes = new VertexAttribute("color", 3, VertexAttributeFormat.UNORM8, 0);
        assertEquals(3, VertexLayout.ofInterleaved(3, bytes).strideBytes); // byte components need no alignment
    }

    /** A block of members named m0, m1, ... of the given types. */
    private static Block block(Rules rules, ShaderLayout.Type... types) {
        ShaderLayout.Member[] members = new ShaderLayout.Member[types.length];
        for (int i = 0; i < types.length; i++) {
            members[i] = ShaderLayout.member("m" + i, types[i]);
        }
        return ShaderLayout.layout(rules, ShaderLayout.struct("S", members));
    }

    @Test
    void std140AndStd430Offsets() {
        // GLSL 4.60 section 7.6.2.2, rules 1-3 and 5: a vec3 has base alignment 16 but size 12, so a following
        // float packs into its 4th component; a mat4 is an array of 4 vec4 columns (alignment 16, size 64).
        // (Formerly asserted on StdLayout, removed in 3.0 in favour of ShaderLayout.)
        for (Rules rules : new Rules[] {Rules.STD140, Rules.STD430}) {
            Block b = block(rules, ShaderLayout.vec(3), ShaderLayout.FLOAT, ShaderLayout.mat(4, 4));
            assertEquals(0, b.offset("m0"));
            assertEquals(12, b.offset("m1"));
            assertEquals(16, b.offset("m2"));
            assertEquals(80, b.paddedSize());
        }
    }

    @Test
    void structSizesFollowSpecRounding() {
        // std140 rounds every struct up to 16; std430 rounds to the largest member alignment.
        assertEquals(16, block(Rules.STD140, ShaderLayout.vec(3), ShaderLayout.FLOAT).paddedSize());
        assertEquals(16, block(Rules.STD430, ShaderLayout.vec(3), ShaderLayout.FLOAT).paddedSize());
        assertEquals(32, block(Rules.STD430, ShaderLayout.vec(4), ShaderLayout.FLOAT).paddedSize());
        assertEquals(16, block(Rules.STD430, ShaderLayout.vec(2), ShaderLayout.FLOAT).paddedSize());
        assertEquals(4, block(Rules.STD430, ShaderLayout.FLOAT).paddedSize());
        assertEquals(16, block(Rules.STD140, ShaderLayout.FLOAT).paddedSize());
        Block o = block(Rules.STD430, ShaderLayout.FLOAT, ShaderLayout.vec(3), ShaderLayout.FLOAT);
        assertEquals(0, o.offset("m0"));
        assertEquals(16, o.offset("m1"));
        assertEquals(28, o.offset("m2"));
    }
}
