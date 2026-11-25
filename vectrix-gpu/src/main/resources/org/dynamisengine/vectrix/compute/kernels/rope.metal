#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
constant array<ulong, int(9)> COS_D_0 = { 13826050856027422720ULL, 4586165620538955093ULL, 13787419979223755799ULL, 4537941361671905306ULL, 13732177094651715420ULL, 4477122120089393304ULL, 13666517717442657437ULL, 4407590220077447199ULL, 13593695978845207959ULL };
constant array<ulong, int(8)> SIN_D_0 = { 13818544856648471893ULL, 4575957461383581969ULL, 13774824197408792602ULL, 4523617214285662004ULL, 13716528800881525988ULL, 4460272573143870729ULL, 13648976655441704991ULL, 4389130328383466826ULL };
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

bool less128_0(const U128_0 thread* a_0, const U128_0 thread* b_0)
{
    ulong _S13 = a_0->hi_0;
    ulong _S14 = b_0->hi_0;
    bool _S15;
    if((a_0->hi_0) < (b_0->hi_0))
    {
        _S15 = true;
    }
    else
    {
        if(_S13 == _S14)
        {
            _S15 = (a_0->lo_0) < (b_0->lo_0);
        }
        else
        {
            _S15 = false;
        }
    }
    return _S15;
}

bool less128_1(const U128_0 thread* a_1, const U128_0 thread* b_1)
{
    ulong _S16 = a_1->hi_0;
    ulong _S17 = b_1->hi_0;
    bool _S18;
    if((a_1->hi_0) < (b_1->hi_0))
    {
        _S18 = true;
    }
    else
    {
        if(_S16 == _S17)
        {
            _S18 = (a_1->lo_0) < (b_1->lo_0);
        }
        else
        {
            _S18 = false;
        }
    }
    return _S18;
}

