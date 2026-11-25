#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
constant array<ulong, int(12)> EXP_D_0 = { 4602678819172646912ULL, 4595172819793696085ULL, 4586165620538955093ULL, 4575957461383581969ULL, 4564047942368979991ULL, 4551452160554016794ULL, 4537941361671905306ULL, 4523617214285662004ULL, 4508805057796939612ULL, 4493156764026750180ULL, 4477122120089393304ULL, 4460272573143870729ULL };
constant array<ulong, int(22)> ERF_D_0 = { 4607760587169110893ULL, 13823819347434633703ULL, 4592795249457507298ULL, 13806772904734002001ULL, 4572672858297640255ULL, 13784395700169345926ULL, 4548523690078996919ULL, 13758300211235570411ULL, 4520380840337101219ULL, 13728650905912600346ULL, 4490032028089207836ULL, 13696886215062967149ULL, 4456846938546218800ULL, 13663222339935684330ULL, 4422367961254817799ULL, 13627706588145419727ULL, 4385785527631038309ULL, 13590210042920191990ULL, 4347653220051876748ULL, 13551767547046642809ULL, 4308455882138099497ULL, 13511757036778165052ULL };
constant array<uint, int(13)> ERF_C_0 = { 1066430139U, 3200291747U, 1038555025U, 3168540274U, 1001074151U, 3126859482U, 956092820U, 3078252846U, 903672677U, 3023026711U, 847143609U, 2963860364U, 785331563U };
float jabs_0(float x_0)
{
    return (as_type<float>(((as_type<uint>((x_0))) & 2147483647U)));
}

float jmin_0(float a_0, float b_0)
{
    if(a_0 != a_0)
    {
        return a_0;
    }
    bool _S1;
    if(a_0 == 0.0)
    {
        _S1 = b_0 == 0.0;
    }
    else
    {
        _S1 = false;
    }
    if(_S1)
    {
        _S1 = (as_type<uint>((b_0))) == 2147483648U;
    }
    else
    {
        _S1 = false;
    }
    if(_S1)
    {
        return b_0;
    }
    float _S2;
    if(a_0 <= b_0)
    {
        _S2 = a_0;
    }
    else
    {
        _S2 = b_0;
    }
    return _S2;
}

float jmax_0(float a_1, float b_1)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S3;
    if(a_1 == 0.0)
    {
        _S3 = b_1 == 0.0;
    }
    else
    {
        _S3 = false;
    }
    if(_S3)
    {
        _S3 = (as_type<uint>((a_1))) == 2147483648U;
    }
    else
    {
        _S3 = false;
    }
    if(_S3)
    {
        return b_1;
    }
    float _S4;
    if(a_1 >= b_1)
    {
        _S4 = a_1;
    }
    else
    {
        _S4 = b_1;
    }
    return _S4;
}

float reproducibleExp_0(float x_1)
{
    float c_0 = jmin_0(jmax_0(x_1, -104.0), 89.0);
    float kf_0 = c_0 * 1.4426950216293335 + 1.2582912e+07 - 1.2582912e+07;
    float r_0 = fma(kf_0, 0.00021219444170129, fma(kf_0, -0.693359375, c_0));
    int k_0 = int(kf_0);
    int k1_0 = k_0 >> 1U;
    return (fma(fma(fma(fma(fma(fma(0.0001984127011383, r_0, 0.00138888892251998), r_0, 0.00833333376795053), r_0, 0.0416666679084301), r_0, 0.1666666716337204), r_0, 0.5), r_0 * r_0, r_0) + 1.0) * (as_type<float>((uint((k1_0 + int(127)) << 23U)))) * (as_type<float>((uint((k_0 - k1_0 + int(127)) << 23U))));
}

float jcopySign_0(float magnitude_0, float sign_0)
{
    return (as_type<float>((((as_type<uint>((magnitude_0))) & 2147483647U) | ((as_type<uint>((sign_0))) & 2147483648U))));
}

float reproducibleErf_0(float x_2)
{
    float a_2 = jmin_0(jabs_0(x_2), 3.92000007629394531);
    float a2_0 = a_2 * a_2;
    float _S5 = - a2_0;
    float a2lo_0 = fma(a_2, a_2, _S5);
    float v_0;
    int n_0;
    if(a_2 < 1.0)
    {
        v_0 = (as_type<float>((785331563U)));
        n_0 = int(11);
        for(;;)
        {
            if(n_0 >= int(0))
            {
            }
            else
            {
                break;
            }
            float _S6 = fma(v_0, a2_0, (as_type<float>((ERF_C_0[n_0]))));
            int _S7 = n_0 - int(1);
            v_0 = _S6;
            n_0 = _S7;
        }
        v_0 = a_2 * v_0;
    }
    else
    {
        float g_0 = reproducibleExp_0(_S5) * (1.0 - a2lo_0);
        v_0 = a_2;
        n_0 = int(64);
        for(;;)
        {
            if(n_0 >= int(1))
            {
            }
            else
            {
                break;
            }
            float _S8 = a_2 + 0.5 * float(n_0) / v_0;
            int _S9 = n_0 - int(1);
            v_0 = _S8;
            n_0 = _S9;
        }
        v_0 = 1.0 - g_0 / ((as_type<float>((1071833029U))) * v_0);
    }
    return jcopySign_0(jmin_0(v_0, 1.0), x_2);
}

struct U128_0
{
    ulong hi_0;
    ulong lo_0;
};

U128_0 u128_0(ulong hi_1, ulong lo_1)
{
    thread U128_0 r_1;
    (&r_1)->hi_0 = hi_1;
    (&r_1)->lo_0 = lo_1;
    return r_1;
}

