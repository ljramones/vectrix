#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
bool before_0(uint ka_0, uint ia_0, uint kb_0, uint ib_0)
{
    if(ib_0 == 4294967295U)
    {
        return ia_0 != 4294967295U;
    }
    if(ia_0 == 4294967295U)
    {
        return false;
    }
    bool na_0 = ka_0 > 2139095040U;
    bool nb_0 = kb_0 > 2139095040U;
    bool _S1;
    if(na_0)
    {
        _S1 = true;
    }
    else
    {
        _S1 = nb_0;
    }
    if(_S1)
    {
        if(na_0)
        {
            _S1 = nb_0;
        }
        else
        {
            _S1 = false;
        }
        if(_S1)
        {
            _S1 = ia_0 < ib_0;
        }
        else
        {
            _S1 = na_0;
        }
        return _S1;
    }
    if(ka_0 != kb_0)
    {
        return ka_0 > kb_0;
    }
    return ia_0 < ib_0;
}

struct AmaxParams_0
{
    uint mode_0;
    uint n_0;
    uint xOff_0;
    uint stage_0;
    uint parts_0;
    uint partOff_0;
    uint stateOff_0;
    uint outOff_0;
    uint need_0;
    uint gridX_0;
};

struct KernelContext_0
{
    AmaxParams_0 constant* p_0;
    uint device* scratch_0;
    uint device* x_0;
    uint device* result_0;
    array<uint, int(256)> threadgroup* keys_0;
    array<uint, int(256)> threadgroup* indices_0;
};

[[kernel]] void blasAmax(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], AmaxParams_0 constant* p_1 [[buffer(3)]], uint device* scratch_1 [[buffer(1)]], uint device* x_1 [[buffer(0)]], uint device* result_1 [[buffer(2)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->scratch_0 = scratch_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->result_0 = result_1;
    threadgroup array<uint, int(256)> keys_1;
    (&kernelContext_0)->keys_0 = &keys_1;
    threadgroup array<uint, int(256)> indices_1;
    (&kernelContext_0)->indices_0 = &indices_1;
    uint g_0 = group_0.y * p_1->gridX_0 + group_0.x;
    bool _S2;
    if((p_1->stage_0) == 0U)
    {
        _S2 = g_0 >= ((&kernelContext_0)->p_0->parts_0);
    }
    else
    {
        _S2 = g_0 != 0U;
    }
    if(_S2)
    {
        return;
    }
    if(((&kernelContext_0)->p_0->need_0) != 0U)
    {
        _S2 = (*((&kernelContext_0)->scratch_0+(&kernelContext_0)->p_0->stateOff_0)) != ((&kernelContext_0)->p_0->need_0);
    }
    else
    {
        _S2 = false;
    }
    if(_S2)
    {
        return;
    }
    uint t_0 = local_0.x;
    uint bk_0;
    uint bi_0;
    uint stride_0;
    if((p_1->stage_0) == 0U)
    {
        uint _S3 = g_0 * 256U + t_0;
        bk_0 = 0U;
        bi_0 = 4294967295U;
        stride_0 = _S3;
        for(;;)
        {
            if(stride_0 < ((&kernelContext_0)->p_0->n_0))
            {
            }
            else
            {
                break;
            }
            uint k_0 = (*((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + stride_0))) & 2147483647U;
            if(before_0(k_0, stride_0, bk_0, bi_0))
            {
                bk_0 = k_0;
                bi_0 = stride_0;
            }
            stride_0 = stride_0 + (&kernelContext_0)->p_0->parts_0 * 256U;
        }
    }
    else
    {
        bk_0 = 0U;
        bi_0 = 4294967295U;
        stride_0 = t_0;
        for(;;)
        {
            if(stride_0 < ((&kernelContext_0)->p_0->parts_0))
            {
            }
            else
            {
                break;
            }
            uint _S4 = 2U * stride_0;
            uint device* _S5 = (&kernelContext_0)->scratch_0+((&kernelContext_0)->p_0->partOff_0 + _S4);
            uint k_1 = *_S5;
            uint device* _S6 = (&kernelContext_0)->scratch_0+((&kernelContext_0)->p_0->partOff_0 + _S4 + 1U);
            uint i_0 = *_S6;
            if(before_0(*_S5, *_S6, bk_0, bi_0))
            {
                bk_0 = k_1;
                bi_0 = i_0;
            }
            stride_0 = stride_0 + 256U;
        }
    }
    (*(&kernelContext_0)->keys_0)[t_0] = bk_0;
    (*(&kernelContext_0)->indices_0)[t_0] = bi_0;
    threadgroup_barrier(mem_flags::mem_threadgroup);
    stride_0 = 128U;
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
            uint _S7 = t_0 + stride_0;
            if(before_0((*(&kernelContext_0)->keys_0)[_S7], (*(&kernelContext_0)->indices_0)[_S7], (*(&kernelContext_0)->keys_0)[t_0], (*(&kernelContext_0)->indices_0)[t_0]))
            {
                (*(&kernelContext_0)->keys_0)[t_0] = (*(&kernelContext_0)->keys_0)[_S7];
                (*(&kernelContext_0)->indices_0)[t_0] = (*(&kernelContext_0)->indices_0)[_S7];
            }
        }
        threadgroup_barrier(mem_flags::mem_threadgroup);
        stride_0 = stride_0 >> 1U;
    }
    if(t_0 == 0U)
    {
        if((p_1->stage_0) == 0U)
        {
            uint _S8 = 2U * g_0;
            *((&kernelContext_0)->scratch_0+((&kernelContext_0)->p_0->partOff_0 + _S8)) = (*(&kernelContext_0)->keys_0)[int(0)];
            *((&kernelContext_0)->scratch_0+((&kernelContext_0)->p_0->partOff_0 + _S8 + 1U)) = (*(&kernelContext_0)->indices_0)[int(0)];
        }
        else
        {
            if(((&kernelContext_0)->p_0->mode_0) == 0U)
            {
                *((&kernelContext_0)->result_0+(&kernelContext_0)->p_0->outOff_0) = (*(&kernelContext_0)->indices_0)[int(0)];
            }
            else
            {
                uint m_0 = (*(&kernelContext_0)->keys_0)[int(0)];
                if(((*(&kernelContext_0)->keys_0)[int(0)]) == 0U)
                {
                    _S2 = true;
                }
                else
                {
                    _S2 = m_0 == 2139095040U;
                }
                if(_S2)
                {
                    *((&kernelContext_0)->result_0+(&kernelContext_0)->p_0->outOff_0) = m_0;
                    *((&kernelContext_0)->scratch_0+(&kernelContext_0)->p_0->stateOff_0) = 0U;
                }
                else
                {
                    *((&kernelContext_0)->scratch_0+(&kernelContext_0)->p_0->stateOff_0) = 2U;
                    *((&kernelContext_0)->scratch_0+((&kernelContext_0)->p_0->stateOff_0 + 1U)) = uint(int(m_0 >> 23U) - int(127));
                }
            }
        }
    }
    return;
}