ulong dRoundPack_0(ulong sign_0, const U128_0 thread* r_1, int e_0)
{
    int _S19 = bitLength128_1(r_1);
    int _S20 = max(_S19 - int(1) - int(52), int(-1074) - e_0);
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
    int biasedMinusOne_0 = e_0 + _S20 + int(1074);
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

ulong dRoundPack_1(ulong sign_1, const U128_0 thread* r_2, int e_1)
{
    int _S35 = bitLength128_1(r_2);
    int _S36 = max(_S35 - int(1) - int(52), int(-1074) - e_1);
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
    int biasedMinusOne_1 = e_1 + _S36 + int(1074);
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

ulong dRoundPack_2(ulong sign_2, const U128_0 thread* r_3, int e_2)
{
    int _S51 = bitLength128_1(r_3);
    int _S52 = max(_S51 - int(1) - int(52), int(-1074) - e_2);
    ulong q_2;
    if(_S52 <= int(0))
    {
        U128_0 _S53 = shl128_1(r_3, - _S52);
        q_2 = _S53.lo_0;
    }
    else
    {
        if(_S52 > int(128))
        {
            q_2 = 0ULL;
        }
        else
        {
            U128_0 qq_2;
            U128_0 rem_2;
            if(_S52 == int(128))
            {
                qq_2 = u128_0(0ULL, 0ULL);
                rem_2 = *r_3;
            }
            else
            {
                if(_S52 >= int(64))
                {
                    int t_2 = _S52 - int(64);
                    bool _S54 = t_2 == int(0);
                    if(_S54)
                    {
                        q_2 = r_3->hi_0;
                    }
                    else
                    {
                        q_2 = (r_3->hi_0) >> ulong(t_2);
                    }
                    U128_0 _S55 = u128_0(0ULL, q_2);
                    if(_S54)
                    {
                        q_2 = 0ULL;
                    }
                    else
                    {
                        q_2 = (r_3->hi_0) & ((1ULL << ulong(t_2)) - 1ULL);
                    }
                    U128_0 _S56 = u128_0(q_2, r_3->lo_0);
                    qq_2 = _S55;
                    rem_2 = _S56;
                }
                else
                {
                    ulong _S57 = ulong(_S52);
                    U128_0 _S58 = u128_0(0ULL, (r_3->lo_0) & ((1ULL << _S57) - 1ULL));
                    qq_2 = u128_0((r_3->hi_0) >> _S57, ((r_3->lo_0) >> _S57) | ((r_3->hi_0) << ulong(int(64) - _S52)));
                    rem_2 = _S58;
                }
            }
            int _S59 = _S52 - int(1);
            thread U128_0 _S60 = u128_0(0ULL, 1ULL);
            U128_0 _S61 = shl128_1(&_S60, _S59);
            U128_0 _S62 = qq_2;
            thread U128_0 _S63 = _S61;
            thread U128_0 _S64 = rem_2;
            bool _S65 = less128_1(&_S63, &_S64);
            bool _S66;
            if(_S65)
            {
                _S66 = true;
            }
            else
            {
                if((rem_2.hi_0) == (_S61.hi_0))
                {
                    _S66 = (rem_2.lo_0) == (_S61.lo_0);
                }
                else
                {
                    _S66 = false;
                }
                if(_S66)
                {
                    _S66 = ((_S62.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S66 = false;
                }
            }
            if(_S66)
            {
                q_2 = _S62.lo_0 + 1ULL;
            }
            else
            {
                q_2 = _S62.lo_0;
            }
        }
    }
    int biasedMinusOne_2 = e_2 + _S52 + int(1074);
    if(biasedMinusOne_2 >= int(2046))
    {
        return sign_2 | 9218868437227405312ULL;
    }
    ulong bits_2 = (ulong(biasedMinusOne_2) << 52U) + q_2;
    if(bits_2 >= 9218868437227405312ULL)
    {
        q_2 = 9218868437227405312ULL;
    }
    else
    {
        q_2 = bits_2;
    }
    return sign_2 | q_2;
}

ulong dFromLong_0(long v_0)
{
    if(v_0 == 0LL)
    {
        return 0ULL;
    }
    bool _S67 = v_0 < 0LL;
    ulong sign_3;
    if(_S67)
    {
        sign_3 = 9223372036854775808ULL;
    }
    else
    {
        sign_3 = 0ULL;
    }
    ulong m_0;
    if(_S67)
    {
        m_0 = ulong(- v_0);
    }
    else
    {
        m_0 = ulong(v_0);
    }
    thread U128_0 _S68 = u128_0(0ULL, m_0);
    ulong _S69 = dRoundPack_2(sign_3, &_S68, int(0));
    return _S69;
}

bool dIsNaN_0(ulong a_2)
{
    return (a_2 & 9223372036854775807ULL) > 9218868437227405312ULL;
}

bool dIsInf_0(ulong a_3)
{
    return (a_3 & 9223372036854775807ULL) == 9218868437227405312ULL;
}

bool dIsZero_0(ulong a_4)
{
    return (a_4 & 9223372036854775807ULL) == 0ULL;
}

void dUnpack_0(ulong a_5, ulong thread* sig_0, int thread* e_3)
{
    int be_0 = int((a_5 >> 52U) & 2047ULL);
    *sig_0 = a_5 & 4503599627370495ULL;
    if(be_0 == int(0))
    {
        *e_3 = int(-1074);
    }
    else
    {
        *sig_0 = (*sig_0) | 4503599627370496ULL;
        *e_3 = be_0 - int(1075);
    }
    return;
}

void dUnpack_1(ulong a_6, ulong thread* sig_1, int thread* e_4)
{
    int be_1 = int((a_6 >> 52U) & 2047ULL);
    *sig_1 = a_6 & 4503599627370495ULL;
    if(be_1 == int(0))
    {
        *e_4 = int(-1074);
    }
    else
    {
        *sig_1 = (*sig_1) | 4503599627370496ULL;
        *e_4 = be_1 - int(1075);
    }
    return;
}

void dUnpack_2(ulong a_7, ulong thread* sig_2, int thread* e_5)
{
    int be_2 = int((a_7 >> 52U) & 2047ULL);
    *sig_2 = a_7 & 4503599627370495ULL;
    if(be_2 == int(0))
    {
        *e_5 = int(-1074);
    }
    else
    {
        *sig_2 = (*sig_2) | 4503599627370496ULL;
        *e_5 = be_2 - int(1075);
    }
    return;
}

U128_0 mul64_0(ulong a_8, ulong b_2)
{
    ulong a0_0 = a_8 & 4294967295ULL;
    ulong a1_0 = a_8 >> 32U;
    ulong b0_0 = b_2 & 4294967295ULL;
    ulong b1_0 = b_2 >> 32U;
    ulong p00_0 = a0_0 * b0_0;
    ulong p01_0 = a0_0 * b1_0;
    ulong p10_0 = a1_0 * b0_0;
    ulong mid_0 = (p00_0 >> 32U) + (p01_0 & 4294967295ULL) + (p10_0 & 4294967295ULL);
    return u128_0(a1_0 * b1_0 + (p01_0 >> 32U) + (p10_0 >> 32U) + (mid_0 >> 32U), (mid_0 << 32U) | (p00_0 & 4294967295ULL));
}

U128_0 shrSticky128_0(const U128_0 thread* x_5, int s_2)
{
    if(s_2 <= int(0))
    {
        return *x_5;
    }
    if(s_2 >= int(128))
    {
        int _S70;
        if(((x_5->hi_0) | (x_5->lo_0)) != 0ULL)
        {
            _S70 = int(1);
        }
        else
        {
            _S70 = int(0);
        }
        return u128_0(0ULL, ulong(_S70));
    }
    thread U128_0 r_4;
    ulong lost_0;
    if(s_2 >= int(64))
    {
        int t_3 = s_2 - int(64);
        bool _S71 = t_3 == int(0);
        if(_S71)
        {
            lost_0 = x_5->hi_0;
        }
        else
        {
            lost_0 = (x_5->hi_0) >> ulong(t_3);
        }
        r_4 = u128_0(0ULL, lost_0);
        ulong _S72 = x_5->lo_0;
        if(_S71)
        {
            lost_0 = 0ULL;
        }
        else
        {
            lost_0 = (x_5->hi_0) << ulong(int(64) - t_3);
        }
        lost_0 = _S72 | lost_0;
    }
    else
    {
        ulong _S73 = ulong(s_2);
        ulong _S74 = ulong(int(64) - s_2);
        r_4 = u128_0((x_5->hi_0) >> _S73, ((x_5->lo_0) >> _S73) | ((x_5->hi_0) << _S74));
        lost_0 = (x_5->lo_0) << _S74;
    }
    if(lost_0 != 0ULL)
    {
        (&r_4)->lo_0 = ((&r_4)->lo_0) | 1ULL;
    }
    return r_4;
}

U128_0 add128_0(const U128_0 thread* a_9, const U128_0 thread* b_3)
{
    ulong lo_2 = a_9->lo_0 + b_3->lo_0;
    ulong _S75 = a_9->hi_0 + b_3->hi_0;
    int _S76;
    if(lo_2 < (a_9->lo_0))
    {
        _S76 = int(1);
    }
    else
    {
        _S76 = int(0);
    }
    return u128_0(_S75 + ulong(_S76), lo_2);
}

U128_0 sub128_0(const U128_0 thread* a_10, const U128_0 thread* b_4)
{
    ulong _S77 = a_10->hi_0 - b_4->hi_0;
    ulong _S78 = a_10->lo_0;
    ulong _S79 = b_4->lo_0;
    int _S80;
    if((a_10->lo_0) < (b_4->lo_0))
    {
        _S80 = int(1);
    }
    else
    {
        _S80 = int(0);
    }
    return u128_0(_S77 - ulong(_S80), _S78 - _S79);
}

ulong dFma_0(ulong a_11, ulong b_5, ulong c_0)
{
    bool _S81;
    if(dIsNaN_0(a_11))
    {
        _S81 = true;
    }
    else
    {
        _S81 = dIsNaN_0(b_5);
    }
    if(_S81)
    {
        _S81 = true;
    }
    else
    {
        _S81 = dIsNaN_0(c_0);
    }
    if(_S81)
    {
        return 9221120237041090560ULL;
    }
    ulong sp_0 = (a_11 ^ b_5) & 9223372036854775808ULL;
    ulong sc_0 = c_0 & 9223372036854775808ULL;
    if(dIsInf_0(a_11))
    {
        _S81 = true;
    }
    else
    {
        _S81 = dIsInf_0(b_5);
    }
    if(_S81)
    {
        if(dIsZero_0(a_11))
        {
            _S81 = true;
        }
        else
        {
            _S81 = dIsZero_0(b_5);
        }
        if(_S81)
        {
            return 9221120237041090560ULL;
        }
        if(dIsInf_0(c_0))
        {
            _S81 = sc_0 != sp_0;
        }
        else
        {
            _S81 = false;
        }
        if(_S81)
        {
            return 9221120237041090560ULL;
        }
        return sp_0 | 9218868437227405312ULL;
    }
    if(dIsInf_0(c_0))
    {
        return c_0;
    }
    if(dIsZero_0(a_11))
    {
        _S81 = true;
    }
    else
    {
        _S81 = dIsZero_0(b_5);
    }
    if(_S81)
    {
        if(dIsZero_0(c_0))
        {
            ulong _S82;
            if(sp_0 == sc_0)
            {
                _S82 = c_0;
            }
            else
            {
                _S82 = 0ULL;
            }
            return _S82;
        }
        return c_0;
    }
    thread ulong ma_0;
    thread int ea_0;
    dUnpack_0(a_11, &ma_0, &ea_0);
    thread ulong mb_0;
    thread int eb_0;
    dUnpack_0(b_5, &mb_0, &eb_0);
    U128_0 p_0 = mul64_0(ma_0, mb_0);
    int ep_0 = ea_0 + eb_0;
    if(dIsZero_0(c_0))
    {
        thread U128_0 _S83 = p_0;
        ulong _S84 = dRoundPack_0(sp_0, &_S83, ep_0);
        return _S84;
    }
    thread ulong mc_0;
    thread int ec_0;
    dUnpack_0(c_0, &mc_0, &ec_0);
    U128_0 cc_0 = u128_0(0ULL, mc_0);
    thread U128_0 _S85 = p_0;
    int _S86 = bitLength128_0(&_S85);
    int e_6 = max(ep_0 + _S86 - int(1), ec_0 + bitLength64_0(mc_0) - int(1)) - int(125);
    int _S87 = ep_0 - e_6;
    U128_0 x_6;
    if(_S87 >= int(0))
    {
        thread U128_0 _S88 = p_0;
        U128_0 _S89 = shl128_0(&_S88, _S87);
        x_6 = _S89;
    }
    else
    {
        int _S90 = e_6 - ep_0;
        thread U128_0 _S91 = p_0;
        U128_0 _S92 = shrSticky128_0(&_S91, _S90);
        x_6 = _S92;
    }
    U128_0 y_0;
    if((ec_0 - e_6) >= int(0))
    {
        int _S93 = ec_0 - e_6;
        thread U128_0 _S94 = cc_0;
        U128_0 _S95 = shl128_0(&_S94, _S93);
        y_0 = _S95;
    }
    else
    {
        int _S96 = e_6 - ec_0;
        thread U128_0 _S97 = cc_0;
        U128_0 _S98 = shrSticky128_0(&_S97, _S96);
        y_0 = _S98;
    }
    if(sp_0 == sc_0)
    {
        thread U128_0 _S99 = x_6;
        thread U128_0 _S100 = y_0;
        U128_0 _S101 = add128_0(&_S99, &_S100);
        thread U128_0 _S102 = _S101;
        ulong _S103 = dRoundPack_0(sp_0, &_S102, e_6);
        return _S103;
    }
    if((x_6.hi_0) == (y_0.hi_0))
    {
        _S81 = (x_6.lo_0) == (y_0.lo_0);
    }
    else
    {
        _S81 = false;
    }
    if(_S81)
    {
        return 0ULL;
    }
    thread U128_0 _S104 = x_6;
    thread U128_0 _S105 = y_0;
    bool _S106 = less128_0(&_S104, &_S105);
    if(_S106)
    {
        thread U128_0 _S107 = y_0;
        thread U128_0 _S108 = x_6;
        U128_0 _S109 = sub128_0(&_S107, &_S108);
        thread U128_0 _S110 = _S109;
        ulong _S111 = dRoundPack_0(sc_0, &_S110, e_6);
        return _S111;
    }
    thread U128_0 _S112 = x_6;
    thread U128_0 _S113 = y_0;
    U128_0 _S114 = sub128_0(&_S112, &_S113);
    thread U128_0 _S115 = _S114;
    ulong _S116 = dRoundPack_0(sp_0, &_S115, e_6);
    return _S116;
}

ulong dMul_0(ulong a_12, ulong b_6)
{
    return dFma_0(a_12, b_6, 9223372036854775808ULL);
}

ulong dAdd_0(ulong a_13, ulong b_7)
{
    return dFma_0(a_13, 4607182418800017408ULL, b_7);
}

ulong dNeg_0(ulong a_14)
{
    return a_14 ^ 9223372036854775808ULL;
}

ulong dSub_0(ulong a_15, ulong b_8)
{
    return dAdd_0(a_15, dNeg_0(b_8));
}

long dToLong_0(ulong a_16)
{
    bool _S117;
    if(dIsNaN_0(a_16))
    {
        _S117 = true;
    }
    else
    {
        _S117 = dIsZero_0(a_16);
    }
    if(_S117)
    {
        return 0LL;
    }
    bool negative_0 = (a_16 & 9223372036854775808ULL) != 0ULL;
    long _S118;
    if(dIsInf_0(a_16))
    {
        if(negative_0)
        {
            _S118 = -9223372036854775808LL;
        }
        else
        {
            _S118 = 9223372036854775807LL;
        }
        return _S118;
    }
    thread ulong sig_3;
    thread int e_7;
    dUnpack_2(a_16, &sig_3, &e_7);
    if((e_7 + bitLength64_0(sig_3)) > int(63))
    {
        if(negative_0)
        {
            _S118 = -9223372036854775808LL;
        }
        else
        {
            _S118 = 9223372036854775807LL;
        }
        return _S118;
    }
    ulong m_1;
    if(e_7 >= int(0))
    {
        m_1 = sig_3 << ulong(e_7);
    }
    else
    {
        if(e_7 <= int(-64))
        {
            m_1 = 0ULL;
        }
        else
        {
            m_1 = sig_3 >> ulong(- e_7);
        }
    }
    long v_1 = long(m_1);
    if(negative_0)
    {
        _S118 = - v_1;
    }
    else
    {
        _S118 = v_1;
    }
    return _S118;
}

ulong dFromFloat_0(float f_0)
{
    uint u_0 = (as_type<uint>((f_0)));
    ulong sign_4 = ulong(u_0 & 2147483648U) << 32U;
    uint be_3 = (u_0 >> 23U) & 255U;
    uint m_2 = u_0 & 8388607U;
    if(be_3 == 255U)
    {
        ulong _S119;
        if(m_2 != 0U)
        {
            _S119 = 9221120237041090560ULL;
        }
        else
        {
            _S119 = sign_4 | 9218868437227405312ULL;
        }
        return _S119;
    }
    if(be_3 == 0U)
    {
        if(m_2 == 0U)
        {
            return sign_4;
        }
        thread U128_0 _S120 = u128_0(0ULL, ulong(m_2));
        ulong _S121 = dRoundPack_1(sign_4, &_S120, int(-149));
        return _S121;
    }
    return (sign_4 | (ulong(be_3 - 127U + 1023U) << 52U)) | (ulong(m_2) << 29U);
}

float dToFloat_0(ulong a_17)
{
    uint sign_5 = uint(a_17 >> 32U) & 2147483648U;
    if(dIsNaN_0(a_17))
    {
        return (as_type<float>((2143289344U)));
    }
    if(dIsInf_0(a_17))
    {
        return (as_type<float>((sign_5 | 2139095040U)));
    }
    if(dIsZero_0(a_17))
    {
        return (as_type<float>((sign_5)));
    }
    thread ulong sig_4;
    thread int e_8;
    dUnpack_1(a_17, &sig_4, &e_8);
    int _S122 = max(bitLength64_0(sig_4) - int(1) - int(23), int(-149) - e_8);
    ulong q_3;
    if(_S122 >= int(64))
    {
        q_3 = 0ULL;
    }
    else
    {
        ulong _S123 = ulong(_S122);
        ulong q_4 = sig_4 >> _S123;
        ulong rem_3 = sig_4 & ((1ULL << _S123) - 1ULL);
        ulong halfway_0 = 1ULL << ulong(_S122 - int(1));
        bool _S124;
        if(rem_3 > halfway_0)
        {
            _S124 = true;
        }
        else
        {
            if(rem_3 == halfway_0)
            {
                _S124 = (q_4 & 1ULL) != 0ULL;
            }
            else
            {
                _S124 = false;
            }
        }
        if(_S124)
        {
            q_3 = q_4 + 1ULL;
        }
        else
        {
            q_3 = q_4;
        }
    }
    int biasedMinusOne_3 = e_8 + _S122 + int(149);
    if(biasedMinusOne_3 >= int(254))
    {
        return (as_type<float>((sign_5 | 2139095040U)));
    }
    uint bits_3 = uint(biasedMinusOne_3) * 8388608U + uint(q_3);
    if(bits_3 >= 2139095040U)
    {
        return (as_type<float>((sign_5 | 2139095040U)));
    }
    return (as_type<float>((sign_5 | bits_3)));
}

struct RopeParams_0
{
    uint half_0;
    uint interleaved_0;
    uint backward_0;
    uint xOff_0;
    uint ldx_0;
    uint count_0;
    uint posOff_0;
    uint group_0;
    uint gridX_0;
};

struct KernelContext_0
{
    RopeParams_0 constant* p_1;
    ulong device* positions_0;
    ulong device* invFreq_0;
    float device* x_7;
};

[[kernel]] void rope(uint3 group_1 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], RopeParams_0 constant* p_2 [[buffer(3)]], ulong device* positions_1 [[buffer(2)]], ulong device* invFreq_1 [[buffer(1)]], float device* x_8 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_1 = p_2;
    (&kernelContext_0)->positions_0 = positions_1;
    (&kernelContext_0)->invFreq_0 = invFreq_1;
    (&kernelContext_0)->x_7 = x_8;
    uint t_4 = (group_1.y * p_2->gridX_0 + group_1.x) * 64U + local_0.x;
    if(t_4 >= (p_2->count_0 * p_2->half_0))
    {
        return;
    }
    uint v_2 = t_4 / p_2->half_0;
    uint i_0 = t_4 - v_2 * p_2->half_0;
    uint _S125 = (&kernelContext_0)->p_1->posOff_0;
    uint _S126 = v_2 / (&kernelContext_0)->p_1->group_0;
    ulong a_18 = dMul_0(dFromLong_0(long(*((&kernelContext_0)->positions_0+(_S125 + _S126)))), *((&kernelContext_0)->invFreq_0+i_0));
    ulong kf_0 = dSub_0(dAdd_0(dMul_0(a_18, 4603909380684499075ULL), 4843621399236968448ULL), 4843621399236968448ULL);
    ulong r_5 = dFma_0(kf_0, 13365421017223154113ULL, dFma_0(kf_0, 13520678587564294144ULL, dFma_0(kf_0, 13677630397471522816ULL, dFma_0(kf_0, 13833125093779177472ULL, a_18))));
    ulong z_0 = dMul_0(r_5, r_5);
    ulong ps_0 = 4389130328383466826ULL;
    int n_5 = int(6);
    for(;;)
    {
        if(n_5 >= int(0))
        {
        }
        else
        {
            break;
        }
        ulong _S127 = dFma_0(ps_0, z_0, SIN_D_0[n_5]);
        int _S128 = n_5 - int(1);
        ps_0 = _S127;
        n_5 = _S128;
    }
    ulong pc_0 = 13593695978845207959ULL;
    n_5 = int(7);
    for(;;)
    {
        if(n_5 >= int(0))
        {
        }
        else
        {
            break;
        }
        ulong _S129 = dFma_0(pc_0, z_0, COS_D_0[n_5]);
        int _S130 = n_5 - int(1);
        pc_0 = _S129;
        n_5 = _S130;
    }
    ulong sn_0 = dFma_0(dMul_0(r_5, z_0), ps_0, r_5);
    ulong cs_0 = dFma_0(z_0, pc_0, 4607182418800017408ULL);
    uint q_5 = uint((dToLong_0(kf_0)) & 3LL);
    bool _S131 = q_5 == 0U;
    ulong s_3;
    if(_S131)
    {
        s_3 = sn_0;
    }
    else
    {
        if(q_5 == 1U)
        {
            ps_0 = cs_0;
        }
        else
        {
            if(q_5 == 2U)
            {
                ps_0 = dNeg_0(sn_0);
            }
            else
            {
                ps_0 = dNeg_0(cs_0);
            }
        }
        s_3 = ps_0;
    }
    ulong c_1;
    if(_S131)
    {
        c_1 = cs_0;
    }
    else
    {
        if(q_5 == 1U)
        {
            ps_0 = dNeg_0(sn_0);
        }
        else
        {
            if(q_5 == 2U)
            {
                ps_0 = dNeg_0(cs_0);
            }
            else
            {
                ps_0 = sn_0;
            }
        }
        c_1 = ps_0;
    }
    if(((&kernelContext_0)->p_1->backward_0) != 0U)
    {
        ps_0 = 13830554455654793216ULL;
    }
    else
    {
        ps_0 = 4607182418800017408ULL;
    }
    ulong s_4 = dMul_0(s_3, ps_0);
    uint base_0 = (&kernelContext_0)->p_1->xOff_0 + v_2 * (&kernelContext_0)->p_1->ldx_0;
    uint i0_0;
    if(((&kernelContext_0)->p_1->interleaved_0) != 0U)
    {
        i0_0 = base_0 + 2U * i_0;
    }
    else
    {
        i0_0 = base_0 + i_0;
    }
    uint i1_0;
    if(((&kernelContext_0)->p_1->interleaved_0) != 0U)
    {
        i1_0 = i0_0 + 1U;
    }
    else
    {
        i1_0 = i0_0 + p_2->half_0;
    }
    ulong x0_0 = dFromFloat_0(*((&kernelContext_0)->x_7+i0_0));
    ulong x1_0 = dFromFloat_0(*((&kernelContext_0)->x_7+i1_0));
    *((&kernelContext_0)->x_7+i0_0) = dToFloat_0(dSub_0(dMul_0(x0_0, c_1), dMul_0(x1_0, s_4)));
    *((&kernelContext_0)->x_7+i1_0) = dToFloat_0(dAdd_0(dMul_0(x0_0, s_4), dMul_0(x1_0, c_1)));
    return;
}

