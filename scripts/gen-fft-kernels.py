#!/usr/bin/env python3
"""Generates the Stockham kernels of ComplexFftd/ComplexFftf: scalar and Vector API, from one butterfly description.

Rewrites the block between "// BEGIN GENERATED KERNELS" and "// END GENERATED KERNELS". Usage:
    python3 scripts/gen-fft-kernels.py double ComplexFftd src/main/java/org/dynamisengine/vectrix/fft/ComplexFftd.java
    python3 scripts/gen-fft-kernels.py float ComplexFftf src/main/java/org/dynamisengine/vectrix/fft/ComplexFftf.java

Each butterfly is a list of complex operations whose scalar and lanewise expansions round identically, so the
scalar and SIMD paths produce the same bits:
  add/sub      lanewise
  scale(c, a)  a * c (c a real constant)
  negI(a)      (ai, -ar): swap pairs, negate odd lanes
  posI(a)      (-ai, ar): swap pairs, negate even lanes
  twiddle      (ar*wr - ai*wi, ar*wi + ai*wr) = a*wr + swap(a)*(-wi, wi)
"""
import sys

BUTTERFLIES = {
    2: [('add', 'y0', 'x0', 'x1'), ('sub', 'y1', 'x0', 'x1')],
    4: [('add', 't0', 'x0', 'x2'), ('sub', 't1', 'x0', 'x2'), ('add', 't2', 'x1', 'x3'), ('sub', 'd3', 'x1', 'x3'),
        ('negI', 't3', 'd3'),
        ('add', 'y0', 't0', 't2'), ('add', 'y1', 't1', 't3'), ('sub', 'y2', 't0', 't2'), ('sub', 'y3', 't1', 't3')],
    3: [('add', 't1', 'x1', 'x2'), ('scale', 'h1', 'HALF', 't1'), ('sub', 't2', 'x0', 'h1'), ('sub', 'd', 'x1', 'x2'),
        ('scale', 't3', 'c3', 'd'), ('negI', 'n3', 't3'), ('posI', 'p3', 't3'),
        ('add', 'y0', 'x0', 't1'), ('add', 'y1', 't2', 'n3'), ('add', 'y2', 't2', 'p3')],
    5: [('add', 'b1', 'x1', 'x4'), ('add', 'b2', 'x2', 'x3'), ('sub', 'd1', 'x1', 'x4'), ('sub', 'd2', 'x2', 'x3'),
        ('add', 'e0', 'x0', 'b1'), ('add', 'y0', 'e0', 'b2'),
        ('scale', 'u1', 'c51', 'b1'), ('scale', 'u2', 'c52', 'b2'), ('add', 'v1', 'x0', 'u1'), ('add', 'r1', 'v1', 'u2'),
        ('scale', 'u3', 'c52', 'b1'), ('scale', 'u4', 'c51', 'b2'), ('add', 'v2', 'x0', 'u3'), ('add', 'r2', 'v2', 'u4'),
        ('scale', 'g1', 's51', 'd1'), ('scale', 'g2', 's52', 'd2'), ('add', 'i1', 'g1', 'g2'),
        ('scale', 'g3', 's52', 'd1'), ('scale', 'g4', 's51', 'd2'), ('sub', 'i2', 'g3', 'g4'),
        ('negI', 'n1', 'i1'), ('posI', 'p1', 'i1'), ('negI', 'n2', 'i2'), ('posI', 'p2', 'i2'),
        ('add', 'y1', 'r1', 'n1'), ('add', 'y4', 'r1', 'p1'), ('add', 'y2', 'r2', 'n2'), ('add', 'y3', 'r2', 'p2')],
}


