// Softmax.softmax in place over each attention row's keysFor(query) scores, one group of 256 threads per row. The
// same algorithm as softmax_row.slang (copied, so the two can change independently): Java Math.max for the maximum,
// Softmax.expShifted, Blasf.sum's order for the sum (thread (block, lane) adds the elements of its block whose index
// is lane mod 8, in index order), and the division by the thread that wrote each element. Java twin:
// org.dynamisengine.vectrix.nn.Attention (forward).
import common;
import attention_common;

struct SoftmaxRowsParams
{
    uint rows;
    uint heads;
    uint queries;
    uint t;
    uint causal;
    uint pOff;
    uint ldp;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> probs;
[[vk::push_constant]] ConstantBuffer<SoftmaxRowsParams> p;

static const uint GROUP = 256;
static const uint MAX_BLOCKS = 1024;

// As in softmax_row.slang, values a thread copies to a local live in group variables that are not written again for
// the row, because slangc's SPIR-V output may reread the group variable after a barrier.
groupshared float shared[GROUP];
groupshared float blockSums[MAX_BLOCKS];
groupshared float rowMax;
groupshared float total;

struct SharedBlocks : IBlockSource
{
    uint next;

    [mutating] float nextBlock()
    {
        float v = blockSums[next];
        next++;
        return v;
    }
};

[shader("compute")]
[numthreads(256, 1, 1)]
void attentionSoftmax(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return; // the whole group: row depends only on the group
    }
    uint t = local.x;
    uint n = keysFor(row / p.heads, p.queries, p.t, p.causal);
    uint base = p.pOff + row * p.ldp;

    float mx = asfloat(0xFF800000u);
    for (uint i = t; i < n; i += GROUP)
    {
        mx = jmax(mx, probs[base + i]);
    }
    shared[t] = mx;
    GroupMemoryBarrierWithGroupSync();
    for (uint stride = GROUP / 2; stride > 0; stride >>= 1)
    {
        if (t < stride)
        {
            shared[t] = jmax(shared[t], shared[t + stride]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        rowMax = shared[0];
    }
    GroupMemoryBarrierWithGroupSync();
    float m = rowMax;
    if (m == asfloat(0xFF800000u))
    {
        // every score is -inf (fully masked): zero probabilities, as Softmax.softmax (4.1)
        for (uint i = t; i < n; i += GROUP)
        {
            probs[base + i] = 0.0f;
        }
        return;
    }

    uint blocks = (n + ATTENTION_BLOCK - 1) / ATTENTION_BLOCK;
    uint lane = t % 8;
    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        float acc = 0.0f;
        if (b < blocks)
        {
            uint start = b * ATTENTION_BLOCK;
            uint len = min(ATTENTION_BLOCK, n - start);
            for (uint j = lane; j < len; j += 8)
            {
                float e = attentionExpShifted(probs[base + start + j], m);
                probs[base + start + j] = e;
                acc = acc + e;
            }
        }
        shared[t] = acc;
        GroupMemoryBarrierWithGroupSync();
        if (lane == 0 && b < blocks)
        {
            blockSums[b] = combineLanes(shared[t], shared[t + 1], shared[t + 2], shared[t + 3], shared[t + 4],
                    shared[t + 5], shared[t + 6], shared[t + 7]);
        }
        GroupMemoryBarrierWithGroupSync();
    }
    if (t == 0)
    {
        SharedBlocks source;
        source.next = 0;
        total = pairwiseBlocks(source, blocks);
    }
    GroupMemoryBarrierWithGroupSync();
    float s = total;

    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        if (b < blocks)
        {
            uint start = b * ATTENTION_BLOCK;
            uint len = min(ATTENTION_BLOCK, n - start);
            for (uint j = lane; j < len; j += 8)
            {
                probs[base + start + j] = probs[base + start + j] / s;
            }
        }
    }
}
