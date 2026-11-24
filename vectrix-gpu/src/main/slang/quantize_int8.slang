// Formats.quantizeInt8: blocks of 32 floats from x[xOff ..], n of them in all (a multiple of 32), get the scale
// max|x| / 127 (scales[sOff + b], float bits) and the codes rint(x / scale) clamped to ±127 (q[qOff + i], packed four
// per 32-bit word, element 0 in the low byte). Exact on every device, subnormals included (formats.slang). Java twin:
// org.dynamisengine.vectrix.nn.Formats.
//
// The CPU method rejects NaN and infinite values before writing anything; a kernel cannot, so a block holding one
// gets the scale NaN (0x7FC00000) and all-zero codes.
//
// A group of 256 threads owns 256 consecutive words of q, one per thread, so a word shared by two blocks is written
// once and any qOff works. First one thread per block touched by the group's words computes the block's scale (and
// writes it, if the block starts in this group's words); then each thread writes its word.
import formats;

struct QuantizeInt8Params
{
    uint xOff;
    uint n;
    uint qOff;
    uint sOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> q;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint> scales;
[[vk::push_constant]] ConstantBuffer<QuantizeInt8Params> p;

static const uint GROUP = 256;
static const uint QBLOCK = 32;

// The group's 1024 bytes span at most 33 blocks. Written once, before the barrier, and only read after it.
groupshared uint blockScale[34];

// The scale bits of the block of 32 floats from x[base]: Math.max of Math.abs, which for finite values is the
// largest magnitude's bits as an unsigned integer.
uint blockScaleBits(uint base)
{
    uint amax = 0u;
    for (uint j = 0; j < QBLOCK; j++)
    {
        amax = max(amax, x[base + j] & 0x7FFFFFFFu);
    }
    return amax >= 0x7F800000u ? NONFINITE_SCALE : int8Scale(amax);
}

[shader("compute")]
[numthreads(256, 1, 1)]
void quantizeInt8(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint g = group.y * p.gridX + group.x;
    uint first = p.qOff >> 2;
    uint words = ((p.qOff + p.n - 1) >> 2) - first + 1;
    if (g >= (words + GROUP - 1) / GROUP)
    {
        return; // the whole group: g depends only on the group
    }
    uint t = local.x;
    uint w0 = first + g * GROUP;
    uint wLast = min(w0 + GROUP, first + words) - 1;
    // the group's elements, [eStart, eLast]
    uint eStart = w0 * 4 > p.qOff ? w0 * 4 - p.qOff : 0u;
    uint eLast = min(p.n - 1, wLast * 4 + 3 - p.qOff);
    uint bFirst = eStart / QBLOCK;
    uint blocks = eLast / QBLOCK - bFirst + 1;
    if (t < blocks)
    {
        uint b = bFirst + t;
        uint s = blockScaleBits(p.xOff + b * QBLOCK);
        blockScale[t] = s;
        if (b * QBLOCK >= eStart)
        {
            scales[p.sOff + b] = s;
        }
    }
    GroupMemoryBarrierWithGroupSync();
    uint w = w0 + t;
    if (w > wLast)
    {
        return;
    }
    uint lo = w * 4;
    bool partial = lo < p.qOff || lo + 3 - p.qOff >= p.n;
    uint word = partial ? q[w] : 0u;
    for (uint k = 0; k < 4; k++)
    {
        uint i = lo + k;
        if (i >= p.qOff && i - p.qOff < p.n)
        {
            uint e = i - p.qOff;
            uint shift = k * 8u;
            uint c = int8Code(x[p.xOff + e], blockScale[e / QBLOCK - bFirst]);
            word = (word & ~(0xFFu << shift)) | (c << shift);
        }
    }
    q[w] = word;
}
