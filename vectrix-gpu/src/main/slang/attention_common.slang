// Helpers shared by the attention kernels (attention_*.slang). Java twin: org.dynamisengine.vectrix.nn.Attention.
//
// The kernels work on attention rows: row = query * heads + head, for `queries` queries of `heads` heads each. Query
// head h reads key/value head h / group. A row of query r attends to keysFor(r) keys: all t, or with causal masking
// the first t - queries + 1 + r (the queries are the last `queries` positions of the t keys).
import common;

static const uint ATTENTION_BLOCK = 256;

// The number of keys query r attends to.
uint keysFor(uint r, uint queries, uint t, uint causal)
{
    return causal != 0 ? t - queries + 1 + r : t;
}

// The terms of a dot product: left(e) and right(e) for e in [0, n), multiplied as fma(left, right, acc).
interface IDotTerms
{
    float left(uint e);
    float right(uint e);
}

// The 256-element blocks of Blasf.dot, computed by one thread: element e of a block goes to lane e mod 8, each lane
// adds its elements in index order with fma, and the lanes combine as in Blasf.
struct SerialDotBlocks<T : IDotTerms> : IBlockSource
{
    T terms;
    uint n;
    uint next;

    [mutating] float nextBlock()
    {
        uint start = next * ATTENTION_BLOCK;
        uint len = min(ATTENTION_BLOCK, n - start);
        next++;
        float acc[8] = { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f };
        for (uint e8 = 0; e8 < len; e8 += 8)
        {
            [ForceUnroll]
            for (uint l = 0; l < 8; l++)
            {
                uint e = e8 + l;
                if (e < len)
                {
                    acc[l] = fma(terms.left(start + e), terms.right(start + e), acc[l]);
                }
            }
        }
        return combineLanes(acc[0], acc[1], acc[2], acc[3], acc[4], acc[5], acc[6], acc[7]);
    }
};

// Blasf.dot of n terms in one thread: 0 for n == 0, otherwise the blocks summed pairwise.
float serialDot<T : IDotTerms>(T terms, uint n)
{
    if (n == 0)
    {
        return 0.0f;
    }
    SerialDotBlocks<T> blocks;
    blocks.terms = terms;
    blocks.n = n;
    blocks.next = 0;
    return pairwiseBlocks(blocks, (n + ATTENTION_BLOCK - 1) / ATTENTION_BLOCK);
}

// Softmax.expShifted for one element (a copy of the one in softmax_row.slang).
float attentionExpShifted(float xv, float m)
{
    float a = jmax(xv, m - 1e38f);
    float s = a - m;
    float bv = s - a;
    float av = s - bv;
    float err = (a - av) + (-bv - m);
    float e = reproducibleExp(s);
    return fma(e, err, e);
}