def scalar_ops(ops, T):
    half = '0.5' if T == 'double' else '0.5f'
    out = []
    for op in ops:
        k, d = op[0], op[1]
        if k in ('add', 'sub'):
            s = '+' if k == 'add' else '-'
            a, b = op[2], op[3]
            out.append(f'{T} {d}r = {a}r {s} {b}r;')
            out.append(f'{T} {d}i = {a}i {s} {b}i;')
        elif k == 'scale':
            c = half if op[2] == 'HALF' else op[2]
            a = op[3]
            out.append(f'{T} {d}r = {a}r * {c};')
            out.append(f'{T} {d}i = {a}i * {c};')
        elif k == 'negI':
            a = op[2]
            out.append(f'{T} {d}r = {a}i;')
            out.append(f'{T} {d}i = -{a}r;')
        elif k == 'posI':
            a = op[2]
            out.append(f'{T} {d}r = -{a}i;')
            out.append(f'{T} {d}i = {a}r;')
    return out


def vector_ops(ops, V):
    out = []
    for op in ops:
        k, d = op[0], op[1]
        if k in ('add', 'sub'):
            out.append(f'{V} {d} = {op[2]}.{k}({op[3]});')
        elif k == 'scale':
            c = 'HALF' if op[2] == 'HALF' else 'p.' + op[2]
            out.append(f'{V} {d} = {op[3]}.mul({c});')
        elif k == 'negI':
            out.append(f'{V} {d} = {op[2]}.rearrange(SWAP).lanewise(VectorOperators.NEG, ODD);')
        elif k == 'posI':
            out.append(f'{V} {d} = {op[2]}.rearrange(SWAP).lanewise(VectorOperators.NEG, EVEN);')
    return out


def ind(lines, n):
    return [' ' * n + l for l in lines]


def scalar_kernels(r, T):
    ops = BUTTERFLIES[r]
    body = scalar_ops(ops, T)
    L = []
    # stride 1, with twiddles; the input may be read with real and imaginary parts swapped
    L.append(f'    private void r{r}s1({T}[] x, int xo, int xr, {T}[] y, int yo, int m, {T}[] tw) {{')
    L.append('        int xi = 1 - xr;')
    L.append(f'        int sm = 2 * m;')
    L.append(f'        for (int p = 0; p < m; p++) {{')
    L.append(f'            int i = xo + 2 * p;')
    L.append(f'            int o = yo + {2 * r} * p;')
    L.append(f'            int t = {2 * (r - 1)} * p;')
    for k in range(r):
        idx = 'i' if k == 0 else (f'i + sm' if k == 1 else f'i + {k} * sm')
        L.append(f'            {T} x{k}r = x[{idx} + xr];')
        L.append(f'            {T} x{k}i = x[{idx} + xi];')
    L += ind(body, 12)
    L.append(f'            y[o] = y0r;')
    L.append(f'            y[o + 1] = y0i;')
    for j in range(1, r):
        L.append(f'            twiddle(y, o + {2 * j}, y{j}r, y{j}i, tw[t + {2 * (j - 1)}], tw[t + {2 * (j - 1) + 1}]);')
    L.append('        }')
    L.append('    }')
    L.append('')
    # general stride, with twiddles, no swaps
    L.append(f'    private void r{r}g({T}[] x, int xo, {T}[] y, int yo, int m, int stride, {T}[] tw) {{')
    L.append(f'        int sm = 2 * stride * m;')
    L.append(f'        int bs = 2 * stride;')
    L.append(f'        for (int p = 0; p < m; p++) {{')
    L.append(f'            int t = {2 * (r - 1)} * p;')
    for j in range(1, r):
        L.append(f'            {T} w{j}r = tw[t + {2 * (j - 1)}];')
        L.append(f'            {T} w{j}i = tw[t + {2 * (j - 1) + 1}];')
    L.append(f'            int ia = xo + bs * p;')
    L.append(f'            int ob = yo + bs * {r} * p;')
    L.append(f'            for (int q = 0; q < bs; q += 2) {{')
    L.append(f'                int i = ia + q;')
    L.append(f'                int o = ob + q;')
    for k in range(r):
        idx = 'i' if k == 0 else (f'i + sm' if k == 1 else f'i + {k} * sm')
        L.append(f'                {T} x{k}r = x[{idx}];')
        L.append(f'                {T} x{k}i = x[{idx} + 1];')
    L += ind(body, 16)
    L.append(f'                y[o] = y0r;')
    L.append(f'                y[o + 1] = y0i;')
    for j in range(1, r):
        oo = 'o + bs' if j == 1 else f'o + {j} * bs'
        L.append(f'                twiddle(y, {oo}, y{j}r, y{j}i, w{j}r, w{j}i);')
    L.append('            }')
    L.append('        }')
    L.append('    }')
    L.append('')
    # last stage (m = 1): no twiddles; swaps on either side (both only for a single-stage transform)
    L.append(f'    private void r{r}m1({T}[] x, int xo, int xr, {T}[] y, int yo, int yr, int stride) {{')
    L.append('        int xi = 1 - xr;')
    L.append('        int yi = 1 - yr;')
    L.append(f'        int bs = 2 * stride;')
    L.append(f'        for (int q = 0; q < bs; q += 2) {{')
    L.append(f'            int i = xo + q;')
    L.append(f'            int o = yo + q;')
    for k in range(r):
        idx = 'i' if k == 0 else (f'i + bs' if k == 1 else f'i + {k} * bs')
        L.append(f'            {T} x{k}r = x[{idx} + xr];')
        L.append(f'            {T} x{k}i = x[{idx} + xi];')
    L += ind(body, 12)
    for j in range(r):
        oo = 'o' if j == 0 else ('o + bs' if j == 1 else f'o + {j} * bs')
        L.append(f'            y[{oo} + yr] = y{j}r;')
        L.append(f'            y[{oo} + yi] = y{j}i;')
    L.append('        }')
    L.append('    }')
    L.append('')
    return L


