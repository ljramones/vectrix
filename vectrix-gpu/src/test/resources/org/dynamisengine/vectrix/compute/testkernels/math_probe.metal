#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float jmax_0(float a_0, float b_0)
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
        _S1 = (as_type<uint>((a_0))) == 2147483648U;
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
    if(a_0 >= b_0)
    {
        _S2 = a_0;
    }
    else
    {
        _S2 = b_0;
    }
    return _S2;
}

float jmin_0(float a_1, float b_1)
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
        _S3 = (as_type<uint>((b_1))) == 2147483648U;
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
    if(a_1 <= b_1)
    {
        _S4 = a_1;
    }
    else
    {
        _S4 = b_1;
    }
    return _S4;
}

float reproducibleExp_0(float x_0)
{
    float c_0 = jmin_0(jmax_0(x_0, -104.0), 89.0);
    float kf_0 = c_0 * 1.4426950216293335 + 1.2582912e+07 - 1.2582912e+07;
    float r_0 = fma(kf_0, 0.00021219444170129, fma(kf_0, -0.693359375, c_0));
    int k_0 = int(kf_0);
    int k1_0 = k_0 >> 1U;
    return (fma(fma(fma(fma(fma(fma(0.0001984127011383, r_0, 0.00138888892251998), r_0, 0.00833333376795053), r_0, 0.0416666679084301), r_0, 0.1666666716337204), r_0, 0.5), r_0 * r_0, r_0) + 1.0) * (as_type<float>((uint((k1_0 + int(127)) << 23U)))) * (as_type<float>((uint((k_0 - k1_0 + int(127)) << 23U))));
}

float jabs_0(float x_1)
{
    return (as_type<float>(((as_type<uint>((x_1))) & 2147483647U)));
}

float reproducibleExpm1_0(float x_2)
{
    if((jabs_0(x_2)) < 0.5)
    {
        return fma(fma(fma(fma(fma(fma(fma(fma(fma(2.75573199814971304e-07, x_2, 2.75573188446287531e-06), x_2, 0.00002480158764229), x_2, 0.0001984127011383), x_2, 0.00138888892251998), x_2, 0.00833333376795053), x_2, 0.0416666679084301), x_2, 0.1666666716337204), x_2, 0.5), x_2 * x_2, x_2);
    }
    return reproducibleExp_0(x_2) - 1.0;
}

float jcopySign_0(float magnitude_0, float sign_0)
{
    return (as_type<float>((((as_type<uint>((magnitude_0))) & 2147483647U) | ((as_type<uint>((sign_0))) & 2147483648U))));
}

float reproducibleTanh_0(float x_3)
{
    float e_0 = reproducibleExpm1_0(2.0 * jmin_0(jabs_0(x_3), 9.10000038146972656));
    return jcopySign_0(e_0 / (e_0 + 2.0), x_3);
}

float reproducibleSigmoid_0(float x_4)
{
    return 1.0 / (1.0 + reproducibleExp_0(- x_4));
}

float logReduced_0(float f_0, int e_1)
{
    float s_0 = f_0 / (2.0 + f_0);
    float z_0 = s_0 * s_0;
    float ef_0 = float(e_1);
    return fma(ef_0, 0.693359375, fma(ef_0, -0.00021219444170129, 2.0 * fma(s_0, z_0 * fma(fma(fma(0.1111111119389534, z_0, 0.1428571492433548), z_0, 0.20000000298023224), z_0, 0.3333333432674408), s_0)));
}

float reproducibleLog_0(float x_5)
{
    bool _S5;
    if(!(x_5 > 0.0))
    {
        _S5 = true;
    }
    else
    {
        _S5 = x_5 == (as_type<float>((2139095040U)));
    }
    float m_0;
    if(_S5)
    {
        if(x_5 == 0.0)
        {
            m_0 = (as_type<float>((4286578688U)));
        }
        else
        {
            if(x_5 == (as_type<float>((2139095040U))))
            {
                m_0 = x_5;
            }
            else
            {
                m_0 = (as_type<float>((2143289344U)));
            }
        }
        return m_0;
    }
    int sub_0;
    if(x_5 < (as_type<float>((8388608U))))
    {
        float _S6 = x_5 * 8.388608e+06;
        sub_0 = int(23);
        m_0 = _S6;
    }
    else
    {
        sub_0 = int(0);
        m_0 = x_5;
    }
    int bits_0 = (as_type<int>((m_0)));
    int e_2 = (bits_0 >> 23U) - int(127) - sub_0;
    float m_1 = (as_type<float>((uint((bits_0 & int(8388607)) | int(1065353216)))));
    int e_3;
    if(m_1 > 1.41421353816986084)
    {
        int e_4 = e_2 + int(1);
        m_0 = m_1 * 0.5;
        e_3 = e_4;
    }
    else
    {
        m_0 = m_1;
        e_3 = e_2;
    }
    return logReduced_0(m_0 - 1.0, e_3);
}

float reproducibleLog1p_0(float y_0)
{
    bool _S7;
    if(y_0 > -0.29289323091506958)
    {
        _S7 = y_0 < 0.41421356797218323;
    }
    else
    {
        _S7 = false;
    }
    if(_S7)
    {
        return logReduced_0(y_0, int(0));
    }
    return reproducibleLog_0(1.0 + y_0);
}

float evaluate_0(uint fn_0, float x_6)
{
    switch(fn_0)
    {
    case 0U:
        {
            return reproducibleExp_0(x_6);
        }
    case 1U:
        {
            return reproducibleExpm1_0(x_6);
        }
    case 2U:
        {
            return reproducibleTanh_0(x_6);
        }
    case 3U:
        {
            return reproducibleSigmoid_0(x_6);
        }
    case 4U:
        {
            return reproducibleLog_0(x_6);
        }
    case 5U:
        {
            return reproducibleLog1p_0(x_6);
        }
    case 6U:
        {
            return sqrt(x_6);
        }
    case 7U:
        {
            return jabs_0(x_6);
        }
    default:
        {
            return (as_type<float>((2143289344U)));
        }
    }
}

struct ProbeParams_0
{
    uint fn_1;
    uint base_0;
    uint stride_0;
    uint count_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ProbeParams_0 constant* p_0;
    uint device* outBits_0;
};

[[kernel]] void probe(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ProbeParams_0 constant* p_1 [[buffer(1)]], uint device* outBits_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->outBits_0 = outBits_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    *((&kernelContext_0)->outBits_0+i_0) = (as_type<uint>((evaluate_0((&kernelContext_0)->p_0->fn_1, (as_type<float>(((&kernelContext_0)->p_0->base_0 + i_0 * (&kernelContext_0)->p_0->stride_0)))))));
    return;
}

