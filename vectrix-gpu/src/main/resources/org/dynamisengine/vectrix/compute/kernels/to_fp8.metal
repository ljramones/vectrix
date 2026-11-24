#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
bool isNanBits_0(uint b_0)
{
    return (b_0 & 2147483647U) > 2139095040U;
}

float fp8Positive_0(uint k_0, uint e5m2_0)
{
    if(e5m2_0 != 0U)
    {
        uint e_0 = k_0 >> 2U;
        uint m_0 = k_0 & 3U;
        if(e_0 == 0U)
        {
            return float(m_0) * (as_type<float>((931135488U)));
        }
        return (as_type<float>((((e_0 + 112U) << 23U) | (m_0 << 21U))));
    }
    uint e_1 = k_0 >> 3U;
    uint m_1 = k_0 & 7U;
    if(e_1 == 0U)
    {
        return float(m_1) * (as_type<float>((989855744U)));
    }
    return (as_type<float>((((e_1 + 120U) << 23U) | (m_1 << 20U))));
}

uint fp8FromFloatBits_0(uint xb_0, uint e5m2_1, uint saturate_0)
{
    uint sign_0;
    if((xb_0 & 2147483648U) != 0U)
    {
        sign_0 = 128U;
    }
    else
    {
        sign_0 = 0U;
    }
    if(isNanBits_0(xb_0))
    {
        return 127U;
    }
    float a_0 = (as_type<float>((xb_0 & 2147483647U)));
    bool _S1 = e5m2_1 != 0U;
    int count_0;
    if(_S1)
    {
        count_0 = int(124);
    }
    else
    {
        count_0 = int(127);
    }
    int _S2 = count_0 - int(1);
    float maxValue_0 = fp8Positive_0(uint(_S2), e5m2_1);
    float over_0 = 2.0 * maxValue_0 - fp8Positive_0(uint(count_0 - int(2)), e5m2_1);
    bool _S3;
    if(a_0 >= over_0)
    {
        _S3 = true;
    }
    else
    {
        _S3 = a_0 == (as_type<float>((2139095040U)));
    }
    int code_0;
    if(_S3)
    {
        code_0 = int(-1);
    }
    else
    {
        if(a_0 > maxValue_0)
        {
            float dm_0 = a_0 - maxValue_0;
            float dv_0 = over_0 - a_0;
            if(dm_0 < dv_0)
            {
                _S3 = true;
            }
            else
            {
                if(dm_0 == dv_0)
                {
                    _S3 = (_S2 & int(1)) == int(0);
                }
                else
                {
                    _S3 = false;
                }
            }
            if(_S3)
            {
                count_0 = _S2;
            }
            else
            {
                count_0 = int(-1);
            }
            code_0 = count_0;
        }
        else
        {
            int lo_0 = int(0);
            int hi_0 = _S2;
            for(;;)
            {
                if(lo_0 < hi_0)
                {
                }
                else
                {
                    break;
                }
                int mid_0 = (lo_0 + hi_0 + int(1)) >> 1U;
                if((fp8Positive_0(uint(mid_0), e5m2_1)) <= a_0)
                {
                    lo_0 = mid_0;
                }
                else
                {
                    hi_0 = mid_0 - int(1);
                }
            }
            float v0_0 = fp8Positive_0(uint(lo_0), e5m2_1);
            if(v0_0 == a_0)
            {
                _S3 = true;
            }
            else
            {
                _S3 = lo_0 == _S2;
            }
            if(_S3)
            {
                code_0 = lo_0;
            }
            else
            {
                int _S4 = lo_0 + int(1);
                float d0_0 = a_0 - v0_0;
                float d1_0 = fp8Positive_0(uint(_S4), e5m2_1) - a_0;
                if(d0_0 < d1_0)
                {
                    count_0 = lo_0;
                }
                else
                {
                    if(d1_0 < d0_0)
                    {
                        count_0 = _S4;
                    }
                    else
                    {
                        if((lo_0 & int(1)) == int(0))
                        {
                            count_0 = lo_0;
                        }
                        else
                        {
                            count_0 = _S4;
                        }
                    }
                }
                code_0 = count_0;
            }
        }
    }
    if(code_0 < int(0))
    {
        if(saturate_0 != 0U)
        {
        }
        else
        {
            if(_S1)
            {
                sign_0 = 124U | sign_0;
            }
            else
            {
                sign_0 = 127U | sign_0;
            }
            return sign_0;
        }
        code_0 = _S2;
    }
    return uint(code_0) | sign_0;
}

struct ToFp8Params_0
{
    uint xOff_0;
    uint cOff_0;
    uint n_0;
    uint e5m2_2;
    uint saturate_1;
    uint gridX_0;
};

struct KernelContext_0
{
    ToFp8Params_0 constant* p_0;
    uint device* codes_0;
    uint device* x_0;
};

[[kernel]] void toFp8(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ToFp8Params_0 constant* p_1 [[buffer(2)]], uint device* codes_1 [[buffer(1)]], uint device* x_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->codes_0 = codes_1;
    (&kernelContext_0)->x_0 = x_1;
    uint t_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    uint first_0 = (p_1->cOff_0) >> 2U;
    if(t_0 > (((p_1->cOff_0 + p_1->n_0 - 1U) >> 2U) - first_0))
    {
        return;
    }
    uint w_0 = first_0 + t_0;
    uint lo_1 = w_0 * 4U;
    bool partial_0;
    if(lo_1 < (p_1->cOff_0))
    {
        partial_0 = true;
    }
    else
    {
        partial_0 = (lo_1 + 3U - p_1->cOff_0) >= (p_1->n_0);
    }
    uint word_0;
    if(partial_0)
    {
        word_0 = *((&kernelContext_0)->codes_0+w_0);
    }
    else
    {
        word_0 = 0U;
    }
    uint k_1 = 0U;
    for(;;)
    {
        if(k_1 < 4U)
        {
        }
        else
        {
            break;
        }
        uint i_0 = lo_1 + k_1;
        if(i_0 >= (p_1->cOff_0))
        {
            partial_0 = (i_0 - p_1->cOff_0) < (p_1->n_0);
        }
        else
        {
            partial_0 = false;
        }
        if(partial_0)
        {
            uint shift_0 = k_1 * 8U;
            word_0 = (word_0 & (~(255U << shift_0))) | ((fp8FromFloatBits_0(*((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + (i_0 - p_1->cOff_0))), (&kernelContext_0)->p_0->e5m2_2, (&kernelContext_0)->p_0->saturate_1)) << shift_0);
        }
        k_1 = k_1 + 1U;
    }
    *((&kernelContext_0)->codes_0+w_0) = word_0;
    return;
}

