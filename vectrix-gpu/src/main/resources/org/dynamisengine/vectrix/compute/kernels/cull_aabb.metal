#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
int classifyAabb_0(const array<float4, int(6)> thread* planes_0, float minX_0, float minY_0, float minZ_0, float maxX_0, float maxY_0, float maxZ_0)
{
    bool inside_0 = true;
    int i_0 = int(0);
    for(;;)
    {
        if(i_0 < int(6))
        {
        }
        else
        {
            break;
        }
        float px_0 = (*planes_0)[i_0].x;
        float py_0 = (*planes_0)[i_0].y;
        float pz_0 = (*planes_0)[i_0].z;
        float pw_0 = (*planes_0)[i_0].w;
        bool _S1 = px_0 < 0.0;
        float _S2;
        if(_S1)
        {
            _S2 = minX_0;
        }
        else
        {
            _S2 = maxX_0;
        }
        float _S3 = px_0 * _S2;
        bool _S4 = py_0 < 0.0;
        float _S5;
        if(_S4)
        {
            _S5 = minY_0;
        }
        else
        {
            _S5 = maxY_0;
        }
        float _S6 = _S3 + py_0 * _S5;
        bool _S7 = pz_0 < 0.0;
        float _S8;
        if(_S7)
        {
            _S8 = minZ_0;
        }
        else
        {
            _S8 = maxZ_0;
        }
        if((_S6 + pz_0 * _S8 + pw_0) < 0.0)
        {
            return int(-3);
        }
        float _S9;
        if(_S1)
        {
            _S9 = maxX_0;
        }
        else
        {
            _S9 = minX_0;
        }
        float _S10 = px_0 * _S9;
        float _S11;
        if(_S4)
        {
            _S11 = maxY_0;
        }
        else
        {
            _S11 = minY_0;
        }
        float _S12 = _S10 + py_0 * _S11;
        float _S13;
        if(_S7)
        {
            _S13 = maxZ_0;
        }
        else
        {
            _S13 = minZ_0;
        }
        float dMin_0 = _S12 + pz_0 * _S13 + pw_0;
        if(inside_0)
        {
            inside_0 = dMin_0 >= 0.0;
        }
        else
        {
            inside_0 = false;
        }
        i_0 = i_0 + int(1);
    }
    if(inside_0)
    {
        i_0 = int(-2);
    }
    else
    {
        i_0 = int(-1);
    }
    return i_0;
}

struct CullParams_0
{
    float x0_0;
    float y0_0;
    float z0_0;
    float w0_0;
    float x1_0;
    float y1_0;
    float z1_0;
    float w1_0;
    float x2_0;
    float y2_0;
    float z2_0;
    float w2_0;
    float x3_0;
    float y3_0;
    float z3_0;
    float w3_0;
    float x4_0;
    float y4_0;
    float z4_0;
    float w4_0;
    float x5_0;
    float y5_0;
    float z5_0;
    float w5_0;
    uint count_0;
    uint boundsOff_0;
    uint boundsStride_0;
    uint resultOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    CullParams_0 constant* p_0;
    int device* result_0;
    float device* bounds_0;
};

[[kernel]] void cullAabb(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], CullParams_0 constant* p_1 [[buffer(2)]], int device* result_1 [[buffer(1)]], float device* bounds_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->result_0 = result_1;
    (&kernelContext_0)->bounds_0 = bounds_1;
    uint i_1 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_1 >= (p_1->count_0))
    {
        return;
    }
    thread array<float4, int(6)> planes_1;
    planes_1[int(0)] = float4((&kernelContext_0)->p_0->x0_0, (&kernelContext_0)->p_0->y0_0, (&kernelContext_0)->p_0->z0_0, (&kernelContext_0)->p_0->w0_0);
    planes_1[int(1)] = float4((&kernelContext_0)->p_0->x1_0, (&kernelContext_0)->p_0->y1_0, (&kernelContext_0)->p_0->z1_0, (&kernelContext_0)->p_0->w1_0);
    planes_1[int(2)] = float4((&kernelContext_0)->p_0->x2_0, (&kernelContext_0)->p_0->y2_0, (&kernelContext_0)->p_0->z2_0, (&kernelContext_0)->p_0->w2_0);
    planes_1[int(3)] = float4((&kernelContext_0)->p_0->x3_0, (&kernelContext_0)->p_0->y3_0, (&kernelContext_0)->p_0->z3_0, (&kernelContext_0)->p_0->w3_0);
    planes_1[int(4)] = float4((&kernelContext_0)->p_0->x4_0, (&kernelContext_0)->p_0->y4_0, (&kernelContext_0)->p_0->z4_0, (&kernelContext_0)->p_0->w4_0);
    planes_1[int(5)] = float4((&kernelContext_0)->p_0->x5_0, (&kernelContext_0)->p_0->y5_0, (&kernelContext_0)->p_0->z5_0, (&kernelContext_0)->p_0->w5_0);
    uint b_0 = (&kernelContext_0)->p_0->boundsOff_0 + i_1;
    int device* _S14 = (&kernelContext_0)->result_0+((&kernelContext_0)->p_0->resultOff_0 + i_1);
    float device* _S15 = (&kernelContext_0)->bounds_0+b_0;
    float device* _S16 = (&kernelContext_0)->bounds_0+(b_0 + (&kernelContext_0)->p_0->boundsStride_0);
    float device* _S17 = (&kernelContext_0)->bounds_0+(b_0 + 2U * (&kernelContext_0)->p_0->boundsStride_0);
    float device* _S18 = (&kernelContext_0)->bounds_0+(b_0 + 3U * (&kernelContext_0)->p_0->boundsStride_0);
    float device* _S19 = (&kernelContext_0)->bounds_0+(b_0 + 4U * (&kernelContext_0)->p_0->boundsStride_0);
    float device* _S20 = (&kernelContext_0)->bounds_0+(b_0 + 5U * (&kernelContext_0)->p_0->boundsStride_0);
    thread array<float4, int(6)> _S21 = planes_1;
    int _S22 = classifyAabb_0(&_S21, *_S15, *_S16, *_S17, *_S18, *_S19, *_S20);
    *_S14 = _S22;
    return;
}

