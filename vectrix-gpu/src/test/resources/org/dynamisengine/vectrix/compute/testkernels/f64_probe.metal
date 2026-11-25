#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
bool dIsNaN_0(ulong a_0)
{
    return (a_0 & 9223372036854775807ULL) > 9218868437227405312ULL;
}

bool dIsInf_0(ulong a_1)
{
    return (a_1 & 9223372036854775807ULL) == 9218868437227405312ULL;
}

bool dIsZero_0(ulong a_2)
{
    return (a_2 & 9223372036854775807ULL) == 0ULL;
}

void dUnpack_0(ulong a_3, ulong thread* sig_0, int thread* e_0)
{
    int be_0 = int((a_3 >> 52U) & 2047ULL);
    *sig_0 = a_3 & 4503599627370495ULL;
    if(be_0 == int(0))
    {
        *e_0 = int(-1074);
    }
    else
    {
        *sig_0 = (*sig_0) | 4503599627370496ULL;
        *e_0 = be_0 - int(1075);
    }
    return;
}

void dUnpack_1(ulong a_4, ulong thread* sig_1, int thread* e_1)
{
    int be_1 = int((a_4 >> 52U) & 2047ULL);
    *sig_1 = a_4 & 4503599627370495ULL;
    if(be_1 == int(0))
    {
        *e_1 = int(-1074);
    }
    else
    {
        *sig_1 = (*sig_1) | 4503599627370496ULL;
        *e_1 = be_1 - int(1075);
    }
    return;
}

struct U128_0
{
    ulong hi_0;
    ulong lo_0;
};

U128_0 u128_0(ulong hi_1, ulong lo_1)
{
    thread U128_0 r_0;
    (&r_0)->hi_0 = hi_1;
    (&r_0)->lo_0 = lo_1;
    return r_0;
}

U128_0 mul64_0(ulong a_5, ulong b_0)
{
    ulong a0_0 = a_5 & 4294967295ULL;
    ulong a1_0 = a_5 >> 32U;
    ulong b0_0 = b_0 & 4294967295ULL;
    ulong b1_0 = b_0 >> 32U;
    ulong p00_0 = a0_0 * b0_0;
    ulong p01_0 = a0_0 * b1_0;
    ulong p10_0 = a1_0 * b0_0;
    ulong mid_0 = (p00_0 >> 32U) + (p01_0 & 4294967295ULL) + (p10_0 & 4294967295ULL);
    return u128_0(a1_0 * b1_0 + (p01_0 >> 32U) + (p10_0 >> 32U) + (mid_0 >> 32U), (mid_0 << 32U) | (p00_0 & 4294967295ULL));
}

int bitLength64_0(ulong x_0)
{
    if(x_0 == 0ULL)
    {
        return int(0);
    }
    ulong _S1 = x_0 >> 32U;
    ulong _S2;
    int n_0;
    if(_S1 != 0ULL)
    {
        _S2 = _S1;
        n_0 = int(33);
    }
    else
    {
        _S2 = x_0;
        n_0 = int(1);
    }
    ulong _S3 = _S2 >> 16U;
    if(_S3 != 0ULL)
    {
        int n_1 = n_0 + int(16);
        _S2 = _S3;
        n_0 = n_1;
    }
    ulong _S4 = _S2 >> 8U;
    if(_S4 != 0ULL)
    {
        int n_2 = n_0 + int(8);
        _S2 = _S4;
        n_0 = n_2;
    }
    ulong _S5 = _S2 >> 4U;
    if(_S5 != 0ULL)
    {
        int n_3 = n_0 + int(4);
        _S2 = _S5;
        n_0 = n_3;
    }
    ulong _S6 = _S2 >> 2U;
    if(_S6 != 0ULL)
    {
        int n_4 = n_0 + int(2);
        _S2 = _S6;
        n_0 = n_4;
    }
    if((_S2 >> 1U) != 0ULL)
    {
        n_0 = n_0 + int(1);
    }
    return n_0;
}

int bitLength128_0(const U128_0 thread* x_1)
{
    ulong _S7 = x_1->hi_0;
    int _S8;
    if((x_1->hi_0) != 0ULL)
    {
        _S8 = int(64) + bitLength64_0(_S7);
    }
    else
    {
        _S8 = bitLength64_0(x_1->lo_0);
    }
    return _S8;
}

