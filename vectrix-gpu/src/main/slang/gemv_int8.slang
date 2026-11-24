// Formats.gemvInt8: y[yOff + r] = dotInt8(row r of W, x) for r in [0, m), W an m×n int8 block-quantized row-major
// matrix (codes q[qOff + r·n ..], packed four per 32-bit word with element 0 in the low byte; scales
// scales[sOff + r·n/32 ..]). Java twin: org.dynamisengine.vectrix.nn.Formats.dotInt8, whose order this follows:
//
//   lane l (0..7) of a row owns the elements whose index in their block is l mod 8. Within a block it sums q·x with
//   fused multiply-adds in index order; across blocks it accumulates T_l = fma(scale, blockPart_l, T_l) in block
//   order. The row's value is combineLanes(T_0 .. T_7), Blasf's lane order.
//
// A group of 64 threads computes 8 rows, one thread per (row, lane). The lanes meet in group memory, which is written
// once and read after the barrier.
import common;
import formats;

struct GemvInt8Params
{
    uint m;
    uint n;
    uint qOff;
    uint sOff;
    uint xOff;
    uint yOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> q;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> scales;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> y;
[[vk::push_constant]] ConstantBuffer<GemvInt8Params> p;

static const uint QBLOCK = 32;

groupshared float lanes[64];

[shader("compute")]
[numthreads(64, 1, 1)]
void gemvInt8(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = (group.y * p.gridX + group.x) * 8 + local.x / 8;
    uint lane = local.x % 8;
    float t = 0.0f;
    if (row < p.m)
    {
        uint blocks = p.n / QBLOCK;
        uint qBase = p.qOff + row * p.n;
        uint sBase = p.sOff + row * blocks;
        for (uint b = 0; b < blocks; b++)
        {
            uint base = b * QBLOCK;
            float a = 0.0f;
            for (uint j = lane; j < QBLOCK; j += 8)
            {
                a = fma(float(packedInt8(q, qBase + base + j)), x[p.xOff + base + j], a);
            }
            t = fma(scales[sBase + b], a, t);
        }
    }
    lanes[local.x] = t;
    GroupMemoryBarrierWithGroupSync();
    if (lane == 0 && row < p.m)
    {
        uint l = local.x;
        y[p.yOff + row] = combineLanes(lanes[l], lanes[l + 1], lanes[l + 2], lanes[l + 3], lanes[l + 4], lanes[l + 5],
                lanes[l + 6], lanes[l + 7]);
    }
}