def vector_kernels(r, T, P):
    V = 'DoubleVector' if T == 'double' else 'FloatVector'
    ops = BUTTERFLIES[r]
    body = vector_ops(ops, V)
    L = []
    L.append(f'        static void r{r}g({P} p, {T}[] x, int xo, {T}[] y, int yo, int m, int stride, {T}[] tw) {{')
    L.append(f'            int sm = 2 * stride * m;')
    L.append(f'            int bs = 2 * stride;')
    L.append(f'            int lanes = SPECIES.length();')
    L.append(f'            for (int q0 = 0; q0 < m; q0++) {{')
    L.append(f'                int t = {2 * (r - 1)} * q0;')
    for j in range(1, r):
        L.append(f'                {V} w{j}r = {V}.broadcast(SPECIES, tw[t + {2 * (j - 1)}]);')
        L.append(f'                {V} w{j}s = {V}.broadcast(SPECIES, tw[t + {2 * (j - 1) + 1}]).lanewise(VectorOperators.NEG, EVEN);')
    L.append(f'                int ia = xo + bs * q0;')
    L.append(f'                int ob = yo + bs * {r} * q0;')
    L.append(f'                for (int q = 0; q < bs; q += lanes) {{')
    L.append(f'                    int i = ia + q;')
    L.append(f'                    int o = ob + q;')
    for k in range(r):
        idx = 'i' if k == 0 else (f'i + sm' if k == 1 else f'i + {k} * sm')
        L.append(f'                    {V} x{k} = {V}.fromArray(SPECIES, x, {idx});')
    L += ind(body, 20)
    L.append(f'                    y0.intoArray(y, o);')
    for j in range(1, r):
        oo = 'o + bs' if j == 1 else f'o + {j} * bs'
        L.append(f'                    y{j}.mul(w{j}r).add(y{j}.rearrange(SWAP).mul(w{j}s)).intoArray(y, {oo});')
    L.append('                }')
    L.append('            }')
    L.append('        }')
    L.append('')
    L.append(f'        static void r{r}m1({P} p, {T}[] x, int xo, {T}[] y, int yo, boolean swapOut, int stride) {{')
    L.append(f'            int bs = 2 * stride;')
    L.append(f'            int lanes = SPECIES.length();')
    L.append(f'            for (int q = 0; q < bs; q += lanes) {{')
    L.append(f'                int i = xo + q;')
    L.append(f'                int o = yo + q;')
    for k in range(r):
        idx = 'i' if k == 0 else (f'i + bs' if k == 1 else f'i + {k} * bs')
        L.append(f'                {V} x{k} = {V}.fromArray(SPECIES, x, {idx});')
    L += ind(body, 16)
    for j in range(r):
        oo = 'o' if j == 0 else ('o + bs' if j == 1 else f'o + {j} * bs')
        L.append(f'                (swapOut ? y{j}.rearrange(SWAP) : y{j}).intoArray(y, {oo});')
    L.append('            }')
    L.append('        }')
    L.append('')
    return L


