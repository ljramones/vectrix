// CullingKernels.frustumCullAabbBatch: result[resultOff + i] = classifyAabb(planes, box i) for i in [0, count), with
// FrustumIntersection's INSIDE (-2), INTERSECT (-1) and OUTSIDE (-3). Java twin:
// org.dynamisengine.vectrix.geometry.CullingKernels (the scalar kernel; the SIMD one classifies identically).
//
// The six planes are in the parameter block in FrustumPlanes.getVec4Array order (x, y, z, w of PLANE_NX to
// PLANE_PZ). The boxes are an AABBSoA buffer: planes minX, minY, minZ, maxX, maxY, maxZ (engine.slang). One thread
// per box.
import common;
import engine;

struct CullParams
{
    float x0;
    float y0;
    float z0;
    float w0;
    float x1;
    float y1;
    float z1;
    float w1;
    float x2;
    float y2;
    float z2;
    float w2;
    float x3;
    float y3;
    float z3;
    float w3;
    float x4;
    float y4;
    float z4;
    float w4;
    float x5;
    float y5;
    float z5;
    float w5;
    uint count;
    uint boundsOff;
    uint boundsStride;
    uint resultOff;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> bounds;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> result;
[[vk::push_constant]] ConstantBuffer<CullParams> p;

static const int INTERSECT = -1;
static const int INSIDE = -2;
static const int OUTSIDE = -3;

// CullingKernels.classifyAabb: OUTSIDE as soon as the box lies behind a plane, INSIDE if it is in front of all six.
int classifyAabb(float4 planes[6], float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
{
    bool inside = true;
    for (int i = 0; i < 6; i++)
    {
        float px = planes[i].x;
        float py = planes[i].y;
        float pz = planes[i].z;
        float pw = planes[i].w;
        float dMax = px * (px < 0.0f ? minX : maxX)
                + py * (py < 0.0f ? minY : maxY)
                + pz * (pz < 0.0f ? minZ : maxZ)
                + pw;
        if (dMax < 0.0f)
        {
            return OUTSIDE;
        }
        float dMin = px * (px < 0.0f ? maxX : minX)
                + py * (py < 0.0f ? maxY : minY)
                + pz * (pz < 0.0f ? maxZ : minZ)
                + pw;
        inside = inside && dMin >= 0.0f;
    }
    return inside ? INSIDE : INTERSECT;
}

[shader("compute")]
[numthreads(64, 1, 1)]
void cullAabb(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    float4 planes[6];
    planes[0] = float4(p.x0, p.y0, p.z0, p.w0);
    planes[1] = float4(p.x1, p.y1, p.z1, p.w1);
    planes[2] = float4(p.x2, p.y2, p.z2, p.w2);
    planes[3] = float4(p.x3, p.y3, p.z3, p.w3);
    planes[4] = float4(p.x4, p.y4, p.z4, p.w4);
    planes[5] = float4(p.x5, p.y5, p.z5, p.w5);
    uint b = p.boundsOff + i;
    uint s = p.boundsStride;
    result[p.resultOff + i] = classifyAabb(planes, bounds[b], bounds[b + s], bounds[b + 2 * s], bounds[b + 3 * s],
            bounds[b + 4 * s], bounds[b + 5 * s]);
}
