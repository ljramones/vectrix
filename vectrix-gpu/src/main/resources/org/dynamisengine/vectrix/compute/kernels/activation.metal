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

float reproducibleSigmoid_0(float x_1)
{
    return 1.0 / (1.0 + reproducibleExp_0(- x_1));
}

float jabs_0(float x_2)
{
    return (as_type<float>(((as_type<uint>((x_2))) & 2147483647U)));
}

float reproducibleExpm1_0(float x_3)
{
    if((jabs_0(x_3)) < 0.5)
    {
        return fma(fma(fma(fma(fma(fma(fma(fma(fma(2.75573199814971304e-07, x_3, 2.75573188446287531e-06), x_3, 0.00002480158764229), x_3, 0.0001984127011383), x_3, 0.00138888892251998), x_3, 0.00833333376795053), x_3, 0.0416666679084301), x_3, 0.1666666716337204), x_3, 0.5), x_3 * x_3, x_3);
    }
    return reproducibleExp_0(x_3) - 1.0;
}

float jcopySign_0(float magnitude_0, float sign_0)
{
    return (as_type<float>((((as_type<uint>((magnitude_0))) & 2147483647U) | ((as_type<uint>((sign_0))) & 2147483648U))));
}

float reproducibleTanh_0(float x_4)
{
    float e_0 = reproducibleExpm1_0(2.0 * jmin_0(jabs_0(x_4), 9.10000038146972656));
    return jcopySign_0(e_0 / (e_0 + 2.0), x_4);
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

float activationOp_0(uint op_0, float a_2, float b_2)
{
    float K_0 = (as_type<float>((1061962282U)));
    float C_0 = (as_type<float>((1027024659U)));
    switch(op_0)
    {
    case 0U:
        {
            return jmax_0(a_2, 0.0);
        }
    case 1U:
        {
            return reproducibleSigmoid_0(a_2);
        }
    case 2U:
        {
            return a_2 * reproducibleSigmoid_0(a_2);
        }
    case 3U:
        {
            return reproducibleTanh_0(a_2);
        }
    case 4U:
        {
            return 0.5 * a_2 * (1.0 + reproducibleTanh_0(K_0 * fma(C_0 * a_2, a_2 * a_2, a_2)));
        }
    case 5U:
        {
            return jmax_0(a_2, 0.0) + reproducibleLog1p_0(reproducibleExp_0(- jabs_0(a_2)));
        }
    case 6U:
        {
            float _S8;
            if(a_2 > 0.0)
            {
                _S8 = b_2;
            }
            else
            {
                _S8 = 0.0;
            }
            return _S8;
        }
    case 7U:
        {
            return b_2 * (a_2 * (1.0 - a_2));
        }
    case 8U:
        {
            float s_1 = reproducibleSigmoid_0(a_2);
            return b_2 * fma(a_2 * s_1, 1.0 - s_1, s_1);
        }
    case 9U:
        {
            return b_2 * ((1.0 - a_2) * (1.0 + a_2));
        }
    case 10U:
        {
            float v2_0 = a_2 * a_2;
            float t_0 = reproducibleTanh_0(K_0 * fma(C_0 * a_2, v2_0, a_2));
            float _S9 = 1.0 + t_0;
            return b_2 * fma(0.5 * a_2 * ((1.0 - t_0) * _S9), K_0 * fma((as_type<float>((1040801102U))), v2_0, 1.0), 0.5 * _S9);
        }
    default:
        {
            return b_2 * reproducibleSigmoid_0(a_2);
        }
    }
}

struct ActivationParams_0
{
    uint op_1;
    uint n_0;
    uint aOff_0;
    uint bOff_0;
    uint destOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ActivationParams_0 constant* p_0;
    float device* b_3;
    float device* dest_0;
    float device* a_3;
};

[[kernel]] void activation(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ActivationParams_0 constant* p_1 [[buffer(3)]], float device* b_4 [[buffer(1)]], float device* dest_1 [[buffer(2)]], float device* a_4 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->b_3 = b_4;
    (&kernelContext_0)->dest_0 = dest_1;
    (&kernelContext_0)->a_3 = a_4;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(i_0 >= (p_1->n_0))
    {
        return;
    }
    float bv_0;
    if(((&kernelContext_0)->p_0->op_1) >= 6U)
    {
        bv_0 = *((&kernelContext_0)->b_3+((&kernelContext_0)->p_0->bOff_0 + i_0));
    }
    else
    {
        bv_0 = 0.0;
    }
    *((&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->destOff_0 + i_0)) = activationOp_0((&kernelContext_0)->p_0->op_1, *((&kernelContext_0)->a_3+((&kernelContext_0)->p_0->aOff_0 + i_0)), bv_0);
    return;
}