int bitLength128_1(const U128_0 thread* x_2)
{
    ulong _S9 = x_2->hi_0;
    int _S10;
    if((x_2->hi_0) != 0ULL)
    {
        _S10 = int(64) + bitLength64_0(_S9);
    }
    else
    {
        _S10 = bitLength64_0(x_2->lo_0);
    }
    return _S10;
}

U128_0 shl128_0(const U128_0 thread* x_3, int s_0)
{
    if(s_0 <= int(0))
    {
        return *x_3;
    }
    if(s_0 >= int(128))
    {
        return u128_0(0ULL, 0ULL);
    }
    if(s_0 >= int(64))
    {
        return u128_0((x_3->lo_0) << ulong(s_0 - int(64)), 0ULL);
    }
    ulong _S11 = ulong(s_0);
    return u128_0(((x_3->hi_0) << _S11) | ((x_3->lo_0) >> ulong(int(64) - s_0)), (x_3->lo_0) << _S11);
}

U128_0 shl128_1(const U128_0 thread* x_4, int s_1)
{
    if(s_1 <= int(0))
    {
        return *x_4;
    }
    if(s_1 >= int(128))
    {
        return u128_0(0ULL, 0ULL);
    }
    if(s_1 >= int(64))
    {
        return u128_0((x_4->lo_0) << ulong(s_1 - int(64)), 0ULL);
    }
    ulong _S12 = ulong(s_1);
    return u128_0(((x_4->hi_0) << _S12) | ((x_4->lo_0) >> ulong(int(64) - s_1)), (x_4->lo_0) << _S12);
}

bool less128_0(const U128_0 thread* a_6, const U128_0 thread* b_1)
{
    ulong _S13 = a_6->hi_0;
    ulong _S14 = b_1->hi_0;
    bool _S15;
    if((a_6->hi_0) < (b_1->hi_0))
    {
        _S15 = true;
    }
    else
    {
        if(_S13 == _S14)
        {
            _S15 = (a_6->lo_0) < (b_1->lo_0);
        }
        else
        {
            _S15 = false;
        }
    }
    return _S15;
}

bool less128_1(const U128_0 thread* a_7, const U128_0 thread* b_2)
{
    ulong _S16 = a_7->hi_0;
    ulong _S17 = b_2->hi_0;
    bool _S18;
    if((a_7->hi_0) < (b_2->hi_0))
    {
        _S18 = true;
    }
    else
    {
        if(_S16 == _S17)
        {
            _S18 = (a_7->lo_0) < (b_2->lo_0);
        }
        else
        {
            _S18 = false;
        }
    }
    return _S18;
}

