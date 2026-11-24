// PackedAffineKernels.quatTranslationToPackedAffineBatch: entry i of the output (12 floats at outOff + 12·i,
// AFFINE_3X4_ROW_MAJOR) becomes T(t[i]) * R(q[i]) for i in [0, count). Java twin:
// org.dynamisengine.vectrix.affine.PackedAffineKernels (setTrs with unit scale).
//
// Buffers: the rotations are 4 floats per quaternion (x, y, z, w) from qOff; the translations are planes tx, ty, tz
// (engine.slang). One thread per transform.
import common;
import engine;

struct QuatTranslationParams
{
    uint count;
    uint qOff;
    uint tOff;
    uint tStride;
    uint outOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> rotation;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> translation;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> outAffine;
[[vk::push_constant]] ConstantBuffer<QuatTranslationParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void quatTranslationToAffine(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    uint q = p.qOff + 4 * i;
    float qx = rotation[q];
    float qy = rotation[q + 1];
    float qz = rotation[q + 2];
    float qw = rotation[q + 3];
    float tx = translation[p.tOff + i];
    float ty = translation[p.tOff + p.tStride + i];
    float tz = translation[p.tOff + 2 * p.tStride + i];
    float sx = 1.0f;
    float sy = 1.0f;
    float sz = 1.0f;

    float dqx = qx + qx;
    float dqy = qy + qy;
    float dqz = qz + qz;
    float q00 = dqx * qx;
    float q11 = dqy * qy;
    float q22 = dqz * qz;
    float q01 = dqx * qy;
    float q02 = dqx * qz;
    float q03 = dqx * qw;
    float q12 = dqy * qz;
    float q13 = dqy * qw;
    float q23 = dqz * qw;

    // Row-major R * S: column c of the rotation is scaled by s_c.
    uint o = p.outOff + 12 * i;
    outAffine[o] = sx - (q11 + q22) * sx;
    outAffine[o + 1] = (q01 - q23) * sy;
    outAffine[o + 2] = (q02 + q13) * sz;
    outAffine[o + 3] = tx;

    outAffine[o + 4] = (q01 + q23) * sx;
    outAffine[o + 5] = sy - (q22 + q00) * sy;
    outAffine[o + 6] = (q12 - q03) * sz;
    outAffine[o + 7] = ty;

    outAffine[o + 8] = (q02 - q13) * sx;
    outAffine[o + 9] = (q12 + q03) * sy;
    outAffine[o + 10] = sz - (q11 + q00) * sz;
    outAffine[o + 11] = tz;
}
