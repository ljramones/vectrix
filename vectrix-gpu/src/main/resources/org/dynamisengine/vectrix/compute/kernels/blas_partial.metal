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

uint nodeStart_0(uint blocks_1, uint depth_1, uint idx_1)
{
    uint d_1 = 0U;
    uint c_1 = blocks_1;
    uint start_0 = 0U;
    for(;;)
    {
        if(d_1 < depth_1)
        {
        }
        else
        {
            break;
        }
        uint left_1 = (c_1 + 1U) / 2U;
        if(((idx_1 >> (depth_1 - 1U - d_1)) & 1U) != 0U)
        {
            uint start_1 = start_0 + left_1;
            c_1 = c_1 - left_1;
            start_0 = start_1;
        }
        else
        {
            c_1 = left_1;
        }
        d_1 = d_1 + 1U;
    }
    return start_0;
}

float jabs_0(float x_0)
{
    return (as_type<float>(((as_type<uint>((x_0))) & 2147483647U)));
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
    float x_1 = (as_type<float>((bits_0)));
    bool _S1;
    if((bits_0 & 2139095040U) == 0U)
    {
        _S1 = k_0 > int(0);
    }
    else
    {
        _S1 = false;
    }
    if(_S1)
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
        return jcopySign_0(r_0, x_1);
    }
    if(k_0 >= int(-126))
    {
        return x_1 * pow2_0(k_0);
    }
    return x_1 * pow2_0(int(-64)) * pow2_0(int(-63));
}

float blasStep_0(uint op_0, float a_0, uint xb_0, uint yb_0, int k_1)
{
    float xv_0 = (as_type<float>((xb_0)));
    if(op_0 == 0U)
    {
        return fma(xv_0, (as_type<float>((yb_0))), a_0);
    }
    if(op_0 == 1U)
    {
        return a_0 + xv_0;
    }
    if(op_0 == 2U)
    {
        return a_0 + jabs_0(xv_0);
    }
    if(op_0 == 3U)
    {
        return fma(xv_0, xv_0, a_0);
    }
    float xs_0 = scaleBy_0(xb_0, k_1);
    return fma(xs_0, xs_0, a_0);
}

float combineLanes_0(float a0_0, float a1_0, float a2_0, float a3_0, float a4_0, float a5_0, float a6_0, float a7_0)
{
    return a0_0 + a4_0 + (a2_0 + a6_0) + (a1_0 + a5_0 + (a3_0 + a7_0));
}

struct LeafBlocks_0
{
    uint next_0;
};

struct PartialParams_0
{
    uint op_1;
    uint n_0;
    uint xOff_0;
    uint yOff_0;
    uint blocks_2;
    uint depth_2;
    uint leafOff_0;
    uint stateOff_0;
    uint need_0;
    uint gridX_0;
};

struct KernelContext_0
{
    PartialParams_0 constant* p_0;
    uint device* scratch_0;
    uint device* y_0;
    uint device* x_2;
    array<float, int(256)> threadgroup* laneSums_0;
    array<float, int(32)> threadgroup* blockSums_0;
};

float LeafBlocks_nextBlock_0(LeafBlocks_0 thread* this_0, KernelContext_0 thread* kernelContext_0)
{
    float v_0 = (*kernelContext_0->blockSums_0)[this_0->next_0];
    this_0->next_0 = this_0->next_0 + 1U;
    return v_0;
}

float pairwiseBlocks_0(LeafBlocks_0 thread* source_0, uint blocks_3, KernelContext_0 thread* kernelContext_1)
{
    thread array<uint, int(26)> count_0;
    count_0[int(0)] = blocks_3;
    thread array<uint, int(26)> state_0;
    state_0[int(0)] = 0U;
    thread array<float, int(26)> left_2;
    left_2[int(0)] = 0.0;
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
        float ret_1;
        int sp_1;
        if((count_0[top_0]) == 1U)
        {
            float _S2 = LeafBlocks_nextBlock_0(source_0, kernelContext_1);
            ret_1 = _S2;
            sp_1 = top_0;
        }
        else
        {
            if((state_0[top_0]) == 0U)
            {
                state_0[top_0] = 1U;
                count_0[sp_0] = (count_0[top_0] + 1U) / 2U;
                state_0[sp_0] = 0U;
                int _S3 = sp_0 + int(1);
                ret_1 = ret_0;
                sp_1 = _S3;
            }
            else
            {
                if((state_0[top_0]) == 1U)
                {
                    left_2[top_0] = ret_0;
                    state_0[top_0] = 2U;
                    count_0[sp_0] = count_0[top_0] / 2U;
                    state_0[sp_0] = 0U;
                    int _S4 = sp_0 + int(1);
                    ret_1 = ret_0;
                    sp_1 = _S4;
                }
                else
                {
                    ret_1 = left_2[top_0] + ret_0;
                    sp_1 = top_0;
                }
            }
        }
        ret_0 = ret_1;
        sp_0 = sp_1;
    }
    return ret_0;
}

