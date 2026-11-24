#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint nodeBlocks_0(uint blocks_0, uint depth_0, uint idx_0)
{
    uint c_0 = blocks_0;
    uint d_0 = 0U;
    for(;;)
    {
        if(d_0 < depth_0)
        {
        }
        else
        {
            break;
        }
        uint left_0 = (c_0 + 1U) / 2U;
        if(((idx_0 >> (depth_0 - 1U - d_0)) & 1U) != 0U)
        {
            c_0 = c_0 - left_0;
        }
        else
        {
            c_0 = left_0;
        }
        d_0 = d_0 + 1U;
    }
    return c_0;
}

struct CombineParams_0
{
    uint blocks_1;
    uint inDepth_0;
    uint outDepth_0;
    uint inOff_0;
    uint outOff_0;
    uint mode_0;
    uint stateOff_0;
    uint need_0;
    uint gridX_0;
};

struct KernelContext_0
{
    CombineParams_0 constant* p_0;
    uint device* scratch_0;
    uint device* result_0;
};

float walk_0(uint count_0, uint depth_1, uint idx_1, KernelContext_0 thread* kernelContext_0)
{
    thread array<uint, int(8)> cnt_0;
    cnt_0[int(0)] = count_0;
    thread array<uint, int(8)> state_0;
    state_0[int(0)] = 0U;
    thread array<uint, int(8)> dep_0;
    dep_0[int(0)] = depth_1;
    thread array<uint, int(8)> id_0;
    id_0[int(0)] = idx_1;
    thread array<float, int(8)> left_1;
    left_1[int(0)] = 0.0;
    float ret_0 = 0.0;
    int sp_0 = int(1);
    for(;;)
    {
        if(sp_0 > int(0))
        {
        }
        else
        {
            break;
        }
        int top_0 = sp_0 - int(1);
        bool _S1;
        if((dep_0[top_0]) == (kernelContext_0->p_0->inDepth_0))
        {
            _S1 = true;
        }
        else
        {
            _S1 = (cnt_0[top_0]) == 1U;
        }
        float ret_1;
        int sp_1;
        if(_S1)
        {
            ret_1 = (as_type<float>((*(kernelContext_0->scratch_0+(kernelContext_0->p_0->inOff_0 + ((id_0[top_0]) << (kernelContext_0->p_0->inDepth_0 - dep_0[top_0])))))));
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                cnt_0[sp_0] = (cnt_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                dep_0[sp_0] = dep_0[top_0] + 1U;
                id_0[sp_0] = id_0[top_0] * 2U;
                int _S2 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S2;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_1[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    cnt_0[sp_0] = cnt_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    dep_0[sp_0] = dep_0[top_0] + 1U;
                    id_0[sp_0] = id_0[top_0] * 2U + 1U;
                    int _S3 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S3;
                }
                else
                {
                    ret_1 = left_1[top_0] + ret_0;
                    sp_1 = top_0;
                }
            }
        }
        ret_0 = ret_1;
        sp_0 = sp_1;
    }
    return ret_0;
}

float pow2_0(int j_0)
{
    return (as_type<float>((uint(j_0 + int(127)) << 23U)));
}

float jcopySign_0(float magnitude_0, float sign_0)
{
    return (as_type<float>((((as_type<uint>((magnitude_0))) & 2147483647U) | ((as_type<uint>((sign_0))) & 2147483648U))));
}

float scaleBy_0(uint bits_0, int k_0)
{
    float x_0 = (as_type<float>((bits_0)));
    bool _S4;
    if((bits_0 & 2139095040U) == 0U)
    {
        _S4 = k_0 > int(0);
    }
    else
    {
        _S4 = false;
    }
    if(_S4)
    {
        float mag_0 = float(bits_0 & 8388607U);
        float r_0;
        if(k_0 >= int(23))
        {
            r_0 = mag_0 * pow2_0(k_0 - int(149));
        }
        else
        {
            r_0 = mag_0 * pow2_0(k_0) * pow2_0(int(-126)) * pow2_0(int(-23));
        }
        return jcopySign_0(r_0, x_0);
    }
    if(k_0 >= int(-126))
    {
        return x_0 * pow2_0(k_0);
    }
    return x_0 * pow2_0(int(-64)) * pow2_0(int(-63));
}

[[kernel]] void blasCombine(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], CombineParams_0 constant* p_1 [[buffer(2)]], uint device* scratch_1 [[buffer(0)]], uint device* result_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_1;
    (&kernelContext_1)->p_0 = p_1;
    (&kernelContext_1)->scratch_0 = scratch_1;
    (&kernelContext_1)->result_0 = result_1;
    uint idx_2 = (group_0.y * p_1->gridX_0 + group_0.x) * 32U + local_0.x;
    if(idx_2 >= (1U << (p_1->outDepth_0)))
    {
        return;
    }
    bool _S5;
    if(((&kernelContext_1)->p_0->need_0) != 0U)
    {
        _S5 = (*((&kernelContext_1)->scratch_0+(&kernelContext_1)->p_0->stateOff_0)) != ((&kernelContext_1)->p_0->need_0);
    }
    else
    {
        _S5 = false;
    }
    if(_S5)
    {
        return;
    }
    float v_0;
    if(((&kernelContext_1)->p_0->blocks_1) != 0U)
    {
        uint count_1 = nodeBlocks_0((&kernelContext_1)->p_0->blocks_1, p_1->outDepth_0, idx_2);
        if(count_1 == 0U)
        {
            return;
        }
        float _S6 = walk_0(count_1, p_1->outDepth_0, idx_2, &kernelContext_1);
        v_0 = _S6;
    }
    else
    {
        v_0 = 0.0;
    }
    if(((&kernelContext_1)->p_0->mode_0) == 0U)
    {
        *((&kernelContext_1)->scratch_0+((&kernelContext_1)->p_0->outOff_0 + idx_2)) = (as_type<uint>((v_0)));
    }
    else
    {
        if(((&kernelContext_1)->p_0->mode_0) == 1U)
        {
            *((&kernelContext_1)->result_0+(&kernelContext_1)->p_0->outOff_0) = (as_type<uint>((v_0)));
        }
        else
        {
            if(((&kernelContext_1)->p_0->mode_0) == 2U)
            {
                if(v_0 >= (as_type<float>((8388608U))))
                {
                    _S5 = v_0 < (as_type<float>((2139095040U)));
                }
                else
                {
                    _S5 = false;
                }
                uint flag_0;
                if(_S5)
                {
                    *((&kernelContext_1)->result_0+(&kernelContext_1)->p_0->outOff_0) = (as_type<uint>((sqrt(v_0))));
                    flag_0 = 0U;
                }
                else
                {
                    if(v_0 != v_0)
                    {
                        *((&kernelContext_1)->result_0+(&kernelContext_1)->p_0->outOff_0) = (as_type<uint>((v_0)));
                        flag_0 = 0U;
                    }
                    else
                    {
                        flag_0 = 1U;
                    }
                }
                *((&kernelContext_1)->scratch_0+(&kernelContext_1)->p_0->stateOff_0) = flag_0;
            }
            else
            {
                *((&kernelContext_1)->result_0+(&kernelContext_1)->p_0->outOff_0) = (as_type<uint>((scaleBy_0((as_type<uint>((sqrt(v_0)))), int(*((&kernelContext_1)->scratch_0+((&kernelContext_1)->p_0->stateOff_0 + 1U)))))));
            }
        }
    }
    return;
}

