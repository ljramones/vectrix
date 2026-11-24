// Blasf reductions, first pass: the value of every subtree of the pairwise block tree at depth `depth`, each at most
// LEAF_BLOCKS blocks, written to scratch[leafOff + subtree]. Java twin: org.dynamisengine.vectrix.blas.Blasf (the
// *Pairwise and *Block methods and tail). See blas.slang.
//
// Group g owns subtree g. Thread t takes block t / 8 of the subtree and lane t mod 8: the elements of that block
// whose index in the block is the lane mod 8, added in index order, as Blasf's lanes are (its tail gives the last
// len mod 8 elements to lanes 0, 1, ... in order). Lane 0 combines the 8 lanes as Blasf does, and thread 0 adds the
// subtree's blocks pairwise. When `need` is not 0 the pass runs only if scratch[stateOff] == need (nrm2's rescaled
// pass, whose exponent is scratch[stateOff + 1]).
import common;
import blas;

struct PartialParams
{
    uint op;
    uint n;
    uint xOff;
    uint yOff;
    uint blocks;
    uint depth;
    uint leafOff;
    uint stateOff;
    uint need;
    uint gridX;
};

// Elements are read as bits: arithmetic happens in blasStep, and nrm2's scaling needs the exact bits.
[[vk::binding(0, 0)]] RWStructuredBuffer<uint> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> y;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint> scratch;
[[vk::push_constant]] ConstantBuffer<PartialParams> p;

static const uint GROUP = 256;

// Each written once per dispatch, so no copy of them is ever stale.
groupshared float laneSums[GROUP];
groupshared float blockSums[LEAF_BLOCKS];

struct LeafBlocks : IBlockSource
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
void blasPartial(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint g = group.y * p.gridX + group.x;
    if (g >= (1u << p.depth))
    {
        return; // the whole group: g depends only on the group
    }
    uint count = nodeBlocks(p.blocks, p.depth, g);
    if (count == 0)
    {
        return;
    }
    if (p.need != 0 && scratch[p.stateOff] != p.need)
    {
        return; // the same value for every thread
    }
    int k = 0;
    if (p.op == 4)
    {
        k = -int(scratch[p.stateOff + 1]);
    }
    uint start = nodeStart(p.blocks, p.depth, g);
    uint t = local.x;
    uint b = t / 8;
    uint lane = t % 8;
    float acc = 0.0f;
    if (b < count)
    {
        uint base = (start + b) * BLAS_BLOCK;
        uint len = min(BLAS_BLOCK, p.n - base);
        for (uint j = lane; j < len; j += 8)
        {
            uint yb = 0u;
            if (p.op == 0)
            {
                yb = y[p.yOff + base + j];
            }
            acc = blasStep(p.op, acc, x[p.xOff + base + j], yb, k);
        }
    }
    laneSums[t] = acc;
    GroupMemoryBarrierWithGroupSync();
    if (lane == 0 && b < count)
    {
        blockSums[b] = combineLanes(laneSums[t], laneSums[t + 1], laneSums[t + 2], laneSums[t + 3], laneSums[t + 4],
                laneSums[t + 5], laneSums[t + 6], laneSums[t + 7]);
    }
    GroupMemoryBarrierWithGroupSync();
    if (t == 0)
    {
        LeafBlocks source;
        source.next = 0;
        scratch[p.leafOff + g] = asuint(pairwiseBlocks(source, count));
    }
}
