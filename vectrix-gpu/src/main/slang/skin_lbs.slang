// SkinningKernels.skinLbs4 and skinLbs4SoA: linear blend skinning from a TransformSoA, four influences per vertex.
// Java twin: org.dynamisengine.vectrix.soa.SkinningKernels (skinLbs4Single).
//
// Buffers: joints is a TransformSoA buffer (planes tx, ty, tz, qx, qy, qz, qw, sx, sy, sz, see engine.slang);
// positions are planes x, y, z. Influence k of vertex i is at idxOff + k * idxSlot + i * idxStep (and likewise for
// the weights): slot 1 and step 4 for the interleaved layout, slot = stride and step 1 for the per-slot one. A vertex
// with a joint index outside [0, jointCount) is not written. One thread per vertex.
import common;
import engine;

struct SkinParams
{
    uint count;
    uint jointCount;
    uint jointsOff;
    uint jointStride;
    uint idxOff;
    uint idxSlot;
    uint idxStep;
    uint wOff;
    uint wSlot;
    uint wStep;
    uint inOff;
    uint inStride;
    uint outOff;
    uint outStride;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> joints;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> indices;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> weights;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> inPos;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> outPos;
[[vk::push_constant]] ConstantBuffer<SkinParams> p;

float J(uint field, uint j)
{
    return joints[p.jointsOff + field * p.jointStride + j];
}

[shader("compute")]
[numthreads(64, 1, 1)]
void skinLbs(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    uint jk[4];
    float wk[4];
    [ForceUnroll]
    for (uint k = 0; k < 4; k++)
    {
        jk[k] = uint(indices[p.idxOff + k * p.idxSlot + i * p.idxStep]);
        wk[k] = weights[p.wOff + k * p.wSlot + i * p.wStep];
    }
    if (jk[0] >= p.jointCount || jk[1] >= p.jointCount || jk[2] >= p.jointCount || jk[3] >= p.jointCount)
    {
        return;
    }
    float x = inPos[p.inOff + i];
    float y = inPos[p.inOff + p.inStride + i];
    float z = inPos[p.inOff + 2 * p.inStride + i];

    // Java starts each sum at 0.0f (ox += ...); its first add is plusZero, since Slang folds 0 + v to v
    float ox = 0.0f;
    float oy = 0.0f;
    float oz = 0.0f;
    [ForceUnroll]
    for (uint k = 0; k < 4; k++)
    {
        uint j = jk[k];
        // scale, then rotate (q·p·q⁻¹ = p + w·t + v × t, t = 2·v × p), then translate
        float px = x * J(7, j);
        float py = y * J(8, j);
        float pz = z * J(9, j);
        float rx = J(3, j);
        float ry = J(4, j);
        float rz = J(5, j);
        float rw = J(6, j);
        float tx2 = 2.0f * (ry * pz - rz * py);
        float ty2 = 2.0f * (rz * px - rx * pz);
        float tz2 = 2.0f * (rx * py - ry * px);
        ox = add(k, ox, (px + rw * tx2 + (ry * tz2 - rz * ty2) + J(0, j)) * wk[k]);
        oy = add(k, oy, (py + rw * ty2 + (rz * tx2 - rx * tz2) + J(1, j)) * wk[k]);
        oz = add(k, oz, (pz + rw * tz2 + (rx * ty2 - ry * tx2) + J(2, j)) * wk[k]);
    }
    outPos[p.outOff + i] = ox;
    outPos[p.outOff + p.outStride + i] = oy;
    outPos[p.outOff + 2 * p.outStride + i] = oz;
}
