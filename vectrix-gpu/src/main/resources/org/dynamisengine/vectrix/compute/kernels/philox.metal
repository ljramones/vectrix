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

struct PhiloxParams_0
{
    uint seedLo_0;
    uint seedHi_0;
    uint startLo_0;
    uint startHi_0;
    uint off_0;
    uint n_0;
    uint uniform_0;
    uint gridX_0;
};

struct KernelContext_0
{
    PhiloxParams_0 constant* p_0;
    uint device* dest_0;
};

[[kernel]] void philox(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], PhiloxParams_0 constant* p_1 [[buffer(1)]], uint device* dest_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->dest_0 = dest_1;
    uint t_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    uint skip_0 = (p_1->startLo_0) & 3U;
    if(t_0 >= ((skip_0 + p_1->n_0 + 3U) / 4U))
    {
        return;
    }
    uint base_0 = ((p_1->startLo_0) >> 2U) | (((&kernelContext_0)->p_0->startHi_0) << 30U);
    uint lo_0 = base_0 + t_0;
    uint _S9 = ((&kernelContext_0)->p_0->startHi_0) >> 2U;
    uint _S10;
    if(lo_0 < base_0)
    {
        _S10 = 1U;
    }
    else
    {
        _S10 = 0U;
    }
    uint4 _S11 = philoxBlock_0(uint4(lo_0, (_S9 + _S10) & 1073741823U, 0U, 0U), uint2((&kernelContext_0)->p_0->seedLo_0, (&kernelContext_0)->p_0->seedHi_0));
    uint _S12 = t_0 * 4U;
    int _S13 = int(skip_0);
    int e_0 = int(_S12) - _S13;
    bool _S14;
    if(e_0 >= int(0))
    {
        _S14 = uint(e_0) < (p_1->n_0);
    }
    else
    {
        _S14 = false;
    }
    if(_S14)
    {
        uint device* _S15 = (&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->off_0 + uint(e_0));
        if(((&kernelContext_0)->p_0->uniform_0) != 0U)
        {
            _S10 = (as_type<uint>((float((_S11[0U]) >> 8U) * (as_type<float>((864026624U))))));
        }
        else
        {
            _S10 = _S11[0U];
        }
        *_S15 = _S10;
    }
    int e_1 = int(_S12 + 1U) - _S13;
    if(e_1 >= int(0))
    {
        _S14 = uint(e_1) < (p_1->n_0);
    }
    else
    {
        _S14 = false;
    }
    if(_S14)
    {
        uint device* _S16 = (&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->off_0 + uint(e_1));
        if(((&kernelContext_0)->p_0->uniform_0) != 0U)
        {
            _S10 = (as_type<uint>((float((_S11[1U]) >> 8U) * (as_type<float>((864026624U))))));
        }
        else
        {
            _S10 = _S11[1U];
        }
        *_S16 = _S10;
    }
    int e_2 = int(_S12 + 2U) - _S13;
    if(e_2 >= int(0))
    {
        _S14 = uint(e_2) < (p_1->n_0);
    }
    else
    {
        _S14 = false;
    }
    if(_S14)
    {
        uint device* _S17 = (&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->off_0 + uint(e_2));
        if(((&kernelContext_0)->p_0->uniform_0) != 0U)
        {
            _S10 = (as_type<uint>((float((_S11[2U]) >> 8U) * (as_type<float>((864026624U))))));
        }
        else
        {
            _S10 = _S11[2U];
        }
        *_S17 = _S10;
    }
    int e_3 = int(_S12 + 3U) - _S13;
    if(e_3 >= int(0))
    {
        _S14 = uint(e_3) < (p_1->n_0);
    }
    else
    {
        _S14 = false;
    }
    if(_S14)
    {
        uint device* _S18 = (&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->off_0 + uint(e_3));
        if(((&kernelContext_0)->p_0->uniform_0) != 0U)
        {
            _S10 = (as_type<uint>((float((_S11[3U]) >> 8U) * (as_type<float>((864026624U))))));
        }
        else
        {
            _S10 = _S11[3U];
        }
        *_S18 = _S10;
    }
    return;
}

