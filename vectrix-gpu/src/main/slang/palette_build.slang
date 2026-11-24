// SkinningKernels.buildMatrixPalette12 (scaled != 0) and buildRigidMatrixPalette12 (scaled == 0): joint j of a
// TransformSoA buffer (planes tx, ty, tz, qx, qy, qz, qw, sx, sy, sz, see engine.slang) becomes 12 floats at
// palOff + 12·j, row-major [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22 tz] (PackedAffineArray's
// AFFINE_3X4_ROW_MAJOR). Java twin: org.dynamisengine.vectrix.soa.SkinningKernels (writePalette). One thread per
// joint.
import common;
import engine;

struct PaletteParams
{
    uint jointCount;
    uint jointsOff;
    uint jointStride;
    uint palOff;
    uint scaled;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> joints;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> palette;
[[vk::push_constant]] ConstantBuffer<PaletteParams> p;

float J(uint field, uint j)
{
    return joints[p.jointsOff + field * p.jointStride + j];
}

[shader("compute")]
[numthreads(64, 1, 1)]
void buildPalette(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint j = (group.y * p.gridX + group.x) * 64 + local.x;
    if (j >= p.jointCount)
    {
        return;
    }
    float x = J(3, j);
    float y = J(4, j);
    float z = J(5, j);
    float w = J(6, j);
    float tx = J(0, j);
    float ty = J(1, j);
    float tz = J(2, j);
    float sx = p.scaled != 0 ? J(7, j) : 1.0f;
    float sy = p.scaled != 0 ? J(8, j) : 1.0f;
    float sz = p.scaled != 0 ? J(9, j) : 1.0f;

    float xx = x * x;
    float yy = y * y;
    float zz = z * z;
    float xy = x * y;
    float xz = x * z;
    float yz = y * z;
    float wx = w * x;
    float wy = w * y;
    float wz = w * z;

    uint o = p.palOff + j * 12;
    palette[o] = (1.0f - 2.0f * (yy + zz)) * sx;
    palette[o + 1] = 2.0f * (xy - wz) * sy;
    palette[o + 2] = 2.0f * (xz + wy) * sz;
    palette[o + 3] = tx;

    palette[o + 4] = 2.0f * (xy + wz) * sx;
    palette[o + 5] = (1.0f - 2.0f * (xx + zz)) * sy;
    palette[o + 6] = 2.0f * (yz - wx) * sz;
    palette[o + 7] = ty;

    palette[o + 8] = 2.0f * (xz - wy) * sx;
    palette[o + 9] = 2.0f * (yz + wx) * sy;
    palette[o + 10] = (1.0f - 2.0f * (xx + yy)) * sz;
    palette[o + 11] = tz;
}