int bitLength64_0(ulong x_3)
{
    if(x_3 == 0ULL)
    {
        return int(0);
    }
    ulong _S10 = x_3 >> 32U;
    ulong _S11;
    int n_1;
    if(_S10 != 0ULL)
    {
        _S11 = _S10;
        n_1 = int(33);
    }
    else
    {
        _S11 = x_3;
        n_1 = int(1);
    }
    ulong _S12 = _S11 >> 16U;
    if(_S12 != 0ULL)
    {
        int n_2 = n_1 + int(16);
        _S11 = _S12;
        n_1 = n_2;
    }
    ulong _S13 = _S11 >> 8U;
    if(_S13 != 0ULL)
    {
        int n_3 = n_1 + int(8);
        _S11 = _S13;
        n_1 = n_3;
    }
    ulong _S14 = _S11 >> 4U;
    if(_S14 != 0ULL)
    {
        int n_4 = n_1 + int(4);
        _S11 = _S14;
        n_1 = n_4;
    }
    ulong _S15 = _S11 >> 2U;
    if(_S15 != 0ULL)
    {
        int n_5 = n_1 + int(2);
        _S11 = _S15;
        n_1 = n_5;
    }
    if((_S11 >> 1U) != 0ULL)
    {
        n_1 = n_1 + int(1);
    }
    return n_1;
}

int bitLength128_0(const U128_0 thread* x_4)
{
    ulong _S16 = x_4->hi_0;
    int _S17;
    if((x_4->hi_0) != 0ULL)
    {
        _S17 = int(64) + bitLength64_0(_S16);
    }
    else
    {
        _S17 = bitLength64_0(x_4->lo_0);
    }
    return _S17;
}

int bitLength128_1(const U128_0 thread* x_5)
{
    ulong _S18 = x_5->hi_0;
    int _S19;
    if((x_5->hi_0) != 0ULL)
    {
        _S19 = int(64) + bitLength64_0(_S18);
    }
    else
    {
        _S19 = bitLength64_0(x_5->lo_0);
    }
    return _S19;
}

U128_0 shl128_0(const U128_0 thread* x_6, int s_0)
{
    if(s_0 <= int(0))
    {
        return *x_6;
    }
    if(s_0 >= int(128))
    {
        return u128_0(0ULL, 0ULL);
    }
    if(s_0 >= int(64))
    {
        return u128_0((x_6->lo_0) << ulong(s_0 - int(64)), 0ULL);
    }
    ulong _S20 = ulong(s_0);
    return u128_0(((x_6->hi_0) << _S20) | ((x_6->lo_0) >> ulong(int(64) - s_0)), (x_6->lo_0) << _S20);
}

U128_0 shl128_1(const U128_0 thread* x_7, int s_1)
{
    if(s_1 <= int(0))
    {
        return *x_7;
    }
    if(s_1 >= int(128))
    {
        return u128_0(0ULL, 0ULL);
    }
    if(s_1 >= int(64))
    {
        return u128_0((x_7->lo_0) << ulong(s_1 - int(64)), 0ULL);
    }
    ulong _S21 = ulong(s_1);
    return u128_0(((x_7->hi_0) << _S21) | ((x_7->lo_0) >> ulong(int(64) - s_1)), (x_7->lo_0) << _S21);
}

bool less128_0(const U128_0 thread* a_3, const U128_0 thread* b_2)
{
    ulong _S22 = a_3->hi_0;
    ulong _S23 = b_2->hi_0;
    bool _S24;
    if((a_3->hi_0) < (b_2->hi_0))
    {
        _S24 = true;
    }
    else
    {
        if(_S22 == _S23)
        {
            _S24 = (a_3->lo_0) < (b_2->lo_0);
        }
        else
        {
            _S24 = false;
        }
    }
    return _S24;
}

bool less128_1(const U128_0 thread* a_4, const U128_0 thread* b_3)
{
    ulong _S25 = a_4->hi_0;
    ulong _S26 = b_3->hi_0;
    bool _S27;
    if((a_4->hi_0) < (b_3->hi_0))
    {
        _S27 = true;
    }
    else
    {
        if(_S25 == _S26)
        {
            _S27 = (a_4->lo_0) < (b_3->lo_0);
        }
        else
        {
            _S27 = false;
        }
    }
    return _S27;
}

