/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.vectrix.gpu.StdLayout;
import org.vectrix.gpu.VertexAttribute;
import org.vectrix.gpu.VertexAttributeFormat;
import org.vectrix.gpu.VertexLayout;

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
    void std140AndStd430Offsets() {
        int[] std140 = StdLayout.offsetsStd140(StdLayout.Type.VEC3, StdLayout.Type.FLOAT, StdLayout.Type.MAT4);
        int[] std430 = StdLayout.offsetsStd430(StdLayout.Type.VEC3, StdLayout.Type.FLOAT, StdLayout.Type.MAT4);
        assertEquals(0, std140[0]);
        assertEquals(16, std140[1]);
        assertEquals(32, std140[2]);
        assertEquals(0, std430[0]);
        assertEquals(16, std430[1]);
        assertEquals(32, std430[2]);
        assertEquals(96, StdLayout.structSizeStd140(StdLayout.Type.VEC3, StdLayout.Type.FLOAT, StdLayout.Type.MAT4));
        assertEquals(96, StdLayout.structSizeStd430(StdLayout.Type.VEC3, StdLayout.Type.FLOAT, StdLayout.Type.MAT4));
    }
}
