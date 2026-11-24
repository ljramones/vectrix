// PackedAffineKernels.transformAabbPackedAffineBatch: for i in [0, count), with k = order[orderOff + i], box k of the
// input transformed by affine k is enclosed in box k of the output. Java twin:
// org.dynamisengine.vectrix.affine.PackedAffineKernels (transformAabbPackedAffineRange).
//
// Buffers: transforms hold 12 floats per entry from tOff (AFFINE_3X4_ROW_MAJOR); the boxes are AABBSoA buffers
// (planes minX, minY, minZ, maxX, maxY, maxZ, see engine.slang); order holds ints. An index k outside [0, entries)
// is skipped. One thread per element of order.
import common;
import engine;

struct AabbParams
{
    uint count;
    uint entries;
    uint tOff;
    uint orderOff;
    uint inOff;
    uint inStride;
    uint outOff;
    uint outStride;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> transforms;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> order;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> inBox;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> outBox;
[[vk::push_constant]] ConstantBuffer<AabbParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void transformAabb(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    uint idx = uint(order[p.orderOff + i]);
    if (idx >= p.entries)
    {
        return;
    }
    uint o = p.tOff + 12 * idx;
    float r00 = transforms[o];
    float r01 = transforms[o + 1];
    float r02 = transforms[o + 2];
    float tx = transforms[o + 3];
    float r10 = transforms[o + 4];
    float r11 = transforms[o + 5];
    float r12 = transforms[o + 6];
    float ty = transforms[o + 7];
    float r20 = transforms[o + 8];
    float r21 = transforms[o + 9];
    float r22 = transforms[o + 10];
    float tz = transforms[o + 11];

    uint b = p.inOff + idx;
    uint s = p.inStride;
    float minX = inBox[b];
    float minY = inBox[b + s];
    float minZ = inBox[b + 2 * s];
    float maxX = inBox[b + 3 * s];
    float maxY = inBox[b + 4 * s];
    float maxZ = inBox[b + 5 * s];

    float cx = (minX + maxX) * 0.5f;
    float cy = (minY + maxY) * 0.5f;
    float cz = (minZ + maxZ) * 0.5f;
    float ex = (maxX - minX) * 0.5f;
    float ey = (maxY - minY) * 0.5f;
    float ez = (maxZ - minZ) * 0.5f;

    float ncx = r00 * cx + r01 * cy + r02 * cz + tx;
    float ncy = r10 * cx + r11 * cy + r12 * cz + ty;
    float ncz = r20 * cx + r21 * cy + r22 * cz + tz;

    float nex = jabs(r00) * ex + jabs(r01) * ey + jabs(r02) * ez;
    float ney = jabs(r10) * ex + jabs(r11) * ey + jabs(r12) * ez;
    float nez = jabs(r20) * ex + jabs(r21) * ey + jabs(r22) * ez;

    uint d = p.outOff + idx;
    uint t = p.outStride;
    outBox[d] = ncx - nex;
    outBox[d + t] = ncy - ney;
    outBox[d + 2 * t] = ncz - nez;
    outBox[d + 3 * t] = ncx + nex;
    outBox[d + 4 * t] = ncy + ney;
    outBox[d + 5 * t] = ncz + nez;
}
