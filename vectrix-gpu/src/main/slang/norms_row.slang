// Norms.layerNorm, Norms.rmsNorm and their backward passes for one row, run by one group of 256 threads, in the
// Java's exact order. Java twin: org.dynamisengine.vectrix.nn.Norms.
//
//   means and variances: Blasf.sum and Blasf.dot (groupSum in softmax_row.slang), divided by n;
//   1/σ = 1 / sqrt(var + eps): Java's (float) Math.sqrt of a float is the correctly rounded float square root;
//   element-wise: each output element is computed and written by one thread after the reductions, so outputs may be
//   inputs at the same offset.
// Optional inputs (gamma, beta) are read at index 0 of a placeholder buffer when absent, so no read leaves a buffer.
// The parameter gradients (accumulated over the rows in row order) are norm_param_grads.slang.
import common;
import softmax_row;

// Blasf.dot(y, y) of the centred row y = x - mean: fma(c, c, acc).
struct CentredSquares : IRowSum
{
    RWStructuredBuffer<float> x;
    uint xBase;
    float mean;

    float accumulate(float acc, uint j)
    {
        float c = x[xBase + j] - mean;
        return fma(c, c, acc);
    }
};

// An optional vector: v[base + j], or `absent` when there is none (then index 0 is read, in bounds).
float optional(RWStructuredBuffer<float> v, uint base, bool present, uint j, float absent)
{
    float value = v[present ? base + j : 0];
    return present ? value : absent;
}

// Norms.layerNorm: y = fma((x - mean)·rstd, γ, β), stats = (mean, rstd) at sBase when hasStats.
void layerNormRow(RWStructuredBuffer<float> x, uint xBase, RWStructuredBuffer<float> gamma, uint gBase,
        bool hasGamma, RWStructuredBuffer<float> beta, uint bBase, bool hasBeta, float eps,
        RWStructuredBuffer<float> y, uint yBase, uint n, RWStructuredBuffer<float> stats, uint sBase, bool hasStats,
        uint t)
{
    RowSum s;
    s.x = x;
    s.xBase = xBase;
    float mean = groupSum(s, n, t, 0) / float(n);
    CentredSquares q;
    q.x = x;
    q.xBase = xBase;
    q.mean = mean;
    float var = groupSum(q, n, t, 1) / float(n);
    float rstd = 1.0f / sqrt(var + eps);
    for (uint i = t; i < n; i += GROUP)
    {
        float v = (x[xBase + i] - mean) * rstd;
        float g = optional(gamma, gBase, hasGamma, i, 1.0f);
        float b = optional(beta, bBase, hasBeta, i, 0.0f);
        y[yBase + i] = fma(v, g, b);
    }
    if (hasStats && t == 0)
    {
        stats[sBase] = mean;
        stats[sBase + 1] = rstd;
    }
}

// Norms.rmsNorm: y = x·rinv·γ, rinv = 1 / sqrt(Blasf.dot(x, x) / n + eps); stats = rinv at sBase when hasStats.
void rmsNormRow(RWStructuredBuffer<float> x, uint xBase, RWStructuredBuffer<float> gamma, uint gBase,
        bool hasGamma, float eps, RWStructuredBuffer<float> y, uint yBase, uint n, RWStructuredBuffer<float> stats,
        uint sBase, bool hasStats, uint t)
{
    RowDot q;
    q.a = x;
    q.aBase = xBase;
    q.b = x;
    q.bBase = xBase;
    float ms = groupSum(q, n, t, 0) / float(n);
    float rinv = 1.0f / sqrt(ms + eps);
    for (uint i = t; i < n; i += GROUP)
    {
        float g = optional(gamma, gBase, hasGamma, i, 1.0f);
        y[yBase + i] = x[xBase + i] * rinv * g;
    }
    if (hasStats && t == 0)
    {
        stats[sBase] = rinv;
    }
}

// The per-element terms of the backward passes: x̂ = (x - mean)·rstd (layer) or x·rinv (RMS), dx̂ = dy·γ.
struct NormTerms
{
    RWStructuredBuffer<float> x;
    uint xBase;
    RWStructuredBuffer<float> gamma;
    uint gBase;
    bool hasGamma;
    RWStructuredBuffer<float> dy;
    uint dyBase;
    bool rms;
    float mean;
    float scale;

    float h(uint j)
    {
        float xv = x[xBase + j];
        if (rms)
        {
            return xv * scale;
        }
        return (xv - mean) * scale;
    }

    float dxh(uint j)
    {
        return dy[dyBase + j] * optional(gamma, gBase, hasGamma, j, 1.0f);
    }
};

// Blasf.sum of dx̂.
struct DxhSum : IRowSum
{
    NormTerms terms;

    float accumulate(float acc, uint j)
    {
        return acc + terms.dxh(j);
    }
};

// Blasf.dot(dx̂, x̂): fma(dx̂, x̂, acc).
struct DxhDotH : IRowSum
{
    NormTerms terms;

    float accumulate(float acc, uint j)
    {
        return fma(terms.dxh(j), terms.h(j), acc);
    }
};

// The input gradient of Norms.layerNormBackward (rms false; stats = mean, rstd) or Norms.rmsNormBackward (rms true;
// stats = rinv): dx = rstd·fma(-x̂, m2, dx̂ - m1), or rinv·fma(-x̂, m2, dx̂), with m1 = Σ dx̂ / n and m2 = Σ dx̂·x̂ / n.
// dx may be x or dy at the same offset.
void normBackwardRow(RWStructuredBuffer<float> x, uint xBase, RWStructuredBuffer<float> gamma, uint gBase,
        bool hasGamma, RWStructuredBuffer<float> stats, uint sBase, bool rms, RWStructuredBuffer<float> dy,
        uint dyBase, RWStructuredBuffer<float> dx, uint dxBase, uint n, uint t)
{
    NormTerms terms;
    terms.x = x;
    terms.xBase = xBase;
    terms.gamma = gamma;
    terms.gBase = gBase;
    terms.hasGamma = hasGamma;
    terms.dy = dy;
    terms.dyBase = dyBase;
    terms.rms = rms;
    float s0 = stats[sBase];
    float s1 = stats[rms ? sBase : sBase + 1]; // RMS stats hold one value per row
    terms.mean = rms ? 0.0f : s0;
    terms.scale = rms ? s0 : s1;
    float m1 = 0.0f;
    if (!rms)
    {
        DxhSum f;
        f.terms = terms;
        m1 = groupSum(f, n, t, 0) / float(n);
    }
    DxhDotH g;
    g.terms = terms;
    float m2 = groupSum(g, n, t, 1) / float(n);
    for (uint i = t; i < n; i += GROUP)
    {
        float h = terms.h(i);
        float d = terms.dxh(i);
        if (rms)
        {
            dx[dxBase + i] = terms.scale * fma(-h, m2, d);
        }
        else
        {
            dx[dxBase + i] = terms.scale * fma(-h, m2, d - m1);
        }
    }
}
