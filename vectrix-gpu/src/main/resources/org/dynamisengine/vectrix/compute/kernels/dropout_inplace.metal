#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint2 mulWide_0(uint x_0, uint y_0)
{
    uint x0_0 = x_0 & 65535U;
    uint x1_0 = x_0 >> 16U;
    uint y0_0 = y_0 & 65535U;
    uint y1_0 = y_0 >> 16U;
    uint p01_0 = x0_0 * y1_0;
    uint p10_0 = x1_0 * y0_0;
    return uint2(x_0 * y_0, x1_0 * y1_0 + (p01_0 >> 16U) + (p10_0 >> 16U) + ((((x0_0 * y0_0) >> 16U) + (p01_0 & 65535U) + (p10_0 & 65535U)) >> 16U));
}

uint4 philoxBlock_0(uint4 ctr_0, uint2 key_0)
{
    uint _S1 = ctr_0.y;
    uint _S2 = ctr_0.z;
    uint _S3 = ctr_0.w;
    uint _S4 = key_0.x;
    uint _S5 = key_0.y;
    uint c0_0 = ctr_0.x;
    uint c2_0 = _S2;
    uint c1_0 = _S1;
    uint c3_0 = _S3;
    int r_0 = int(0);
    uint k0_0 = _S4;
    uint k1_0 = _S5;
    for(;;)
    {
        if(r_0 < int(10))
        {
        }
        else
        {
            break;
        }
        uint2 p0_0 = mulWide_0(3528531795U, c0_0);
        uint2 p1_0 = mulWide_0(3449720151U, c2_0);
        uint n0_0 = ((p1_0.y) ^ c1_0) ^ k0_0;
        uint n2_0 = ((p0_0.y) ^ c3_0) ^ k1_0;
        uint _S6 = p1_0.x;
        uint _S7 = p0_0.x;
        uint k0_1 = k0_0 + 2654435769U;
        uint k1_1 = k1_0 + 3144134277U;
        int _S8 = r_0 + int(1);
        c0_0 = n0_0;
        c2_0 = n2_0;
        c1_0 = _S6;
        c3_0 = _S7;
        r_0 = _S8;
        k0_0 = k0_1;
        k1_0 = k1_1;
    }
    return uint4(c0_0, c1_0, c2_0, c3_0);
}

uint4 streamBlock_0(uint seedLo_0, uint seedHi_0, uint startLo_0, uint startHi_0, uint t_0)
{
    uint base_0 = (startLo_0 >> 2U) | (startHi_0 << 30U);
    uint lo_0 = base_0 + t_0;
    uint _S9 = startHi_0 >> 2U;
    uint _S10;
    if(lo_0 < base_0)
    {
        _S10 = 1U;
    }
    else
    {
        _S10 = 0U;
    }
    return philoxBlock_0(uint4(lo_0, (_S9 + _S10) & 1073741823U, 0U, 0U), uint2(seedLo_0, seedHi_0));
}

float uniformOf_0(uint word_0)
{
    return float(word_0 >> 8U) * (as_type<float>((864026624U)));
}

void dropoutCounter_0(float device* x_1, uint xOff_0, float device* y_1, uint yOff_0, uint seedLo_1, uint seedHi_1, uint startLo_1, uint startHi_1, float prob_0, float scale_0, uint n_0, uint t_1)
{
    uint skip_0 = startLo_1 & 3U;
    if(t_1 >= ((skip_0 + n_0 + 3U) / 4U))
    {
        return;
    }
    uint4 _S11 = streamBlock_0(seedLo_1, seedHi_1, startLo_1, startHi_1, t_1);
    uint _S12 = t_1 * 4U;
    bool _S13;
    if(_S12 >= skip_0)
    {
        _S13 = (_S12 - skip_0) < n_0;
    }
    else
    {
        _S13 = false;
    }
    float _S14;
    if(_S13)
    {
        uint e_0 = _S12 - skip_0;
        float device* _S15 = y_1+(yOff_0 + e_0);
        if((uniformOf_0(_S11[0U])) >= prob_0)
        {
            _S14 = *(x_1+(xOff_0 + e_0)) * scale_0;
        }
        else
        {
            _S14 = 0.0;
        }
        *_S15 = _S14;
    }
    uint k_0 = _S12 + 1U;
    if(k_0 >= skip_0)
    {
        _S13 = (k_0 - skip_0) < n_0;
    }
    else
    {
        _S13 = false;
    }
    if(_S13)
    {
        uint e_1 = k_0 - skip_0;
        float device* _S16 = y_1+(yOff_0 + e_1);
        if((uniformOf_0(_S11[1U])) >= prob_0)
        {
            _S14 = *(x_1+(xOff_0 + e_1)) * scale_0;
        }
        else
        {
            _S14 = 0.0;
        }
        *_S16 = _S14;
    }
    uint k_1 = _S12 + 2U;
    if(k_1 >= skip_0)
    {
        _S13 = (k_1 - skip_0) < n_0;
    }
    else
    {
        _S13 = false;
    }
    if(_S13)
    {
        uint e_2 = k_1 - skip_0;
        float device* _S17 = y_1+(yOff_0 + e_2);
        if((uniformOf_0(_S11[2U])) >= prob_0)
        {
            _S14 = *(x_1+(xOff_0 + e_2)) * scale_0;
        }
        else
        {
            _S14 = 0.0;
        }
        *_S17 = _S14;
    }
    uint k_2 = _S12 + 3U;
    if(k_2 >= skip_0)
    {
        _S13 = (k_2 - skip_0) < n_0;
    }
    else
    {
        _S13 = false;
    }
    if(_S13)
    {
        uint e_3 = k_2 - skip_0;
        float device* _S18 = y_1+(yOff_0 + e_3);
        if((uniformOf_0(_S11[3U])) >= prob_0)
        {
            _S14 = *(x_1+(xOff_0 + e_3)) * scale_0;
        }
        else
        {
            _S14 = 0.0;
        }
        *_S18 = _S14;
    }
    return;
}

struct DropoutInPlaceParams_0
{
    uint seedLo_2;
    uint seedHi_2;
    uint startLo_2;
    uint startHi_2;
    float prob_1;
    float scale_1;
    uint xOff_1;
    uint n_1;
    uint gridX_0;
};

struct KernelContext_0
{
    DropoutInPlaceParams_0 constant* p_0;
    float device* x_2;
};

[[kernel]] void dropoutInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], DropoutInPlaceParams_0 constant* p_1 [[buffer(1)]], float device* x_3 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->x_2 = x_3;
    dropoutCounter_0(x_3, p_1->xOff_1, x_3, p_1->xOff_1, p_1->seedLo_2, p_1->seedHi_2, p_1->startLo_2, p_1->startHi_2, p_1->prob_1, p_1->scale_1, p_1->n_1, (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x);
    return;
}

