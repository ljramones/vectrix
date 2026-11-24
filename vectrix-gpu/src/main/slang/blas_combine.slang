// Blasf reductions, upper passes: the nodes of the pairwise block tree at depth outDepth from those at inDepth
// (stored at scratch[inOff + index]), each as left + right in Blasf's order. Java twin:
// org.dynamisengine.vectrix.blas.Blasf (the *Pairwise methods, and nrm2's tests on the sums). See blas.slang.
//
// One thread per output node. mode 0 writes node idx to scratch[outOff + idx]; the last pass (outDepth 0) writes the
// result to result[outOff]: mode 1 the sum, mode 2 nrm2's first test (sqrt of a normal sum, a NaN as is, otherwise
// scratch[stateOff] = 1 to ask for the rescaled passes), mode 3 nrm2's rescaled result scalb(sqrt(t), e), e at
// scratch[stateOff + 1]. With `need` not 0 the pass runs only if scratch[stateOff] == need. blocks == 0 gives 0.
import common;
import blas;

struct CombineParams
{
    uint blocks;
    uint inDepth;
    uint outDepth;
    uint inOff;
    uint outOff;
    uint mode;
    uint stateOff;
    uint need;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> scratch;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> result;
[[vk::push_constant]] ConstantBuffer<CombineParams> p;

static const uint GROUP = 32;

// Frames for one walk: inDepth - outDepth + 1 (GpuBlasf passes at most 4 levels at a time).
static const int FRAMES = 8;

// The value of the node of `count` blocks at (depth, idx), walking its subtree down to inDepth in post-order.
float walk(uint count, uint depth, uint idx)
{
    uint cnt[FRAMES];
    uint state[FRAMES];
    uint dep[FRAMES];
    uint id[FRAMES];
    float left[FRAMES];
    int sp = 1;
    cnt[0] = count;
    state[0] = 0;
    dep[0] = depth;
    id[0] = idx;
    left[0] = 0.0f;
    float ret = 0.0f;
    while (sp > 0)
    {
        int top = sp - 1;
        if (dep[top] == p.inDepth || cnt[top] == 1)
        {
            // a stored node, or a one-block leaf: its value is at its leftmost descendant on level inDepth
            ret = asfloat(scratch[p.inOff + (id[top] << (p.inDepth - dep[top]))]);
            sp--;
        }
        else if (state[top] == 0)
        {
            state[top] = 1;
            cnt[sp] = (cnt[top] + 1) / 2;
            state[sp] = 0;
            dep[sp] = dep[top] + 1;
            id[sp] = id[top] * 2;
            sp++;
        }
        else if (state[top] == 1)
        {
            left[top] = ret;
            state[top] = 2;
            cnt[sp] = cnt[top] / 2;
            state[sp] = 0;
            dep[sp] = dep[top] + 1;
            id[sp] = id[top] * 2 + 1;
            sp++;
        }
        else
        {
            ret = left[top] + ret;
            sp--;
        }
    }
    return ret;
}

[shader("compute")]
[numthreads(32, 1, 1)]
void blasCombine(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint idx = (group.y * p.gridX + group.x) * GROUP + local.x;
    if (idx >= (1u << p.outDepth))
    {
        return;
    }
    if (p.need != 0 && scratch[p.stateOff] != p.need)
    {
        return;
    }
    float v = 0.0f;
    if (p.blocks != 0)
    {
        uint count = nodeBlocks(p.blocks, p.outDepth, idx);
        if (count == 0)
        {
            return; // below a one-block leaf: never read
        }
        v = walk(count, p.outDepth, idx);
    }
    if (p.mode == 0)
    {
        scratch[p.outOff + idx] = asuint(v);
    }
    else if (p.mode == 1)
    {
        result[p.outOff] = asuint(v);
    }
    else if (p.mode == 2)
    {
        // Blasf.nrm2: s >= Float.MIN_NORMAL && s < infinity, then s != s
        uint flag = 0;
        if (v >= asfloat(0x00800000u) && v < asfloat(0x7F800000u))
        {
            result[p.outOff] = asuint(sqrt(v));
        }
        else if (v != v)
        {
            result[p.outOff] = asuint(v);
        }
        else
        {
            flag = 1;
        }
        scratch[p.stateOff] = flag;
    }
    else
    {
        // Math.scalb((float) Math.sqrt(t), e)
        int e = int(scratch[p.stateOff + 1]);
        result[p.outOff] = asuint(scaleBy(asuint(sqrt(v)), e));
    }
}