ulong dRoundPack_0(ulong sign_1, const U128_0 thread* r_2, int e_0)
{
    int _S28 = bitLength128_1(r_2);
    int _S29 = max(_S28 - int(1) - int(52), int(-1074) - e_0);
    ulong q_0;
    if(_S29 <= int(0))
    {
        U128_0 _S30 = shl128_1(r_2, - _S29);
        q_0 = _S30.lo_0;
    }
    else
    {
        if(_S29 > int(128))
        {
            q_0 = 0ULL;
        }
        else
        {
            U128_0 qq_0;
            U128_0 rem_0;
            if(_S29 == int(128))
            {
                qq_0 = u128_0(0ULL, 0ULL);
                rem_0 = *r_2;
            }
            else
            {
                if(_S29 >= int(64))
                {
                    int t_0 = _S29 - int(64);
                    bool _S31 = t_0 == int(0);
                    if(_S31)
                    {
                        q_0 = r_2->hi_0;
                    }
                    else
                    {
                        q_0 = (r_2->hi_0) >> ulong(t_0);
                    }
                    U128_0 _S32 = u128_0(0ULL, q_0);
                    if(_S31)
                    {
                        q_0 = 0ULL;
                    }
                    else
                    {
                        q_0 = (r_2->hi_0) & ((1ULL << ulong(t_0)) - 1ULL);
                    }
                    U128_0 _S33 = u128_0(q_0, r_2->lo_0);
                    qq_0 = _S32;
                    rem_0 = _S33;
                }
                else
                {
                    ulong _S34 = ulong(_S29);
                    U128_0 _S35 = u128_0(0ULL, (r_2->lo_0) & ((1ULL << _S34) - 1ULL));
                    qq_0 = u128_0((r_2->hi_0) >> _S34, ((r_2->lo_0) >> _S34) | ((r_2->hi_0) << ulong(int(64) - _S29)));
                    rem_0 = _S35;
                }
            }
            int _S36 = _S29 - int(1);
            thread U128_0 _S37 = u128_0(0ULL, 1ULL);
            U128_0 _S38 = shl128_1(&_S37, _S36);
            U128_0 _S39 = qq_0;
            thread U128_0 _S40 = _S38;
            thread U128_0 _S41 = rem_0;
            bool _S42 = less128_1(&_S40, &_S41);
            bool _S43;
            if(_S42)
            {
                _S43 = true;
            }
            else
            {
                if((rem_0.hi_0) == (_S38.hi_0))
                {
                    _S43 = (rem_0.lo_0) == (_S38.lo_0);
                }
                else
                {
                    _S43 = false;
                }
                if(_S43)
                {
                    _S43 = ((_S39.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S43 = false;
                }
            }
            if(_S43)
            {
                q_0 = _S39.lo_0 + 1ULL;
            }
            else
            {
                q_0 = _S39.lo_0;
            }
        }
    }
    int biasedMinusOne_0 = e_0 + _S29 + int(1074);
    if(biasedMinusOne_0 >= int(2046))
    {
        return sign_1 | 9218868437227405312ULL;
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
    return sign_1 | q_0;
}

ulong dRoundPack_1(ulong sign_2, const U128_0 thread* r_3, int e_1)
{
    int _S44 = bitLength128_1(r_3);
    int _S45 = max(_S44 - int(1) - int(52), int(-1074) - e_1);
    ulong q_1;
    if(_S45 <= int(0))
    {
        U128_0 _S46 = shl128_1(r_3, - _S45);
        q_1 = _S46.lo_0;
    }
    else
    {
        if(_S45 > int(128))
        {
            q_1 = 0ULL;
        }
        else
        {
            U128_0 qq_1;
            U128_0 rem_1;
            if(_S45 == int(128))
            {
                qq_1 = u128_0(0ULL, 0ULL);
                rem_1 = *r_3;
            }
            else
            {
                if(_S45 >= int(64))
                {
                    int t_1 = _S45 - int(64);
                    bool _S47 = t_1 == int(0);
                    if(_S47)
                    {
                        q_1 = r_3->hi_0;
                    }
                    else
                    {
                        q_1 = (r_3->hi_0) >> ulong(t_1);
                    }
                    U128_0 _S48 = u128_0(0ULL, q_1);
                    if(_S47)
                    {
                        q_1 = 0ULL;
                    }
                    else
                    {
                        q_1 = (r_3->hi_0) & ((1ULL << ulong(t_1)) - 1ULL);
                    }
                    U128_0 _S49 = u128_0(q_1, r_3->lo_0);
                    qq_1 = _S48;
                    rem_1 = _S49;
                }
                else
                {
                    ulong _S50 = ulong(_S45);
                    U128_0 _S51 = u128_0(0ULL, (r_3->lo_0) & ((1ULL << _S50) - 1ULL));
                    qq_1 = u128_0((r_3->hi_0) >> _S50, ((r_3->lo_0) >> _S50) | ((r_3->hi_0) << ulong(int(64) - _S45)));
                    rem_1 = _S51;
                }
            }
            int _S52 = _S45 - int(1);
            thread U128_0 _S53 = u128_0(0ULL, 1ULL);
            U128_0 _S54 = shl128_1(&_S53, _S52);
            U128_0 _S55 = qq_1;
            thread U128_0 _S56 = _S54;
            thread U128_0 _S57 = rem_1;
            bool _S58 = less128_1(&_S56, &_S57);
            bool _S59;
            if(_S58)
            {
                _S59 = true;
            }
            else
            {
                if((rem_1.hi_0) == (_S54.hi_0))
                {
                    _S59 = (rem_1.lo_0) == (_S54.lo_0);
                }
                else
                {
                    _S59 = false;
                }
                if(_S59)
                {
                    _S59 = ((_S55.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S59 = false;
                }
            }
            if(_S59)
            {
                q_1 = _S55.lo_0 + 1ULL;
            }
            else
            {
                q_1 = _S55.lo_0;
            }
        }
    }
    int biasedMinusOne_1 = e_1 + _S45 + int(1074);
    if(biasedMinusOne_1 >= int(2046))
    {
        return sign_2 | 9218868437227405312ULL;
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
    return sign_2 | q_1;
}

ulong dRoundPack_2(ulong sign_3, const U128_0 thread* r_4, int e_2)
{
    int _S60 = bitLength128_1(r_4);
    int _S61 = max(_S60 - int(1) - int(52), int(-1074) - e_2);
    ulong q_2;
    if(_S61 <= int(0))
    {
        U128_0 _S62 = shl128_1(r_4, - _S61);
        q_2 = _S62.lo_0;
    }
    else
    {
        if(_S61 > int(128))
        {
            q_2 = 0ULL;
        }
        else
        {
            U128_0 qq_2;
            U128_0 rem_2;
            if(_S61 == int(128))
            {
                qq_2 = u128_0(0ULL, 0ULL);
                rem_2 = *r_4;
            }
            else
            {
                if(_S61 >= int(64))
                {
                    int t_2 = _S61 - int(64);
                    bool _S63 = t_2 == int(0);
                    if(_S63)
                    {
                        q_2 = r_4->hi_0;
                    }
                    else
                    {
                        q_2 = (r_4->hi_0) >> ulong(t_2);
                    }
                    U128_0 _S64 = u128_0(0ULL, q_2);
                    if(_S63)
                    {
                        q_2 = 0ULL;
                    }
                    else
                    {
                        q_2 = (r_4->hi_0) & ((1ULL << ulong(t_2)) - 1ULL);
                    }
                    U128_0 _S65 = u128_0(q_2, r_4->lo_0);
                    qq_2 = _S64;
                    rem_2 = _S65;
                }
                else
                {
                    ulong _S66 = ulong(_S61);
                    U128_0 _S67 = u128_0(0ULL, (r_4->lo_0) & ((1ULL << _S66) - 1ULL));
                    qq_2 = u128_0((r_4->hi_0) >> _S66, ((r_4->lo_0) >> _S66) | ((r_4->hi_0) << ulong(int(64) - _S61)));
                    rem_2 = _S67;
                }
            }
            int _S68 = _S61 - int(1);
            thread U128_0 _S69 = u128_0(0ULL, 1ULL);
            U128_0 _S70 = shl128_1(&_S69, _S68);
            U128_0 _S71 = qq_2;
            thread U128_0 _S72 = _S70;
            thread U128_0 _S73 = rem_2;
            bool _S74 = less128_1(&_S72, &_S73);
            bool _S75;
            if(_S74)
            {
                _S75 = true;
            }
            else
            {
                if((rem_2.hi_0) == (_S70.hi_0))
                {
                    _S75 = (rem_2.lo_0) == (_S70.lo_0);
                }
                else
                {
                    _S75 = false;
                }
                if(_S75)
                {
                    _S75 = ((_S71.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S75 = false;
                }
            }
            if(_S75)
            {
                q_2 = _S71.lo_0 + 1ULL;
            }
            else
            {
                q_2 = _S71.lo_0;
            }
        }
    }
    int biasedMinusOne_2 = e_2 + _S61 + int(1074);
    if(biasedMinusOne_2 >= int(2046))
    {
        return sign_3 | 9218868437227405312ULL;
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
    return sign_3 | q_2;
}

ulong dRoundPack_3(ulong sign_4, const U128_0 thread* r_5, int e_3)
{
    int _S76 = bitLength128_1(r_5);
    int _S77 = max(_S76 - int(1) - int(52), int(-1074) - e_3);
    ulong q_3;
    if(_S77 <= int(0))
    {
        U128_0 _S78 = shl128_1(r_5, - _S77);
        q_3 = _S78.lo_0;
    }
    else
    {
        if(_S77 > int(128))
        {
            q_3 = 0ULL;
        }
        else
        {
            U128_0 qq_3;
            U128_0 rem_3;
            if(_S77 == int(128))
            {
                qq_3 = u128_0(0ULL, 0ULL);
                rem_3 = *r_5;
            }
            else
            {
                if(_S77 >= int(64))
                {
                    int t_3 = _S77 - int(64);
                    bool _S79 = t_3 == int(0);
                    if(_S79)
                    {
                        q_3 = r_5->hi_0;
                    }
                    else
                    {
                        q_3 = (r_5->hi_0) >> ulong(t_3);
                    }
                    U128_0 _S80 = u128_0(0ULL, q_3);
                    if(_S79)
                    {
                        q_3 = 0ULL;
                    }
                    else
                    {
                        q_3 = (r_5->hi_0) & ((1ULL << ulong(t_3)) - 1ULL);
                    }
                    U128_0 _S81 = u128_0(q_3, r_5->lo_0);
                    qq_3 = _S80;
                    rem_3 = _S81;
                }
                else
                {
                    ulong _S82 = ulong(_S77);
                    U128_0 _S83 = u128_0(0ULL, (r_5->lo_0) & ((1ULL << _S82) - 1ULL));
                    qq_3 = u128_0((r_5->hi_0) >> _S82, ((r_5->lo_0) >> _S82) | ((r_5->hi_0) << ulong(int(64) - _S77)));
                    rem_3 = _S83;
                }
            }
            int _S84 = _S77 - int(1);
            thread U128_0 _S85 = u128_0(0ULL, 1ULL);
            U128_0 _S86 = shl128_1(&_S85, _S84);
            U128_0 _S87 = qq_3;
            thread U128_0 _S88 = _S86;
            thread U128_0 _S89 = rem_3;
            bool _S90 = less128_1(&_S88, &_S89);
            bool _S91;
            if(_S90)
            {
                _S91 = true;
            }
            else
            {
                if((rem_3.hi_0) == (_S86.hi_0))
                {
                    _S91 = (rem_3.lo_0) == (_S86.lo_0);
                }
                else
                {
                    _S91 = false;
                }
                if(_S91)
                {
                    _S91 = ((_S87.lo_0) & 1ULL) != 0ULL;
                }
                else
                {
                    _S91 = false;
                }
            }
            if(_S91)
            {
                q_3 = _S87.lo_0 + 1ULL;
            }
            else
            {
                q_3 = _S87.lo_0;
            }
        }
    }
    int biasedMinusOne_3 = e_3 + _S77 + int(1074);
    if(biasedMinusOne_3 >= int(2046))
    {
        return sign_4 | 9218868437227405312ULL;
    }
    ulong bits_3 = (ulong(biasedMinusOne_3) << 52U) + q_3;
    if(bits_3 >= 9218868437227405312ULL)
    {
        q_3 = 9218868437227405312ULL;
    }
    else
    {
        q_3 = bits_3;
    }
    return sign_4 | q_3;
}

ulong dFromFloat_0(float f_0)
{
    uint u_0 = (as_type<uint>((f_0)));
    ulong sign_5 = ulong(u_0 & 2147483648U) << 32U;
    uint be_0 = (u_0 >> 23U) & 255U;
    uint m_0 = u_0 & 8388607U;
    if(be_0 == 255U)
    {
        ulong _S92;
        if(m_0 != 0U)
        {
            _S92 = 9221120237041090560ULL;
        }
        else
        {
            _S92 = sign_5 | 9218868437227405312ULL;
        }
        return _S92;
    }
    if(be_0 == 0U)
    {
        if(m_0 == 0U)
        {
            return sign_5;
        }
        thread U128_0 _S93 = u128_0(0ULL, ulong(m_0));
        ulong _S94 = dRoundPack_1(sign_5, &_S93, int(-149));
        return _S94;
    }
    return (sign_5 | (ulong(be_0 - 127U + 1023U) << 52U)) | (ulong(m_0) << 29U);
}

bool dIsNaN_0(ulong a_5)
{
    return (a_5 & 9223372036854775807ULL) > 9218868437227405312ULL;
}

bool dIsInf_0(ulong a_6)
{
    return (a_6 & 9223372036854775807ULL) == 9218868437227405312ULL;
}

bool dIsZero_0(ulong a_7)
{
    return (a_7 & 9223372036854775807ULL) == 0ULL;
}

void dUnpack_0(ulong a_8, ulong thread* sig_0, int thread* e_4)
{
    int be_1 = int((a_8 >> 52U) & 2047ULL);
    *sig_0 = a_8 & 4503599627370495ULL;
    if(be_1 == int(0))
    {
        *e_4 = int(-1074);
    }
    else
    {
        *sig_0 = (*sig_0) | 4503599627370496ULL;
        *e_4 = be_1 - int(1075);
    }
    return;
}

void dUnpack_1(ulong a_9, ulong thread* sig_1, int thread* e_5)
{
    int be_2 = int((a_9 >> 52U) & 2047ULL);
    *sig_1 = a_9 & 4503599627370495ULL;
    if(be_2 == int(0))
    {
        *e_5 = int(-1074);
    }
    else
    {
        *sig_1 = (*sig_1) | 4503599627370496ULL;
        *e_5 = be_2 - int(1075);
    }
    return;
}

void dUnpack_2(ulong a_10, ulong thread* sig_2, int thread* e_6)
{
    int be_3 = int((a_10 >> 52U) & 2047ULL);
    *sig_2 = a_10 & 4503599627370495ULL;
    if(be_3 == int(0))
    {
        *e_6 = int(-1074);
    }
    else
    {
        *sig_2 = (*sig_2) | 4503599627370496ULL;
        *e_6 = be_3 - int(1075);
    }
    return;
}

void dUnpack_3(ulong a_11, ulong thread* sig_3, int thread* e_7)
{
    int be_4 = int((a_11 >> 52U) & 2047ULL);
    *sig_3 = a_11 & 4503599627370495ULL;
    if(be_4 == int(0))
    {
        *e_7 = int(-1074);
    }
    else
    {
        *sig_3 = (*sig_3) | 4503599627370496ULL;
        *e_7 = be_4 - int(1075);
    }
    return;
}

U128_0 mul64_0(ulong a_12, ulong b_4)
{
    ulong a0_0 = a_12 & 4294967295ULL;
    ulong a1_0 = a_12 >> 32U;
    ulong b0_0 = b_4 & 4294967295ULL;
    ulong b1_0 = b_4 >> 32U;
    ulong p00_0 = a0_0 * b0_0;
    ulong p01_0 = a0_0 * b1_0;
    ulong p10_0 = a1_0 * b0_0;
    ulong mid_0 = (p00_0 >> 32U) + (p01_0 & 4294967295ULL) + (p10_0 & 4294967295ULL);
    return u128_0(a1_0 * b1_0 + (p01_0 >> 32U) + (p10_0 >> 32U) + (mid_0 >> 32U), (mid_0 << 32U) | (p00_0 & 4294967295ULL));
}

U128_0 shrSticky128_0(const U128_0 thread* x_8, int s_2)
{
    if(s_2 <= int(0))
    {
        return *x_8;
    }
    if(s_2 >= int(128))
    {
        int _S95;
        if(((x_8->hi_0) | (x_8->lo_0)) != 0ULL)
        {
            _S95 = int(1);
        }
        else
        {
            _S95 = int(0);
        }
        return u128_0(0ULL, ulong(_S95));
    }
    thread U128_0 r_6;
    ulong lost_0;
    if(s_2 >= int(64))
    {
        int t_4 = s_2 - int(64);
        bool _S96 = t_4 == int(0);
        if(_S96)
        {
            lost_0 = x_8->hi_0;
        }
        else
        {
            lost_0 = (x_8->hi_0) >> ulong(t_4);
        }
        r_6 = u128_0(0ULL, lost_0);
        ulong _S97 = x_8->lo_0;
        if(_S96)
        {
            lost_0 = 0ULL;
        }
        else
        {
            lost_0 = (x_8->hi_0) << ulong(int(64) - t_4);
        }
        lost_0 = _S97 | lost_0;
    }
    else
    {
        ulong _S98 = ulong(s_2);
        ulong _S99 = ulong(int(64) - s_2);
        r_6 = u128_0((x_8->hi_0) >> _S98, ((x_8->lo_0) >> _S98) | ((x_8->hi_0) << _S99));
        lost_0 = (x_8->lo_0) << _S99;
    }
    if(lost_0 != 0ULL)
    {
        (&r_6)->lo_0 = ((&r_6)->lo_0) | 1ULL;
    }
    return r_6;
}

U128_0 add128_0(const U128_0 thread* a_13, const U128_0 thread* b_5)
{
    ulong lo_2 = a_13->lo_0 + b_5->lo_0;
    ulong _S100 = a_13->hi_0 + b_5->hi_0;
    int _S101;
    if(lo_2 < (a_13->lo_0))
    {
        _S101 = int(1);
    }
    else
    {
        _S101 = int(0);
    }
    return u128_0(_S100 + ulong(_S101), lo_2);
}

U128_0 sub128_0(const U128_0 thread* a_14, const U128_0 thread* b_6)
{
    ulong _S102 = a_14->hi_0 - b_6->hi_0;
    ulong _S103 = a_14->lo_0;
    ulong _S104 = b_6->lo_0;
    int _S105;
    if((a_14->lo_0) < (b_6->lo_0))
    {
        _S105 = int(1);
    }
    else
    {
        _S105 = int(0);
    }
    return u128_0(_S102 - ulong(_S105), _S103 - _S104);
}

ulong dFma_0(ulong a_15, ulong b_7, ulong c_1)
{
    bool _S106;
    if(dIsNaN_0(a_15))
    {
        _S106 = true;
    }
    else
    {
        _S106 = dIsNaN_0(b_7);
    }
    if(_S106)
    {
        _S106 = true;
    }
    else
    {
        _S106 = dIsNaN_0(c_1);
    }
    if(_S106)
    {
        return 9221120237041090560ULL;
    }
    ulong sp_0 = (a_15 ^ b_7) & 9223372036854775808ULL;
    ulong sc_0 = c_1 & 9223372036854775808ULL;
    if(dIsInf_0(a_15))
    {
        _S106 = true;
    }
    else
    {
        _S106 = dIsInf_0(b_7);
    }
    if(_S106)
    {
        if(dIsZero_0(a_15))
        {
            _S106 = true;
        }
        else
        {
            _S106 = dIsZero_0(b_7);
        }
        if(_S106)
        {
            return 9221120237041090560ULL;
        }
        if(dIsInf_0(c_1))
        {
            _S106 = sc_0 != sp_0;
        }
        else
        {
            _S106 = false;
        }
        if(_S106)
        {
            return 9221120237041090560ULL;
        }
        return sp_0 | 9218868437227405312ULL;
    }
    if(dIsInf_0(c_1))
    {
        return c_1;
    }
    if(dIsZero_0(a_15))
    {
        _S106 = true;
    }
    else
    {
        _S106 = dIsZero_0(b_7);
    }
    if(_S106)
    {
        if(dIsZero_0(c_1))
        {
            ulong _S107;
            if(sp_0 == sc_0)
            {
                _S107 = c_1;
            }
            else
            {
                _S107 = 0ULL;
            }
            return _S107;
        }
        return c_1;
    }
    thread ulong ma_0;
    thread int ea_0;
    dUnpack_0(a_15, &ma_0, &ea_0);
    thread ulong mb_0;
    thread int eb_0;
    dUnpack_0(b_7, &mb_0, &eb_0);
    U128_0 p_0 = mul64_0(ma_0, mb_0);
    int ep_0 = ea_0 + eb_0;
    if(dIsZero_0(c_1))
    {
        thread U128_0 _S108 = p_0;
        ulong _S109 = dRoundPack_0(sp_0, &_S108, ep_0);
        return _S109;
    }
    thread ulong mc_0;
    thread int ec_0;
    dUnpack_0(c_1, &mc_0, &ec_0);
    U128_0 cc_0 = u128_0(0ULL, mc_0);
    thread U128_0 _S110 = p_0;
    int _S111 = bitLength128_0(&_S110);
    int e_8 = max(ep_0 + _S111 - int(1), ec_0 + bitLength64_0(mc_0) - int(1)) - int(125);
    int _S112 = ep_0 - e_8;
    U128_0 x_9;
    if(_S112 >= int(0))
    {
        thread U128_0 _S113 = p_0;
        U128_0 _S114 = shl128_0(&_S113, _S112);
        x_9 = _S114;
    }
    else
    {
        int _S115 = e_8 - ep_0;
        thread U128_0 _S116 = p_0;
        U128_0 _S117 = shrSticky128_0(&_S116, _S115);
        x_9 = _S117;
    }
    U128_0 y_0;
    if((ec_0 - e_8) >= int(0))
    {
        int _S118 = ec_0 - e_8;
        thread U128_0 _S119 = cc_0;
        U128_0 _S120 = shl128_0(&_S119, _S118);
        y_0 = _S120;
    }
    else
    {
        int _S121 = e_8 - ec_0;
        thread U128_0 _S122 = cc_0;
        U128_0 _S123 = shrSticky128_0(&_S122, _S121);
        y_0 = _S123;
    }
    if(sp_0 == sc_0)
    {
        thread U128_0 _S124 = x_9;
        thread U128_0 _S125 = y_0;
        U128_0 _S126 = add128_0(&_S124, &_S125);
        thread U128_0 _S127 = _S126;
        ulong _S128 = dRoundPack_0(sp_0, &_S127, e_8);
        return _S128;
    }
    if((x_9.hi_0) == (y_0.hi_0))
    {
        _S106 = (x_9.lo_0) == (y_0.lo_0);
    }
    else
    {
        _S106 = false;
    }
    if(_S106)
    {
        return 0ULL;
    }
    thread U128_0 _S129 = x_9;
    thread U128_0 _S130 = y_0;
    bool _S131 = less128_0(&_S129, &_S130);
    if(_S131)
    {
        thread U128_0 _S132 = y_0;
        thread U128_0 _S133 = x_9;
        U128_0 _S134 = sub128_0(&_S132, &_S133);
        thread U128_0 _S135 = _S134;
        ulong _S136 = dRoundPack_0(sc_0, &_S135, e_8);
        return _S136;
    }
    thread U128_0 _S137 = x_9;
    thread U128_0 _S138 = y_0;
    U128_0 _S139 = sub128_0(&_S137, &_S138);
    thread U128_0 _S140 = _S139;
    ulong _S141 = dRoundPack_0(sp_0, &_S140, e_8);
    return _S141;
}

ulong dMul_0(ulong a_16, ulong b_8)
{
    return dFma_0(a_16, b_8, 9223372036854775808ULL);
}

ulong dAdd_0(ulong a_17, ulong b_9)
{
    return dFma_0(a_17, 4607182418800017408ULL, b_9);
}

float dToFloat_0(ulong a_18)
{
    uint sign_6 = uint(a_18 >> 32U) & 2147483648U;
    if(dIsNaN_0(a_18))
    {
        return (as_type<float>((2143289344U)));
    }
    if(dIsInf_0(a_18))
    {
        return (as_type<float>((sign_6 | 2139095040U)));
    }
    if(dIsZero_0(a_18))
    {
        return (as_type<float>((sign_6)));
    }
    thread ulong sig_4;
    thread int e_9;
    dUnpack_1(a_18, &sig_4, &e_9);
    int _S142 = max(bitLength64_0(sig_4) - int(1) - int(23), int(-149) - e_9);
    ulong q_4;
    if(_S142 >= int(64))
    {
        q_4 = 0ULL;
    }
    else
    {
        ulong _S143 = ulong(_S142);
        ulong q_5 = sig_4 >> _S143;
        ulong rem_4 = sig_4 & ((1ULL << _S143) - 1ULL);
        ulong halfway_0 = 1ULL << ulong(_S142 - int(1));
        bool _S144;
        if(rem_4 > halfway_0)
        {
            _S144 = true;
        }
        else
        {
            if(rem_4 == halfway_0)
            {
                _S144 = (q_5 & 1ULL) != 0ULL;
            }
            else
            {
                _S144 = false;
            }
        }
        if(_S144)
        {
            q_4 = q_5 + 1ULL;
        }
        else
        {
            q_4 = q_5;
        }
    }
    int biasedMinusOne_4 = e_9 + _S142 + int(149);
    if(biasedMinusOne_4 >= int(254))
    {
        return (as_type<float>((sign_6 | 2139095040U)));
    }
    uint bits_4 = uint(biasedMinusOne_4) * 8388608U + uint(q_4);
    if(bits_4 >= 2139095040U)
    {
        return (as_type<float>((sign_6 | 2139095040U)));
    }
    return (as_type<float>((sign_6 | bits_4)));
}

bool dLess_0(ulong a_19, ulong b_10)
{
    bool _S145;
    if(dIsNaN_0(a_19))
    {
        _S145 = true;
    }
    else
    {
        _S145 = dIsNaN_0(b_10);
    }
    if(_S145)
    {
        return false;
    }
    if(dIsZero_0(a_19))
    {
        _S145 = dIsZero_0(b_10);
    }
    else
    {
        _S145 = false;
    }
    if(_S145)
    {
        return false;
    }
    bool na_0 = (a_19 & 9223372036854775808ULL) != 0ULL;
    if(na_0 != ((b_10 & 9223372036854775808ULL) != 0ULL))
    {
        return na_0;
    }
    if(na_0)
    {
        _S145 = a_19 > b_10;
    }
    else
    {
        _S145 = a_19 < b_10;
    }
    return _S145;
}

ulong dMin_0(ulong a_20, ulong b_11)
{
    bool _S146;
    if(dIsNaN_0(a_20))
    {
        _S146 = true;
    }
    else
    {
        _S146 = dIsNaN_0(b_11);
    }
    if(_S146)
    {
        return 9221120237041090560ULL;
    }
    if(dIsZero_0(a_20))
    {
        _S146 = dIsZero_0(b_11);
    }
    else
    {
        _S146 = false;
    }
    if(_S146)
    {
        return a_20 | b_11;
    }
    ulong _S147;
    if(dLess_0(b_11, a_20))
    {
        _S147 = b_11;
    }
    else
    {
        _S147 = a_20;
    }
    return _S147;
}

ulong dFromLong_0(long v_1)
{
    if(v_1 == 0LL)
    {
        return 0ULL;
    }
    bool _S148 = v_1 < 0LL;
    ulong sign_7;
    if(_S148)
    {
        sign_7 = 9223372036854775808ULL;
    }
    else
    {
        sign_7 = 0ULL;
    }
    ulong m_1;
    if(_S148)
    {
        m_1 = ulong(- v_1);
    }
    else
    {
        m_1 = ulong(v_1);
    }
    thread U128_0 _S149 = u128_0(0ULL, m_1);
    ulong _S150 = dRoundPack_3(sign_7, &_S149, int(0));
    return _S150;
}

ulong dDiv_0(ulong a_21, ulong b_12)
{
    bool _S151;
    if(dIsNaN_0(a_21))
    {
        _S151 = true;
    }
    else
    {
        _S151 = dIsNaN_0(b_12);
    }
    if(_S151)
    {
        return 9221120237041090560ULL;
    }
    ulong s_3 = (a_21 ^ b_12) & 9223372036854775808ULL;
    ulong q_6;
    if(dIsInf_0(a_21))
    {
        if(dIsInf_0(b_12))
        {
            q_6 = 9221120237041090560ULL;
        }
        else
        {
            q_6 = s_3 | 9218868437227405312ULL;
        }
        return q_6;
    }
    if(dIsInf_0(b_12))
    {
        return s_3;
    }
    if(dIsZero_0(b_12))
    {
        if(dIsZero_0(a_21))
        {
            q_6 = 9221120237041090560ULL;
        }
        else
        {
            q_6 = s_3 | 9218868437227405312ULL;
        }
        return q_6;
    }
    if(dIsZero_0(a_21))
    {
        return s_3;
    }
    thread ulong ma_1;
    thread int ea_1;
    dUnpack_2(a_21, &ma_1, &ea_1);
    thread ulong mb_1;
    thread int eb_1;
    dUnpack_2(b_12, &mb_1, &eb_1);
    int na_1 = int(53) - bitLength64_0(ma_1);
    int nb_0 = int(53) - bitLength64_0(mb_1);
    ulong _S152 = ma_1 << ulong(na_1);
    ma_1 = _S152;
    mb_1 = mb_1 << ulong(nb_0);
    ea_1 = ea_1 - na_1;
    eb_1 = eb_1 - nb_0;
    int i_0 = int(0);
    q_6 = 0ULL;
    ulong rem_5 = _S152;
    for(;;)
    {
        if(i_0 < int(58))
        {
        }
        else
        {
            break;
        }
        ulong q_7 = q_6 << 1U;
        ulong rem_6;
        if(rem_5 >= mb_1)
        {
            ulong q_8 = q_7 | 1ULL;
            rem_6 = rem_5 - mb_1;
            q_6 = q_8;
        }
        else
        {
            rem_6 = rem_5;
            q_6 = q_7;
        }
        ulong rem_7 = rem_6 << 1U;
        i_0 = i_0 + int(1);
        rem_5 = rem_7;
    }
    if(rem_5 != 0ULL)
    {
        q_6 = q_6 | 1ULL;
    }
    int _S153 = ea_1 - eb_1 - int(57);
    thread U128_0 _S154 = u128_0(0ULL, q_6);
    ulong _S155 = dRoundPack_2(s_3, &_S154, _S153);
    return _S155;
}

ulong dNeg_0(ulong a_22)
{
    return a_22 ^ 9223372036854775808ULL;
}

ulong dSub_0(ulong a_23, ulong b_13)
{
    return dAdd_0(a_23, dNeg_0(b_13));
}

long dToLong_0(ulong a_24)
{
    bool _S156;
    if(dIsNaN_0(a_24))
    {
        _S156 = true;
    }
    else
    {
        _S156 = dIsZero_0(a_24);
    }
    if(_S156)
    {
        return 0LL;
    }
    bool negative_0 = (a_24 & 9223372036854775808ULL) != 0ULL;
    long _S157;
    if(dIsInf_0(a_24))
    {
        if(negative_0)
        {
            _S157 = -9223372036854775808LL;
        }
        else
        {
            _S157 = 9223372036854775807LL;
        }
        return _S157;
    }
    thread ulong sig_5;
    thread int e_10;
    dUnpack_3(a_24, &sig_5, &e_10);
    if((e_10 + bitLength64_0(sig_5)) > int(63))
    {
        if(negative_0)
        {
            _S157 = -9223372036854775808LL;
        }
        else
        {
            _S157 = 9223372036854775807LL;
        }
        return _S157;
    }
    ulong m_2;
    if(e_10 >= int(0))
    {
        m_2 = sig_5 << ulong(e_10);
    }
    else
    {
        if(e_10 <= int(-64))
        {
            m_2 = 0ULL;
        }
        else
        {
            m_2 = sig_5 >> ulong(- e_10);
        }
    }
    long v_2 = long(m_2);
    if(negative_0)
    {
        _S157 = - v_2;
    }
    else
    {
        _S157 = v_2;
    }
    return _S157;
}

ulong dScalb_0(ulong a_25, int n_6)
{
    return dMul_0(a_25, ulong(n_6 + int(1023)) << 52U);
}

ulong dExp_0(ulong x_10)
{
    ulong p_1;
    if(!dLess_0(13873145138068324352ULL, x_10))
    {
        if(dIsNaN_0(x_10))
        {
            p_1 = x_10;
        }
        else
        {
            p_1 = 0ULL;
        }
        return p_1;
    }
    if(dLess_0(4649456441864749056ULL, x_10))
    {
        return 9218868437227405312ULL;
    }
    ulong kf_1 = dSub_0(dAdd_0(dMul_0(x_10, 4609176140021203710ULL), 4843621399236968448ULL), 4843621399236968448ULL);
    ulong r_7 = dFma_0(kf_1, 4451553433427316262ULL, dFma_0(kf_1, 13827790571168595968ULL, x_10));
    p_1 = 4460272573143870729ULL;
    int n_7 = int(10);
    for(;;)
    {
        if(n_7 >= int(0))
        {
        }
        else
        {
            break;
        }
        ulong _S158 = dFma_0(p_1, r_7, EXP_D_0[n_7]);
        int _S159 = n_7 - int(1);
        p_1 = _S158;
        n_7 = _S159;
    }
    int k_1 = int(dToLong_0(kf_1));
    int k1_1 = k_1 >> 1U;
    return dScalb_0(dScalb_0(dAdd_0(dFma_0(p_1, dMul_0(r_7, r_7), r_7), 4607182418800017408ULL), k1_1), k_1 - k1_1);
}

ulong erfcTailD_0(ulong a_26)
{
    ulong t_5 = a_26;
    int n_8 = int(120);
    for(;;)
    {
        if(n_8 >= int(1))
        {
        }
        else
        {
            break;
        }
        ulong _S160 = dAdd_0(a_26, dDiv_0(dMul_0(4602678819172646912ULL, dFromLong_0(long(n_8))), t_5));
        int _S161 = n_8 - int(1);
        t_5 = _S160;
        n_8 = _S161;
    }
    return dDiv_0(dExp_0(dMul_0(dNeg_0(a_26), a_26)), dMul_0(4610661241675116394ULL, t_5));
}

float normalCdf_0(float x_11)
{
    float SQRT2_0 = (as_type<float>((1068827891U)));
    if(!(x_11 <= (- SQRT2_0)))
    {
        bool _S162;
        if(x_11 >= SQRT2_0)
        {
            _S162 = true;
        }
        else
        {
            _S162 = x_11 != x_11;
        }
        if(_S162)
        {
            return 0.5 * (1.0 + reproducibleErf_0(x_11 * (as_type<float>((1060439283U)))));
        }
        ulong a_27 = dMul_0(dFromFloat_0(x_11), 4604544271217802189ULL);
        ulong _S163 = dMul_0(a_27, a_27);
        ulong p_2 = 13511757036778165052ULL;
        int n_9 = int(20);
        for(;;)
        {
            if(n_9 >= int(0))
            {
            }
            else
            {
                break;
            }
            ulong _S164 = dFma_0(p_2, _S163, ERF_D_0[n_9]);
            int _S165 = n_9 - int(1);
            p_2 = _S164;
            n_9 = _S165;
        }
        return dToFloat_0(dMul_0(4602678819172646912ULL, dAdd_0(4607182418800017408ULL, dMul_0(a_27, p_2))));
    }
    return dToFloat_0(dMul_0(4602678819172646912ULL, erfcTailD_0(dMin_0(dMul_0(dFromFloat_0(- x_11), 4604544271217802189ULL), 4621875412584313651ULL))));
}

float normalPdf_0(float x_12)
{
    ulong v_3 = dFromFloat_0(x_12);
    return dToFloat_0(dMul_0(4600858325139338833ULL, dExp_0(dMul_0(dMul_0(13826050856027422720ULL, v_3), v_3))));
}

float geluOp_0(uint op_0, float a_28, float b_14)
{
    if(op_0 == 0U)
    {
        return a_28 * normalCdf_0(a_28);
    }
    return b_14 * fma(a_28, normalPdf_0(a_28), normalCdf_0(a_28));
}

struct GeluSelfParams_0
{
    uint op_1;
    uint n_10;
    uint off_0;
    uint gridX_0;
};

struct KernelContext_0
{
    GeluSelfParams_0 constant* p_3;
    float device* x_13;
};

[[kernel]] void geluSelf(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], GeluSelfParams_0 constant* p_4 [[buffer(1)]], float device* x_14 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_3 = p_4;
    (&kernelContext_0)->x_13 = x_14;
    uint i_1 = (group_0.y * p_4->gridX_0 + group_0.x) * 256U + local_0.x;
    if(i_1 >= (p_4->n_10))
    {
        return;
    }
    float device* _S166 = (&kernelContext_0)->x_13+((&kernelContext_0)->p_3->off_0 + i_1);
    *((&kernelContext_0)->x_13+((&kernelContext_0)->p_3->off_0 + i_1)) = geluOp_0((&kernelContext_0)->p_3->op_1, *_S166, *_S166);
    return;
}