ulong dRoundPack_0(ulong sign_0, const U128_0 thread* r_1, int e_2)
{
    int _S19 = bitLength128_1(r_1);
    int _S20 = max(_S19 - int(1) - int(52), int(-1074) - e_2);
    ulong q_0;
    if(_S20 <= int(0))
    {
        U128_0 _S21 = shl128_1(r_1, - _S20);
        q_0 = _S21.lo_0;
    }
    else
    {
        if(_S20 > int(128))
        {
            q_0 = 0ULL;
        }
        else
        {
            U128_0 qq_0;
            U128_0 rem_0;
            if(_S20 == int(128))
            {
                qq_0 = u128_0(0ULL, 0ULL);
                rem_0 = *r_1;
            }
            else
            {
                if(_S20 >= int(64))
                {
                    int t_0 = _S20 - int(64);
                    bool _S22 = t_0 == int(0);
                    if(_S22)
                    {
                        q_0 = r_1->hi_0;
                    }
                    else
                    {
                        q_0 = (r_1->hi_0) >> ulong(t_0);
                    }
                    U128_0 _S23 = u128_0(0ULL, q_0);
                    if(_S22)
                    {
                        q_0 = 0ULL;
                    }
                    else
                    {
                        q_0 = (r_1->hi_0) & ((1ULL << ulong(t_0)) - 1ULL);
                    }
                    U128_0 _S24 = u128_0(q_0, r_1->lo_0);
                    qq_0 = _S23;
                    rem_0 = _S24;
                }
                else
                {
                    ulong _S25 = ulong(_S20);
                    U128_0 _S26 = u128_0(0ULL, (r_1->lo_0) & ((1ULL << _S25) - 1ULL));
                    qq_0 = u128_0((r_1->hi_0) >> _S25, ((r_1->lo_0) >> _S25) | ((r_1->hi_0) << ulong(int(64) - _S20)));
                    rem_0 = _S26;
                }
            }
            int _S27 = _S20 - int(1);
            thread U128_0 _S28 = u128_0(0ULL, 1ULL);
            U128_0 _S29 = shl128_1(&_S28, _S27);
            U128_0 _S30 = qq_0;
            thread U128_0 _S31 = _S29;
            thread U128_0 _S32 = rem_0;
            bool _S33 = less128_1(&_S31, &_S32);
            bool _S34;
            if(_S33)
            {
                _S34 = true;
            }
            else
            {
                if((rem_0.hi_0) == (_S29.hi_0))
                {
                    _S34 = (rem_0.lo_0) == (_S29.lo_0);
                }
                else
                {
                    _S34 = false;
                }
                if(_S34)
                {
                    _S34 = ((_S30.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S34 = false;
                }
            }
            if(_S34)
            {
                q_0 = _S30.lo_0 + 1ULL;
            }
            else
            {
                q_0 = _S30.lo_0;
            }
        }
    }
    int biasedMinusOne_0 = e_2 + _S20 + int(1074);
    if(biasedMinusOne_0 >= int(2046))
    {
        return sign_0 | 9218868437227405312ULL;
    }
    ulong bits_0 = (ulong(biasedMinusOne_0) << 52U) + q_0;
    if(bits_0 >= 9218868437227405312ULL)
    {
        q_0 = 9218868437227405312ULL;
    }
    else
    {
        q_0 = bits_0;
    }
    return sign_0 | q_0;
}

ulong dRoundPack_1(ulong sign_1, const U128_0 thread* r_2, int e_3)
{
    int _S35 = bitLength128_1(r_2);
    int _S36 = max(_S35 - int(1) - int(52), int(-1074) - e_3);
    ulong q_1;
    if(_S36 <= int(0))
    {
        U128_0 _S37 = shl128_1(r_2, - _S36);
        q_1 = _S37.lo_0;
    }
    else
    {
        if(_S36 > int(128))
        {
            q_1 = 0ULL;
        }
        else
        {
            U128_0 qq_1;
            U128_0 rem_1;
            if(_S36 == int(128))
            {
                qq_1 = u128_0(0ULL, 0ULL);
                rem_1 = *r_2;
            }
            else
            {
                if(_S36 >= int(64))
                {
                    int t_1 = _S36 - int(64);
                    bool _S38 = t_1 == int(0);
                    if(_S38)
                    {
                        q_1 = r_2->hi_0;
                    }
                    else
                    {
                        q_1 = (r_2->hi_0) >> ulong(t_1);
                    }
                    U128_0 _S39 = u128_0(0ULL, q_1);
                    if(_S38)
                    {
                        q_1 = 0ULL;
                    }
                    else
                    {
                        q_1 = (r_2->hi_0) & ((1ULL << ulong(t_1)) - 1ULL);
                    }
                    U128_0 _S40 = u128_0(q_1, r_2->lo_0);
                    qq_1 = _S39;
                    rem_1 = _S40;
                }
                else
                {
                    ulong _S41 = ulong(_S36);
                    U128_0 _S42 = u128_0(0ULL, (r_2->lo_0) & ((1ULL << _S41) - 1ULL));
                    qq_1 = u128_0((r_2->hi_0) >> _S41, ((r_2->lo_0) >> _S41) | ((r_2->hi_0) << ulong(int(64) - _S36)));
                    rem_1 = _S42;
                }
            }
            int _S43 = _S36 - int(1);
            thread U128_0 _S44 = u128_0(0ULL, 1ULL);
            U128_0 _S45 = shl128_1(&_S44, _S43);
            U128_0 _S46 = qq_1;
            thread U128_0 _S47 = _S45;
            thread U128_0 _S48 = rem_1;
            bool _S49 = less128_1(&_S47, &_S48);
            bool _S50;
            if(_S49)
            {
                _S50 = true;
            }
            else
            {
                if((rem_1.hi_0) == (_S45.hi_0))
                {
                    _S50 = (rem_1.lo_0) == (_S45.lo_0);
                }
                else
                {
                    _S50 = false;
                }
                if(_S50)
                {
                    _S50 = ((_S46.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S50 = false;
                }
            }
            if(_S50)
            {
                q_1 = _S46.lo_0 + 1ULL;
            }
            else
            {
                q_1 = _S46.lo_0;
            }
        }
    }
    int biasedMinusOne_1 = e_3 + _S36 + int(1074);
    if(biasedMinusOne_1 >= int(2046))
    {
        return sign_1 | 9218868437227405312ULL;
    }
    ulong bits_1 = (ulong(biasedMinusOne_1) << 52U) + q_1;
    if(bits_1 >= 9218868437227405312ULL)
    {
        q_1 = 9218868437227405312ULL;
    }
    else
    {
        q_1 = bits_1;
    }
    return sign_1 | q_1;
}

U128_0 shrSticky128_0(const U128_0 thread* x_5, int s_2)
{
    if(s_2 <= int(0))
    {
        return *x_5;
    }
    if(s_2 >= int(128))
    {
        int _S51;
        if(((x_5->hi_0) | (x_5->lo_0)) != 0ULL)
        {
            _S51 = int(1);
        }
        else
        {
            _S51 = int(0);
        }
        return u128_0(0ULL, ulong(_S51));
    }
    thread U128_0 r_3;
    ulong lost_0;
    if(s_2 >= int(64))
    {
        int t_2 = s_2 - int(64);
        bool _S52 = t_2 == int(0);
        if(_S52)
        {
            lost_0 = x_5->hi_0;
        }
        else
        {
            lost_0 = (x_5->hi_0) >> ulong(t_2);
        }
        r_3 = u128_0(0ULL, lost_0);
        ulong _S53 = x_5->lo_0;
        if(_S52)
        {
            lost_0 = 0ULL;
        }
        else
        {
            lost_0 = (x_5->hi_0) << ulong(int(64) - t_2);
        }
        lost_0 = _S53 | lost_0;
    }
    else
    {
        ulong _S54 = ulong(s_2);
        ulong _S55 = ulong(int(64) - s_2);
        r_3 = u128_0((x_5->hi_0) >> _S54, ((x_5->lo_0) >> _S54) | ((x_5->hi_0) << _S55));
        lost_0 = (x_5->lo_0) << _S55;
    }
    if(lost_0 != 0ULL)
    {
        (&r_3)->lo_0 = ((&r_3)->lo_0) | 1ULL;
    }
    return r_3;
}

U128_0 add128_0(const U128_0 thread* a_8, const U128_0 thread* b_3)
{
    ulong lo_2 = a_8->lo_0 + b_3->lo_0;
    ulong _S56 = a_8->hi_0 + b_3->hi_0;
    int _S57;
    if(lo_2 < (a_8->lo_0))
    {
        _S57 = int(1);
    }
    else
    {
        _S57 = int(0);
    }
    return u128_0(_S56 + ulong(_S57), lo_2);
}

U128_0 sub128_0(const U128_0 thread* a_9, const U128_0 thread* b_4)
{
    ulong _S58 = a_9->hi_0 - b_4->hi_0;
    ulong _S59 = a_9->lo_0;
    ulong _S60 = b_4->lo_0;
    int _S61;
    if((a_9->lo_0) < (b_4->lo_0))
    {
        _S61 = int(1);
    }
    else
    {
        _S61 = int(0);
    }
    return u128_0(_S58 - ulong(_S61), _S59 - _S60);
}

ulong dFma_0(ulong a_10, ulong b_5, ulong c_0)
{
    bool _S62;
    if(dIsNaN_0(a_10))
    {
        _S62 = true;
    }
    else
    {
        _S62 = dIsNaN_0(b_5);
    }
    if(_S62)
    {
        _S62 = true;
    }
    else
    {
        _S62 = dIsNaN_0(c_0);
    }
    if(_S62)
    {
        return 9221120237041090560ULL;
    }
    ulong sp_0 = (a_10 ^ b_5) & 9223372036854775808ULL;
    ulong sc_0 = c_0 & 9223372036854775808ULL;
    if(dIsInf_0(a_10))
    {
        _S62 = true;
    }
    else
    {
        _S62 = dIsInf_0(b_5);
    }
    if(_S62)
    {
        if(dIsZero_0(a_10))
        {
            _S62 = true;
        }
        else
        {
            _S62 = dIsZero_0(b_5);
        }
        if(_S62)
        {
            return 9221120237041090560ULL;
        }
        if(dIsInf_0(c_0))
        {
            _S62 = sc_0 != sp_0;
        }
        else
        {
            _S62 = false;
        }
        if(_S62)
        {
            return 9221120237041090560ULL;
        }
        return sp_0 | 9218868437227405312ULL;
    }
    if(dIsInf_0(c_0))
    {
        return c_0;
    }
    if(dIsZero_0(a_10))
    {
        _S62 = true;
    }
    else
    {
        _S62 = dIsZero_0(b_5);
    }
    if(_S62)
    {
        if(dIsZero_0(c_0))
        {
            ulong _S63;
            if(sp_0 == sc_0)
            {
                _S63 = c_0;
            }
            else
            {
                _S63 = 0ULL;
            }
            return _S63;
        }
        return c_0;
    }
    thread ulong ma_0;
    thread int ea_0;
    dUnpack_0(a_10, &ma_0, &ea_0);
    thread ulong mb_0;
    thread int eb_0;
    dUnpack_0(b_5, &mb_0, &eb_0);
    U128_0 p_0 = mul64_0(ma_0, mb_0);
    int ep_0 = ea_0 + eb_0;
    if(dIsZero_0(c_0))
    {
        thread U128_0 _S64 = p_0;
        ulong _S65 = dRoundPack_0(sp_0, &_S64, ep_0);
        return _S65;
    }
    thread ulong mc_0;
    thread int ec_0;
    dUnpack_0(c_0, &mc_0, &ec_0);
    U128_0 cc_0 = u128_0(0ULL, mc_0);
    thread U128_0 _S66 = p_0;
    int _S67 = bitLength128_0(&_S66);
    int e_4 = max(ep_0 + _S67 - int(1), ec_0 + bitLength64_0(mc_0) - int(1)) - int(125);
    int _S68 = ep_0 - e_4;
    U128_0 x_6;
    if(_S68 >= int(0))
    {
        thread U128_0 _S69 = p_0;
        U128_0 _S70 = shl128_0(&_S69, _S68);
        x_6 = _S70;
    }
    else
    {
        int _S71 = e_4 - ep_0;
        thread U128_0 _S72 = p_0;
        U128_0 _S73 = shrSticky128_0(&_S72, _S71);
        x_6 = _S73;
    }
    U128_0 y_0;
    if((ec_0 - e_4) >= int(0))
    {
        int _S74 = ec_0 - e_4;
        thread U128_0 _S75 = cc_0;
        U128_0 _S76 = shl128_0(&_S75, _S74);
        y_0 = _S76;
    }
    else
    {
        int _S77 = e_4 - ec_0;
        thread U128_0 _S78 = cc_0;
        U128_0 _S79 = shrSticky128_0(&_S78, _S77);
        y_0 = _S79;
    }
    if(sp_0 == sc_0)
    {
        thread U128_0 _S80 = x_6;
        thread U128_0 _S81 = y_0;
        U128_0 _S82 = add128_0(&_S80, &_S81);
        thread U128_0 _S83 = _S82;
        ulong _S84 = dRoundPack_0(sp_0, &_S83, e_4);
        return _S84;
    }
    if((x_6.hi_0) == (y_0.hi_0))
    {
        _S62 = (x_6.lo_0) == (y_0.lo_0);
    }
    else
    {
        _S62 = false;
    }
    if(_S62)
    {
        return 0ULL;
    }
    thread U128_0 _S85 = x_6;
    thread U128_0 _S86 = y_0;
    bool _S87 = less128_0(&_S85, &_S86);
    if(_S87)
    {
        thread U128_0 _S88 = y_0;
        thread U128_0 _S89 = x_6;
        U128_0 _S90 = sub128_0(&_S88, &_S89);
        thread U128_0 _S91 = _S90;
        ulong _S92 = dRoundPack_0(sc_0, &_S91, e_4);
        return _S92;
    }
    thread U128_0 _S93 = x_6;
    thread U128_0 _S94 = y_0;
    U128_0 _S95 = sub128_0(&_S93, &_S94);
    thread U128_0 _S96 = _S95;
    ulong _S97 = dRoundPack_0(sp_0, &_S96, e_4);
    return _S97;
}

ulong dMul_0(ulong a_11, ulong b_6)
{
    return dFma_0(a_11, b_6, 9223372036854775808ULL);
}

ulong dAdd_0(ulong a_12, ulong b_7)
{
    return dFma_0(a_12, 4607182418800017408ULL, b_7);
}

ulong dNeg_0(ulong a_13)
{
    return a_13 ^ 9223372036854775808ULL;
}

ulong dSub_0(ulong a_14, ulong b_8)
{
    return dAdd_0(a_14, dNeg_0(b_8));
}

ulong dDiv_0(ulong a_15, ulong b_9)
{
    bool _S98;
    if(dIsNaN_0(a_15))
    {
        _S98 = true;
    }
    else
    {
        _S98 = dIsNaN_0(b_9);
    }
    if(_S98)
    {
        return 9221120237041090560ULL;
    }
    ulong s_3 = (a_15 ^ b_9) & 9223372036854775808ULL;
    ulong q_2;
    if(dIsInf_0(a_15))
    {
        if(dIsInf_0(b_9))
        {
            q_2 = 9221120237041090560ULL;
        }
        else
        {
            q_2 = s_3 | 9218868437227405312ULL;
        }
        return q_2;
    }
    if(dIsInf_0(b_9))
    {
        return s_3;
    }
    if(dIsZero_0(b_9))
    {
        if(dIsZero_0(a_15))
        {
            q_2 = 9221120237041090560ULL;
        }
        else
        {
            q_2 = s_3 | 9218868437227405312ULL;
        }
        return q_2;
    }
    if(dIsZero_0(a_15))
    {
        return s_3;
    }
    thread ulong ma_1;
    thread int ea_1;
    dUnpack_1(a_15, &ma_1, &ea_1);
    thread ulong mb_1;
    thread int eb_1;
    dUnpack_1(b_9, &mb_1, &eb_1);
    int na_0 = int(53) - bitLength64_0(ma_1);
    int nb_0 = int(53) - bitLength64_0(mb_1);
    ulong _S99 = ma_1 << ulong(na_0);
    ma_1 = _S99;
    mb_1 = mb_1 << ulong(nb_0);
    ea_1 = ea_1 - na_0;
    eb_1 = eb_1 - nb_0;
    int i_0 = int(0);
    q_2 = 0ULL;
    ulong rem_2 = _S99;
    for(;;)
    {
        if(i_0 < int(58))
        {
        }
        else
        {
            break;
        }
        ulong q_3 = q_2 << 1U;
        ulong rem_3;
        if(rem_2 >= mb_1)
        {
            ulong q_4 = q_3 | 1ULL;
            rem_3 = rem_2 - mb_1;
            q_2 = q_4;
        }
        else
        {
            rem_3 = rem_2;
            q_2 = q_3;
        }
        ulong rem_4 = rem_3 << 1U;
        i_0 = i_0 + int(1);
        rem_2 = rem_4;
    }
    if(rem_2 != 0ULL)
    {
        q_2 = q_2 | 1ULL;
    }
    int _S100 = ea_1 - eb_1 - int(57);
    thread U128_0 _S101 = u128_0(0ULL, q_2);
    ulong _S102 = dRoundPack_1(s_3, &_S101, _S100);
    return _S102;
}

ulong dFromFloat_0(float f_0)
{
    uint u_0 = (as_type<uint>((f_0)));
    ulong sign_2 = ulong(u_0 & 2147483648U) << 32U;
    uint be_2 = (u_0 >> 23U) & 255U;
    uint m_0 = u_0 & 8388607U;
    if(be_2 == 255U)
    {
        ulong _S103;
        if(m_0 != 0U)
        {
            _S103 = 9221120237041090560ULL;
        }
        else
        {
            _S103 = sign_2 | 9218868437227405312ULL;
        }
        return _S103;
    }
    if(be_2 == 0U)
    {
        if(m_0 == 0U)
        {
            return sign_2;
        }
        thread U128_0 _S104 = u128_0(0ULL, ulong(m_0));
        ulong _S105 = dRoundPack_1(sign_2, &_S104, int(-149));
        return _S105;
    }
    return (sign_2 | (ulong(be_2 - 127U + 1023U) << 52U)) | (ulong(m_0) << 29U);
}

float dToFloat_0(ulong a_16)
{
    uint sign_3 = uint(a_16 >> 32U) & 2147483648U;
    if(dIsNaN_0(a_16))
    {
        return (as_type<float>((2143289344U)));
    }
    if(dIsInf_0(a_16))
    {
        return (as_type<float>((sign_3 | 2139095040U)));
    }
    if(dIsZero_0(a_16))
    {
        return (as_type<float>((sign_3)));
    }
    thread ulong sig_2;
    thread int e_5;
    dUnpack_1(a_16, &sig_2, &e_5);
    int _S106 = max(bitLength64_0(sig_2) - int(1) - int(23), int(-149) - e_5);
    ulong q_5;
    if(_S106 >= int(64))
    {
        q_5 = 0ULL;
    }
    else
    {
        ulong _S107 = ulong(_S106);
        ulong q_6 = sig_2 >> _S107;
        ulong rem_5 = sig_2 & ((1ULL << _S107) - 1ULL);
        ulong halfway_0 = 1ULL << ulong(_S106 - int(1));
        bool _S108;
        if(rem_5 > halfway_0)
        {
            _S108 = true;
        }
        else
        {
            if(rem_5 == halfway_0)
            {
                _S108 = (q_6 & 1ULL) != 0ULL;
            }
            else
            {
                _S108 = false;
            }
        }
        if(_S108)
        {
            q_5 = q_6 + 1ULL;
        }
        else
        {
            q_5 = q_6;
        }
    }
    int biasedMinusOne_2 = e_5 + _S106 + int(149);
    if(biasedMinusOne_2 >= int(254))
    {
        return (as_type<float>((sign_3 | 2139095040U)));
    }
    uint bits_2 = uint(biasedMinusOne_2) * 8388608U + uint(q_5);
    if(bits_2 >= 2139095040U)
    {
        return (as_type<float>((sign_3 | 2139095040U)));
    }
    return (as_type<float>((sign_3 | bits_2)));
}

long dToLong_0(ulong a_17)
{
    bool _S109;
    if(dIsNaN_0(a_17))
    {
        _S109 = true;
    }
    else
    {
        _S109 = dIsZero_0(a_17);
    }
    if(_S109)
    {
        return 0LL;
    }
    bool negative_0 = (a_17 & 9223372036854775808ULL) != 0ULL;
    long _S110;
    if(dIsInf_0(a_17))
    {
        if(negative_0)
        {
            _S110 = -9223372036854775808LL;
        }
        else
        {
            _S110 = 9223372036854775807LL;
        }
        return _S110;
    }
    thread ulong sig_3;
    thread int e_6;
    dUnpack_1(a_17, &sig_3, &e_6);
    if((e_6 + bitLength64_0(sig_3)) > int(63))
    {
        if(negative_0)
        {
            _S110 = -9223372036854775808LL;
        }
        else
        {
            _S110 = 9223372036854775807LL;
        }
        return _S110;
    }
    ulong m_1;
    if(e_6 >= int(0))
    {
        m_1 = sig_3 << ulong(e_6);
    }
    else
    {
        if(e_6 <= int(-64))
        {
            m_1 = 0ULL;
        }
        else
        {
            m_1 = sig_3 >> ulong(- e_6);
        }
    }
    long v_0 = long(m_1);
    if(negative_0)
    {
        _S110 = - v_0;
    }
    else
    {
        _S110 = v_0;
    }
    return _S110;
}

ulong dFromLong_0(long v_1)
{
    if(v_1 == 0LL)
    {
        return 0ULL;
    }
    bool _S111 = v_1 < 0LL;
    ulong sign_4;
    if(_S111)
    {
        sign_4 = 9223372036854775808ULL;
    }
    else
    {
        sign_4 = 0ULL;
    }
    ulong m_2;
    if(_S111)
    {
        m_2 = ulong(- v_1);
    }
    else
    {
        m_2 = ulong(v_1);
    }
    thread U128_0 _S112 = u128_0(0ULL, m_2);
    ulong _S113 = dRoundPack_1(sign_4, &_S112, int(0));
    return _S113;
}

bool dLess_0(ulong a_18, ulong b_10)
{
    bool _S114;
    if(dIsNaN_0(a_18))
    {
        _S114 = true;
    }
    else
    {
        _S114 = dIsNaN_0(b_10);
    }
    if(_S114)
    {
        return false;
    }
    if(dIsZero_0(a_18))
    {
        _S114 = dIsZero_0(b_10);
    }
    else
    {
        _S114 = false;
    }
    if(_S114)
    {
        return false;
    }
    bool na_1 = (a_18 & 9223372036854775808ULL) != 0ULL;
    if(na_1 != ((b_10 & 9223372036854775808ULL) != 0ULL))
    {
        return na_1;
    }
    if(na_1)
    {
        _S114 = a_18 > b_10;
    }
    else
    {
        _S114 = a_18 < b_10;
    }
    return _S114;
}

ulong dMin_0(ulong a_19, ulong b_11)
{
    bool _S115;
    if(dIsNaN_0(a_19))
    {
        _S115 = true;
    }
    else
    {
        _S115 = dIsNaN_0(b_11);
    }
    if(_S115)
    {
        return 9221120237041090560ULL;
    }
    if(dIsZero_0(a_19))
    {
        _S115 = dIsZero_0(b_11);
    }
    else
    {
        _S115 = false;
    }
    if(_S115)
    {
        return a_19 | b_11;
    }
    ulong _S116;
    if(dLess_0(b_11, a_19))
    {
        _S116 = b_11;
    }
    else
    {
        _S116 = a_19;
    }
    return _S116;
}

ulong dScalb_0(ulong a_20, int n_5)
{
    return dMul_0(a_20, ulong(n_5 + int(1023)) << 52U);
}

struct F64ProbeParams_0
{
    uint op_0;
    uint n_6;
    uint gridX_0;
};

struct KernelContext_0
{
    F64ProbeParams_0 constant* p_1;
    ulong device* a_21;
    ulong device* b_12;
    ulong device* c_1;
    ulong device* o_0;
};

[[kernel]] void f64Probe(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], F64ProbeParams_0 constant* p_2 [[buffer(4)]], ulong device* a_22 [[buffer(0)]], ulong device* b_13 [[buffer(1)]], ulong device* c_2 [[buffer(2)]], ulong device* o_1 [[buffer(3)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_1 = p_2;
    (&kernelContext_0)->a_21 = a_22;
    (&kernelContext_0)->b_12 = b_13;
    (&kernelContext_0)->c_1 = c_2;
    (&kernelContext_0)->o_0 = o_1;
    uint i_1 = (group_0.y * p_2->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_1 >= (p_2->n_6))
    {
        return;
    }
    ulong x_7 = *((&kernelContext_0)->a_21+i_1);
    ulong y_1 = *((&kernelContext_0)->b_12+i_1);
    ulong z_0 = *((&kernelContext_0)->c_1+i_1);
    ulong r_4;
    switch((&kernelContext_0)->p_1->op_0)
    {
    case 0U:
        {
            r_4 = dFma_0(x_7, y_1, z_0);
            break;
        }
    case 1U:
        {
            r_4 = dMul_0(x_7, y_1);
            break;
        }
    case 2U:
        {
            r_4 = dAdd_0(x_7, y_1);
            break;
        }
    case 3U:
        {
            r_4 = dSub_0(x_7, y_1);
            break;
        }
    case 4U:
        {
            r_4 = dDiv_0(x_7, y_1);
            break;
        }
    case 5U:
        {
            r_4 = dFromFloat_0((as_type<float>((uint(x_7)))));
            break;
        }
    case 6U:
        {
            r_4 = ulong((as_type<uint>((dToFloat_0(x_7)))));
            break;
        }
    case 7U:
        {
            r_4 = ulong(dToLong_0(x_7));
            break;
        }
    case 8U:
        {
            r_4 = dFromLong_0(long(x_7));
            break;
        }
    case 9U:
        {
            int _S117;
            if(dLess_0(x_7, y_1))
            {
                _S117 = int(1);
            }
            else
            {
                _S117 = int(0);
            }
            r_4 = ulong(_S117);
            break;
        }
    case 10U:
        {
            r_4 = dMin_0(x_7, y_1);
            break;
        }
    default:
        {
            r_4 = dScalb_0(x_7, int(long(y_1)));
            break;
        }
    }
    *((&kernelContext_0)->o_0+i_1) = r_4;
    return;
}

