#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float times2p64_0(uint b_0)
{
    uint mag_0 = b_0 & 2147483647U;
    float r_0;
    if(mag_0 < 8388608U)
    {
        r_0 = float(mag_0) * (as_type<float>((352321536U)));
    }
    else
    {
        r_0 = (as_type<float>((mag_0 + 536870912U)));
    }
    return (as_type<float>(((as_type<uint>((r_0))) | (b_0 & 2147483648U))));
}

uint int8Scale_0(uint amax_0)
{
    if(amax_0 == 0U)
    {
        return 0U;
    }
    if(amax_0 >= 75497472U)
    {
        return (as_type<uint>(((as_type<float>((amax_0))) / 127.0)));
    }
    uint _S1 = (as_type<uint>((times2p64_0(amax_0) / 127.0)));
    if(_S1 >= 545259520U)
    {
        return _S1 - 536870912U;
    }
    uint u_0;
    if(amax_0 < 8388608U)
    {
        u_0 = amax_0;
    }
    else
    {
        u_0 = ((amax_0 & 8388607U) | 8388608U) << ((amax_0 >> 23U) - 1U);
    }
    return (u_0 + 126U) / 127U;
}

struct QuantizeInt8Params_0
{
    uint xOff_0;
    uint n_0;
    uint qOff_0;
    uint sOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    QuantizeInt8Params_0 constant* p_0;
    uint device* x_0;
    uint device* scales_0;
    uint device* q_0;
    array<uint, int(34)> threadgroup* blockScale_0;
};

uint blockScaleBits_0(uint base_0, KernelContext_0 thread* kernelContext_0)
{
    uint amax_1 = 0U;
    uint j_0 = 0U;
    for(;;)
    {
        if(j_0 < 32U)
        {
        }
        else
        {
            break;
        }
        uint _S2 = max(amax_1, (*(kernelContext_0->x_0+(base_0 + j_0))) & 2147483647U);
        uint _S3 = j_0 + 1U;
        amax_1 = _S2;
        j_0 = _S3;
    }
    if(amax_1 >= 2139095040U)
    {
        amax_1 = 2143289344U;
    }
    else
    {
        amax_1 = int8Scale_0(amax_1);
    }
    return amax_1;
}

float jmin_0(float a_0, float b_1)
{
    if(a_0 != a_0)
    {
        return a_0;
    }
    bool _S4;
    if(a_0 == 0.0)
    {
        _S4 = b_1 == 0.0;
    }
    else
    {
        _S4 = false;
    }
    if(_S4)
    {
        _S4 = (as_type<uint>((b_1))) == 2147483648U;
    }
    else
    {
        _S4 = false;
    }
    if(_S4)
    {
        return b_1;
    }
    float _S5;
    if(a_0 <= b_1)
    {
        _S5 = a_0;
    }
    else
    {
        _S5 = b_1;
    }
    return _S5;
}

float jmax_0(float a_1, float b_2)
{
    if(a_1 != a_1)
    {
        return a_1;
    }
    bool _S6;
    if(a_1 == 0.0)
    {
        _S6 = b_2 == 0.0;
    }
    else
    {
        _S6 = false;
    }
    if(_S6)
    {
        _S6 = (as_type<uint>((a_1))) == 2147483648U;
    }
    else
    {
        _S6 = false;
    }
    if(_S6)
    {
        return b_2;
    }
    float _S7;
    if(a_1 >= b_2)
    {
        _S7 = a_1;
    }
    else
    {
        _S7 = b_2;
    }
    return _S7;
}

uint int8Code_0(uint xb_0, uint sb_0)
{
    bool _S8;
    if(sb_0 == 0U)
    {
        _S8 = true;
    }
    else
    {
        _S8 = sb_0 == 2143289344U;
    }
    if(_S8)
    {
        return 0U;
    }
    float v_0;
    if(sb_0 < 226492416U)
    {
        v_0 = times2p64_0(xb_0) / times2p64_0(sb_0);
    }
    else
    {
        v_0 = (as_type<float>((xb_0))) / (as_type<float>((sb_0)));
    }
    return uint(int(jmax_0(-127.0, jmin_0(127.0, v_0 + 1.2582912e+07 - 1.2582912e+07)))) & 255U;
}