def generate(T, P):
    V = 'DoubleVector' if T == 'double' else 'FloatVector'
    B = 'Double' if T == 'double' else 'Float'
    one = '1.0' if T == 'double' else '1.0f'
    L = ['    // BEGIN GENERATED KERNELS: scripts/gen-fft-kernels.py; edit the generator, not this block.', '']
    L.append(f'''    /**
     * The unscaled transform. The inverse uses {{@code IDFT(x) = swap(DFT(swap(x)))}}, where {{@code swap}} exchanges
     * real and imaginary parts: the first pass reads swapped and the last pass writes swapped, so the inverse
     * costs no more than the forward transform.
     */
    void run({T}[] d, int off, {T}[] s, int soff, boolean inv) {{
        if (inner != null) {{
            bluestein(d, off, s, soff, inv);
            return;
        }}
        boolean simd = Simd.AVAILABLE && Simd.enabled();
        {T}[] src = d;
        int so = off;
        {T}[] dst = s;
        int dso = soff;
        int stride = 1;
        int len = n;
        int temp = soff + 2 * n;
        int last = radix.length - 1;
        if (last < 0) {{
            return; // n = 1
        }}
        for (int i = 0; i <= last; i++) {{
            int r = radix[i];
            int m = len / r;
            {T}[] tw = twiddle[i];
            int xr = inv && i == 0 ? 1 : 0;
            int yr = inv && i == last ? 1 : 0;
            boolean vec = simd && xr == 0 && Simd.fits(2 * stride);
            if (r > 5) {{
                radixP(src, so, xr, dst, dso, yr, r, m, stride, tw, primeRoots[i], s, temp);
            }} else if (m == 1) {{
                if (vec) {{
                    simdM1(r, src, so, dst, dso, yr == 1, stride);
                }} else {{
                    switch (r) {{
                        case 4: r4m1(src, so, xr, dst, dso, yr, stride); break;
                        case 2: r2m1(src, so, xr, dst, dso, yr, stride); break;
                        case 3: r3m1(src, so, xr, dst, dso, yr, stride); break;
                        default: r5m1(src, so, xr, dst, dso, yr, stride); break;
                    }}
                }}
            }} else if (stride == 1) {{
                switch (r) {{
                    case 4: r4s1(src, so, xr, dst, dso, m, tw); break;
                    case 2: r2s1(src, so, xr, dst, dso, m, tw); break;
                    case 3: r3s1(src, so, xr, dst, dso, m, tw); break;
                    default: r5s1(src, so, xr, dst, dso, m, tw); break;
                }}
            }} else if (vec) {{
                simdGeneral(r, src, so, dst, dso, m, stride, tw);
            }} else {{
                switch (r) {{
                    case 4: r4g(src, so, dst, dso, m, stride, tw); break;
                    case 2: r2g(src, so, dst, dso, m, stride, tw); break;
                    case 3: r3g(src, so, dst, dso, m, stride, tw); break;
                    default: r5g(src, so, dst, dso, m, stride, tw); break;
                }}
            }}
            {T}[] ta = src;
            src = dst;
            dst = ta;
            int to = so;
            so = dso;
            dso = to;
            stride *= r;
            len = m;
        }}
        if (src != d || so != off) {{
            System.arraycopy(src, so, d, off, 2 * n);
        }}
    }}

    private static void twiddle({T}[] y, int o, {T} vr, {T} vi, {T} wr, {T} wi) {{
        y[o] = vr * wr - vi * wi;
        y[o + 1] = vr * wi + vi * wr;
    }}
''')
    for r in (2, 4, 3, 5):
        L += scalar_kernels(r, T)
    zero = '0.0' if T == 'double' else '0.0f'
    L.append(f'''    /** A direct butterfly for an odd prime radix, using {{@code 4r}} values of scratch at {{@code temp}}. */
    private static void radixP({T}[] x, int xo, int xr, {T}[] y, int yo, int yr, int r, int m, int stride,
            {T}[] tw, {T}[] roots, {T}[] s, int temp) {{
        int xi = 1 - xr;
        int yi = 1 - yr;
        int sm = 2 * stride * m;
        int h = (r - 1) / 2;
        int bsum = temp;          // b_k = x_k + x_(r-k), k = 1..h
        int bdif = temp + 2 * r;  // d_k = x_k - x_(r-k)
        int bs = 2 * stride;
        for (int p = 0; p < m; p++) {{
            int t = 2 * (r - 1) * p;
            int a0 = xo + bs * p;
            int b0 = yo + bs * r * p;
            for (int q = 0; q < bs; q += 2) {{
                int i0 = a0 + q;
                {T} x0r = x[i0 + xr];
                {T} x0i = x[i0 + xi];
                {T} sumr = x0r;
                {T} sumi = x0i;
                for (int k = 1; k <= h; k++) {{
                    int ik = i0 + k * sm;
                    int il = i0 + (r - k) * sm;
                    {T} br = x[ik + xr] + x[il + xr];
                    {T} bi = x[ik + xi] + x[il + xi];
                    s[bsum + 2 * k] = br;
                    s[bsum + 2 * k + 1] = bi;
                    s[bdif + 2 * k] = x[ik + xr] - x[il + xr];
                    s[bdif + 2 * k + 1] = x[ik + xi] - x[il + xi];
                    sumr += br;
                    sumi += bi;
                }}
                int o = b0 + q;
                {T}[] out = y;
                for (int j = 1; j <= h; j++) {{
                    // root^(jk) = cos(2π jk/r) - i sin(2π jk/r)
                    {T} rr = x0r;
                    {T} ri = x0i;
                    {T} ir = {zero};
                    {T} ii = {zero};
                    int e = 0;
                    for (int k = 1; k <= h; k++) {{
                        e += j;
                        if (e >= r) {{
                            e -= r;
                        }}
                        {T} c = roots[2 * e];
                        {T} sn = -roots[2 * e + 1];
                        rr += c * s[bsum + 2 * k];
                        ri += c * s[bsum + 2 * k + 1];
                        ir += sn * s[bdif + 2 * k];
                        ii += sn * s[bdif + 2 * k + 1];
                    }}
                    // y_j = R - i·I, y_(r-j) = R + i·I, then the stage twiddles
                    int oj = o + j * bs;
                    int ok = o + (r - j) * bs;
                    {T} ajr = rr + ii;
                    {T} aji = ri - ir;
                    {T} akr = rr - ii;
                    {T} aki = ri + ir;
                    if (m == 1) {{
                        out[oj + yr] = ajr;
                        out[oj + yi] = aji;
                        out[ok + yr] = akr;
                        out[ok + yi] = aki;
                    }} else {{
                        {T} wjr = tw[t + 2 * (j - 1)];
                        {T} wji = tw[t + 2 * (j - 1) + 1];
                        {T} wkr = tw[t + 2 * (r - j - 1)];
                        {T} wki = tw[t + 2 * (r - j - 1) + 1];
                        out[oj + yr] = ajr * wjr - aji * wji;
                        out[oj + yi] = ajr * wji + aji * wjr;
                        out[ok + yr] = akr * wkr - aki * wki;
                        out[ok + yi] = akr * wki + aki * wkr;
                    }}
                }}
                out[o + yr] = sumr;
                out[o + yi] = sumi;
            }}
        }}
    }}

    private void simdGeneral(int r, {T}[] x, int xo, {T}[] y, int yo, int m, int stride, {T}[] tw) {{
//#ifdef __HAS_VECTOR_API__
        switch (r) {{
            case 4: Simd.r4g(this, x, xo, y, yo, m, stride, tw); break;
            case 2: Simd.r2g(this, x, xo, y, yo, m, stride, tw); break;
            case 3: Simd.r3g(this, x, xo, y, yo, m, stride, tw); break;
            default: Simd.r5g(this, x, xo, y, yo, m, stride, tw); break;
        }}
//#else
        throw new AssertionError("no Vector API");
//#endif
    }}

    private void simdM1(int r, {T}[] x, int xo, {T}[] y, int yo, boolean swapOut, int stride) {{
//#ifdef __HAS_VECTOR_API__
        switch (r) {{
            case 4: Simd.r4m1(this, x, xo, y, yo, swapOut, stride); break;
            case 2: Simd.r2m1(this, x, xo, y, yo, swapOut, stride); break;
            case 3: Simd.r3m1(this, x, xo, y, yo, swapOut, stride); break;
            default: Simd.r5m1(this, x, xo, y, yo, swapOut, stride); break;
        }}
//#else
        throw new AssertionError("no Vector API");
//#endif
    }}

    /**
     * Vector API kernels: the scalar operations lane by lane, so the results are the same bits. Kept in a holder so
     * the scalar build never loads {{@code jdk.incubator.vector}}.
     */
    private static final class Simd {{
//#ifdef __HAS_VECTOR_API__
        static final VectorSpecies<{B}> SPECIES = {V}.SPECIES_PREFERRED;
        static final boolean AVAILABLE = SimdSupport.isVectorApiAvailable() && SPECIES.length() >= 2;
        static final int LANES = SPECIES.length();
        static final VectorShuffle<{B}> SWAP = VectorShuffle.fromOp(SPECIES, i -> i ^ 1);
        static final VectorMask<{B}> EVEN = VectorMask.fromLong(SPECIES, 0x5555555555555555L);
        static final VectorMask<{B}> ODD = EVEN.not();
        static final {T} HALF = {'0.5' if T == 'double' else '0.5f'};

        private Simd() {{
        }}

        /** Whether SIMD is enabled now ({{@code KernelConfig.setSimdEnabled}}). */
        static boolean enabled() {{
            return SimdSupport.backend() == SimdSupport.Backend.VECTOR_API;
        }}

        /** Whether a run of {{@code count}} values splits into whole vectors. */
        static boolean fits(int count) {{
            return count % LANES == 0;
        }}
''')
    for r in (2, 4, 3, 5):
        L += vector_kernels(r, T, P)
    L.append('''//#else
        static final boolean AVAILABLE = false;

        private Simd() {
        }

        static boolean enabled() {
            return false;
        }

        static boolean fits(int count) {
            return false;
        }
//#endif
    }

    // END GENERATED KERNELS''')
    return '\n'.join(L) + '\n'


if __name__ == '__main__':
    T, P, path = sys.argv[1], sys.argv[2], sys.argv[3]
    s = open(path).read()
    i = s.index('    // BEGIN GENERATED KERNELS')
    j = s.index('    // END GENERATED KERNELS') + len('    // END GENERATED KERNELS\n')
    s = s[:i] + generate(T, P) + s[j:]
    open(path, 'w').write(s)
