#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
int orderKey_0(uint bits_0)
{
    uint mag_0 = bits_0 & 2147483647U;
    if(mag_0 > 2139095040U)
    {
        return int(2147483647);
    }
    int m_0 = int(mag_0);
    int _S1;
    if((bits_0 & 2147483648U) != 0U)
    {
        _S1 = - m_0;
    }
    else
    {
        _S1 = m_0;
    }
    return _S1;
}

struct ArgmaxParams_0
{
    uint rows_0;
    uint n_0;
    uint off_0;
    uint ld_0;
    uint dOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ArgmaxParams_0 constant* p_0;
    uint device* logits_0;
    uint device* dest_0;
    array<int, int(256)> threadgroup* keys_0;
    array<uint, int(256)> threadgroup* indices_0;
};

[[kernel]] void argmax(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ArgmaxParams_0 constant* p_1 [[buffer(2)]], uint device* logits_1 [[buffer(0)]], uint device* dest_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->logits_0 = logits_1;
    (&kernelContext_0)->dest_0 = dest_1;
    threadgroup array<int, int(256)> keys_1;
    (&kernelContext_0)->keys_0 = &keys_1;
    threadgroup array<uint, int(256)> indices_1;
    (&kernelContext_0)->indices_0 = &indices_1;
    uint row_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(row_0 >= (p_1->rows_0))
    {
        return;
    }
    uint t_0 = local_0.x;
    uint _S2 = (&kernelContext_0)->p_0->off_0 + row_0 * (&kernelContext_0)->p_0->ld_0;
    int bestKey_0 = int(-2147483648);
    uint best_0 = 4294967295U;
    uint i_0 = t_0;
    for(;;)
    {
        if(i_0 < ((&kernelContext_0)->p_0->n_0))
        {
        }
        else
        {
            break;
        }
        int k_0 = orderKey_0(*((&kernelContext_0)->logits_0+(_S2 + i_0)));
        if(k_0 > bestKey_0)
        {
            bestKey_0 = k_0;
            best_0 = i_0;
        }
        i_0 = i_0 + 256U;
    }
    (*(&kernelContext_0)->keys_0)[t_0] = bestKey_0;
    (*(&kernelContext_0)->indices_0)[t_0] = best_0;
    threadgroup_barrier(mem_flags::mem_threadgroup);
    uint stride_0 = 128U;
    for(;;)
    {
        if(stride_0 > 0U)
        {
        }
        else
        {
            break;
        }
        if(t_0 < stride_0)
        {
            int k1_0 = (*(&kernelContext_0)->keys_0)[t_0];
            uint _S3 = t_0 + stride_0;
            int k2_0 = (*(&kernelContext_0)->keys_0)[_S3];
            uint i1_0 = (*(&kernelContext_0)->indices_0)[t_0];
            uint i2_0 = (*(&kernelContext_0)->indices_0)[_S3];
            bool _S4;
            if(((*(&kernelContext_0)->keys_0)[_S3]) > (*(&kernelContext_0)->keys_0)[t_0])
            {
                _S4 = true;
            }
            else
            {
                if(k2_0 == k1_0)
                {
                    _S4 = i2_0 < i1_0;
                }
                else
                {
                    _S4 = false;
                }
            }
            if(_S4)
            {
                (*(&kernelContext_0)->keys_0)[t_0] = k2_0;
                (*(&kernelContext_0)->indices_0)[t_0] = i2_0;
            }
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        stride_0 = stride_0 >> 1U;
    }
    if(t_0 == 0U)
    {
        *((&kernelContext_0)->dest_0+((&kernelContext_0)->p_0->dOff_0 + row_0)) = (*(&kernelContext_0)->indices_0)[int(0)];
    }
    return;
}