[[kernel]] void quantizeInt8(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], QuantizeInt8Params_0 constant* p_1 [[buffer(3)]], uint device* x_1 [[buffer(0)]], uint device* scales_1 [[buffer(2)]], uint device* q_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_1;
    (&kernelContext_1)->p_0 = p_1;
    (&kernelContext_1)->x_0 = x_1;
    (&kernelContext_1)->scales_0 = scales_1;
    (&kernelContext_1)->q_0 = q_1;
    threadgroup array<uint, int(34)> blockScale_1;
    (&kernelContext_1)->blockScale_0 = &blockScale_1;
    uint g_0 = group_0.y * p_1->gridX_0 + group_0.x;
    uint first_0 = (p_1->qOff_0) >> 2U;
    uint words_0 = ((p_1->qOff_0 + p_1->n_0 - 1U) >> 2U) - first_0 + 1U;
    if(g_0 >= ((words_0 + 256U - 1U) / 256U))
    {
        return;
    }
    uint t_0 = local_0.x;
    uint w0_0 = first_0 + g_0 * 256U;
    uint wLast_0 = min(w0_0 + 256U, first_0 + words_0) - 1U;
    uint _S9 = w0_0 * 4U;
    uint eStart_0;
    if(_S9 > (p_1->qOff_0))
    {
        eStart_0 = _S9 - p_1->qOff_0;
    }
    else
    {
        eStart_0 = 0U;
    }
    uint bFirst_0 = eStart_0 / 32U;
    if(t_0 < (min(p_1->n_0 - 1U, wLast_0 * 4U + 3U - p_1->qOff_0) / 32U - bFirst_0 + 1U))
    {
        uint b_3 = bFirst_0 + t_0;
        uint _S10 = b_3 * 32U;
        uint _S11 = blockScaleBits_0((&kernelContext_1)->p_0->xOff_0 + _S10, &kernelContext_1);
        (*(&kernelContext_1)->blockScale_0)[t_0] = _S11;
        if(_S10 >= eStart_0)
        {
            *((&kernelContext_1)->scales_0+((&kernelContext_1)->p_0->sOff_0 + b_3)) = _S11;
        }
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    uint w_0 = w0_0 + t_0;
    if(w_0 > wLast_0)
    {
        return;
    }
    uint lo_0 = w_0 * 4U;
    bool partial_0;
    if(lo_0 < (p_1->qOff_0))
    {
        partial_0 = true;
    }
    else
    {
        partial_0 = (lo_0 + 3U - p_1->qOff_0) >= (p_1->n_0);
    }
    if(partial_0)
    {
        eStart_0 = *((&kernelContext_1)->q_0+w_0);
    }
    else
    {
        eStart_0 = 0U;
    }
    uint word_0 = eStart_0;
    uint k_0 = 0U;
    for(;;)
    {
        if(k_0 < 4U)
        {
        }
        else
        {
            break;
        }
        uint i_0 = lo_0 + k_0;
        if(i_0 >= (p_1->qOff_0))
        {
            partial_0 = (i_0 - p_1->qOff_0) < (p_1->n_0);
        }
        else
        {
            partial_0 = false;
        }
        if(partial_0)
        {
            uint e_0 = i_0 - p_1->qOff_0;
            uint shift_0 = k_0 * 8U;
            word_0 = (word_0 & (~(255U << shift_0))) | ((int8Code_0(*((&kernelContext_1)->x_0+((&kernelContext_1)->p_0->xOff_0 + e_0)), (*(&kernelContext_1)->blockScale_0)[e_0 / 32U - bFirst_0])) << shift_0);
        }
        k_0 = k_0 + 1U;
    }
    *((&kernelContext_1)->q_0+w_0) = word_0;
    return;
}

