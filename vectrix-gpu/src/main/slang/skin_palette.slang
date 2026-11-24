// SkinningKernels.skinLbs4MatrixPalette: linear blend skinning from a packed 3×4 matrix palette, four influences per
// vertex. Java twin: org.dynamisengine.vectrix.soa.SkinningKernels.
//
// Buffers: the palette holds 12 floats per joint from palOff, row-major [r00 r01 r02 tx  r10 r11 r12 ty  r20 r21 r22
// tz] (PackedAffineArray's AFFINE_3X4_ROW_MAJOR); positions are planes x, y, z; influences are addressed as in
// skin_lbs.slang. A vertex with a joint index outside [0, jointCount) is not written. One thread per vertex.
import common;
import engine;

struct PaletteSkinParams
{
    uint count;
    uint jointCount;
    uint palOff;
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

[[vk::binding(0, 0)]] RWStructuredBuffer<float> palette;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> indices;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> weights;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> inPos;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> outPos;
[[vk::push_constant]] ConstantBuffer<PaletteSkinParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void skinPalette(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
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
        uint m = p.palOff + jk[k] * 12;
        float w = wk[k];
        ox = add(k, ox, (palette[m] * x + palette[m + 1] * y + palette[m + 2] * z + palette[m + 3]) * w);
        oy = add(k, oy, (palette[m + 4] * x + palette[m + 5] * y + palette[m + 6] * z + palette[m + 7]) * w);
        oz = add(k, oz, (palette[m + 8] * x + palette[m + 9] * y + palette[m + 10] * z + palette[m + 11]) * w);
    }
    outPos[p.outOff + i] = ox;
    outPos[p.outOff + p.outStride + i] = oy;
    outPos[p.outOff + 2 * p.outStride + i] = oz;
}
