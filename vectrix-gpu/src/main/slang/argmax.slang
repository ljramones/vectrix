// Sampler.argmax over rows: dest[dOff + r] = the index of the largest of the n logits from logits[off + r·ld], the
// first of equal ones; the index of the first NaN if the row holds one; -1 if n is 0. Java twin:
// org.dynamisengine.vectrix.nn.Sampler.
//
// Values are compared through an integer key with the order of Java's float comparison (-0 equals +0) and NaN above
// everything, so the scan is exact on every device, subnormals included. The row's answer is then the lowest index
// of the largest key: the first NaN if any, else the first maximum. One group of 256 threads per row: each thread
// scans every 256th element in order, and a tree over group memory keeps the larger key, then the lower index.
struct ArgmaxParams
{
    uint rows;
    uint n;
    uint off;
    uint ld;
    uint dOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> logits;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> dest;
[[vk::push_constant]] ConstantBuffer<ArgmaxParams> p;

static const uint GROUP = 256;

groupshared int keys[GROUP];
groupshared uint indices[GROUP];

// Float bits to a key that orders as Java's float comparison does: ±0 equal, NaN above +infinity.
int orderKey(uint bits)
{
    uint mag = bits & 0x7FFFFFFFu;
    if (mag > 0x7F800000u)
    {
        return 0x7FFFFFFF;
    }
    int m = int(mag);
    return (bits & 0x80000000u) != 0u ? -m : m;
}

[shader("compute")]
[numthreads(256, 1, 1)]
void argmax(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return; // the whole group: row depends only on the group
    }
    uint t = local.x;
    uint base = p.off + row * p.ld;
    int bestKey = int(0x80000000u); // below every key
    uint best = 0xFFFFFFFFu;        // -1: no element
    for (uint i = t; i < p.n; i += GROUP)
    {
        int k = orderKey(logits[base + i]);
        if (k > bestKey)
        {
            bestKey = k;
            best = i;
        }
    }
    keys[t] = bestKey;
    indices[t] = best;
    GroupMemoryBarrierWithGroupSync();
    for (uint stride = GROUP / 2; stride > 0; stride >>= 1)
    {
        if (t < stride)
        {
            int k1 = keys[t];
            int k2 = keys[t + stride];
            uint i1 = indices[t];
            uint i2 = indices[t + stride];
            if (k2 > k1 || k2 == k1 && i2 < i1)
            {
                keys[t] = k2;
                indices[t] = i2;
            }
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        dest[p.dOff + row] = indices[0];
    }
}
