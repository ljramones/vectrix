#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct UploadParams_0
{
    uint count_0;
    uint transformCount_0;
    uint tOff_0;
    uint orderOff_0;
    uint outOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    UploadParams_0 constant* p_0;
    int device* order_0;
    uint device* outUpload_0;
    uint device* transforms_0;
};

[[kernel]] void uploadAffine(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], UploadParams_0 constant* p_1 [[buffer(3)]], int device* order_1 [[buffer(1)]], uint device* outUpload_1 [[buffer(2)]], uint device* transforms_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->order_0 = order_1;
    (&kernelContext_0)->outUpload_0 = outUpload_1;
    (&kernelContext_0)->transforms_0 = transforms_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    uint idx_0 = uint(*((&kernelContext_0)->order_0+((&kernelContext_0)->p_0->orderOff_0 + i_0)));
    if(idx_0 >= ((&kernelContext_0)->p_0->transformCount_0))
    {
        return;
    }
    uint _S1 = (&kernelContext_0)->p_0->tOff_0 + 12U * idx_0;
    uint _S2 = (&kernelContext_0)->p_0->outOff_0 + 12U * i_0;
    *((&kernelContext_0)->outUpload_0+_S2) = *((&kernelContext_0)->transforms_0+_S1);
    *((&kernelContext_0)->outUpload_0+(_S2 + 1U)) = *((&kernelContext_0)->transforms_0+(_S1 + 1U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 2U)) = *((&kernelContext_0)->transforms_0+(_S1 + 2U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 3U)) = *((&kernelContext_0)->transforms_0+(_S1 + 3U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 4U)) = *((&kernelContext_0)->transforms_0+(_S1 + 4U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 5U)) = *((&kernelContext_0)->transforms_0+(_S1 + 5U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 6U)) = *((&kernelContext_0)->transforms_0+(_S1 + 6U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 7U)) = *((&kernelContext_0)->transforms_0+(_S1 + 7U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 8U)) = *((&kernelContext_0)->transforms_0+(_S1 + 8U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 9U)) = *((&kernelContext_0)->transforms_0+(_S1 + 9U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 10U)) = *((&kernelContext_0)->transforms_0+(_S1 + 10U));
    *((&kernelContext_0)->outUpload_0+(_S2 + 11U)) = *((&kernelContext_0)->transforms_0+(_S1 + 11U));
    return;
}

