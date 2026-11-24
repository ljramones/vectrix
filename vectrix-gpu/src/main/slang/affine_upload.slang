// PackedAffineKernels.uploadPackedAffine: for i in [0, count), the 12 floats of transform order[orderOff + i] are
// copied to outOff + 12·i (AFFINE_3X4_ROW_MAJOR on both sides). Java twin:
// org.dynamisengine.vectrix.affine.PackedAffineKernels (uploadPackedAffineRange).
//
// The copy moves 32-bit words, so every bit pattern (NaN payloads included) arrives unchanged. An index outside
// [0, transformCount) is skipped. One thread per element of order.
import common;
import engine;

struct UploadParams
{
    uint count;
    uint transformCount;
    uint tOff;
    uint orderOff;
    uint outOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> transforms;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> order;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint> outUpload;
[[vk::push_constant]] ConstantBuffer<UploadParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void uploadAffine(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    uint idx = uint(order[p.orderOff + i]);
    if (idx >= p.transformCount)
    {
        return;
    }
    uint src = p.tOff + 12 * idx;
    uint dst = p.outOff + 12 * i;
    [ForceUnroll]
    for (uint c = 0; c < 12; c++)
    {
        outUpload[dst + c] = transforms[src + c];
    }
}
