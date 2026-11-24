// Softmax.softmaxBackward in place over each attention row's keysFor(query) entries, one group of 256 threads per
// row: with y the probabilities and dy the score gradients, dd = Blasf.dot(dy, y, n), then dy[j] = y[j]·(dy[j] - dd).
// Thread (block, lane) adds fma(dy[j], y[j], acc) over the elements of its 256-element block whose index is lane mod
// 8, in index order; the lanes combine as in Blasf and the blocks pairwise. Each thread then rewrites only the
// elements it read. Java twin: org.dynamisengine.vectrix.nn.Attention (backward).
import common;
import attention_common;

struct SoftmaxBackwardParams
{
    uint rows;
    uint heads;
    uint queries;
    uint t;
    uint causal;
    uint pOff;
    uint ldp;
    uint dsOff;
    uint ldds;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> probs;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> ds;
[[vk::push_constant]] ConstantBuffer<SoftmaxBackwardParams> p;

static const uint GROUP = 256;
static const uint MAX_BLOCKS = 1024;

// Written once per row and then only read (see attention_softmax.slang).
groupshared float shared[GROUP];
groupshared float blockSums[MAX_BLOCKS];
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
void attentionSoftmaxBackward(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return; // the whole group: row depends only on the group
    }
    uint t = local.x;
    uint n = keysFor(row / p.heads, p.queries, p.t, p.causal);
    uint yBase = p.pOff + row * p.ldp;
    uint dBase = p.dsOff + row * p.ldds;

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
                acc = fma(ds[dBase + start + j], probs[yBase + start + j], acc);
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
    float dd = total;

    for (uint b0 = 0; b0 < blocks; b0 += GROUP / 8)
    {
        uint b = b0 + t / 8;
        if (b < blocks)
        {
            uint start = b * ATTENTION_BLOCK;
            uint len = min(ATTENTION_BLOCK, n - start);
            for (uint j = lane; j < len; j += 8)
            {
                ds[dBase + start + j] = probs[yBase + start + j] * (ds[dBase + start + j] - dd);
            }
        }
    }
}