[[kernel]] void blasPartial(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], PartialParams_0 constant* p_1 [[buffer(3)]], uint device* scratch_1 [[buffer(2)]], uint device* y_1 [[buffer(1)]], uint device* x_3 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_2;
    (&kernelContext_2)->p_0 = p_1;
    (&kernelContext_2)->scratch_0 = scratch_1;
    (&kernelContext_2)->y_0 = y_1;
    (&kernelContext_2)->x_2 = x_3;
    threadgroup array<float, int(256)> laneSums_1;
    (&kernelContext_2)->laneSums_0 = &laneSums_1;
    threadgroup array<float, int(32)> blockSums_1;
    (&kernelContext_2)->blockSums_0 = &blockSums_1;
    uint g_0 = group_0.y * p_1->gridX_0 + group_0.x;
    if(g_0 >= (1U << (p_1->depth_2)))
    {
        return;
    }
    uint count_1 = nodeBlocks_0((&kernelContext_2)->p_0->blocks_2, p_1->depth_2, g_0);
    if(count_1 == 0U)
    {
        return;
    }
    bool _S5;
    if(((&kernelContext_2)->p_0->need_0) != 0U)
    {
        _S5 = (*((&kernelContext_2)->scratch_0+(&kernelContext_2)->p_0->stateOff_0)) != ((&kernelContext_2)->p_0->need_0);
    }
    else
    {
        _S5 = false;
    }
    if(_S5)
    {
        return;
    }
    int k_2;
    if(((&kernelContext_2)->p_0->op_1) == 4U)
    {
        k_2 = - int(*((&kernelContext_2)->scratch_0+((&kernelContext_2)->p_0->stateOff_0 + 1U)));
    }
    else
    {
        k_2 = int(0);
    }
    uint start_2 = nodeStart_0((&kernelContext_2)->p_0->blocks_2, p_1->depth_2, g_0);
    uint t_0 = local_0.x;
    uint b_0 = t_0 / 8U;
    uint lane_0 = t_0 % 8U;
    bool _S6 = b_0 < count_1;
    float acc_0;
    if(_S6)
    {
        uint base_0 = (start_2 + b_0) * 256U;
        uint _S7 = min(256U, (&kernelContext_2)->p_0->n_0 - base_0);
        acc_0 = 0.0;
        uint j_1 = lane_0;
        for(;;)
        {
            if(j_1 < _S7)
            {
            }
            else
            {
                break;
            }
            uint yb_1;
            if(((&kernelContext_2)->p_0->op_1) == 0U)
            {
                yb_1 = *((&kernelContext_2)->y_0+((&kernelContext_2)->p_0->yOff_0 + base_0 + j_1));
            }
            else
            {
                yb_1 = 0U;
            }
            float _S8 = blasStep_0((&kernelContext_2)->p_0->op_1, acc_0, *((&kernelContext_2)->x_2+((&kernelContext_2)->p_0->xOff_0 + base_0 + j_1)), yb_1, k_2);
            uint j_2 = j_1 + 8U;
            acc_0 = _S8;
            j_1 = j_2;
        }
    }
    else
    {
        acc_0 = 0.0;
    }
    (*(&kernelContext_2)->laneSums_0)[t_0] = acc_0;
    threadgroup_barrier(mem_flags::mem_threadgroup);
    if(lane_0 == 0U)
    {
        _S5 = _S6;
    }
    else
    {
        _S5 = false;
    }
    if(_S5)
    {
        (*(&kernelContext_2)->blockSums_0)[b_0] = combineLanes_0((*(&kernelContext_2)->laneSums_0)[t_0], (*(&kernelContext_2)->laneSums_0)[t_0 + 1U], (*(&kernelContext_2)->laneSums_0)[t_0 + 2U], (*(&kernelContext_2)->laneSums_0)[t_0 + 3U], (*(&kernelContext_2)->laneSums_0)[t_0 + 4U], (*(&kernelContext_2)->laneSums_0)[t_0 + 5U], (*(&kernelContext_2)->laneSums_0)[t_0 + 6U], (*(&kernelContext_2)->laneSums_0)[t_0 + 7U]);
    }
    threadgroup_barrier(mem_flags::mem_threadgroup);
    if(t_0 == 0U)
    {
        thread LeafBlocks_0 source_1;
        (&source_1)->next_0 = 0U;
        uint device* _S9 = (&kernelContext_2)->scratch_0+((&kernelContext_2)->p_0->leafOff_0 + g_0);
        float _S10 = pairwiseBlocks_0(&source_1, count_1, &kernelContext_2);
        *_S9 = (as_type<uint>((_S10)));
    }
    return;
}

