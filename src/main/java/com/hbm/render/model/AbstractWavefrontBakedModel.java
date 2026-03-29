package com.hbm.render.model;

import com.hbm.render.loader.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.vecmath.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Base implementation for baked models that render exported Wavefront meshes.
 */
@SideOnly(Side.CLIENT)
public abstract class AbstractWavefrontBakedModel extends AbstractBakedModel {

    protected final HFRWavefrontObject model;
    protected final VertexFormat format;
    protected final float baseScale;
    protected final float baseTx;
    protected final float baseTy;
    protected final float baseTz;

    protected AbstractWavefrontBakedModel(HFRWavefrontObject model, VertexFormat format, float baseScale, float tx,
                                          float ty, float tz, ItemCameraTransforms transforms) {
        super(false, true, false, transforms, ItemOverrideList.NONE);
        this.model = model;
        this.format = format;
        this.baseScale = baseScale;
        this.baseTx = tx;
        this.baseTy = ty;
        this.baseTz = tz;
    }

    protected List<BakedQuad> bakeSimpleQuads(Set<String> partNames, float roll, float pitch, float yaw,
                                              boolean applyShading, boolean centerToBlock, TextureAtlasSprite sprite) {
        return bakeSimpleQuads(partNames, roll, pitch, yaw, applyShading, centerToBlock, sprite, -1);
    }

    protected List<BakedQuad> bakeSimpleQuads(Set<String> partNames, float roll, float pitch, float yaw,
                                              boolean applyShading, boolean centerToBlock, TextureAtlasSprite sprite,
                                              int tintIndex) {
        return bakeSimpleQuads(partNames, roll, pitch, yaw, applyShading, centerToBlock, sprite, tintIndex, 0.0F, 0.0F,
                0.0F);
    }

    protected List<BakedQuad> bakeSimpleQuads(Set<String> partNames, float roll, float pitch, float yaw,
                                              boolean applyShading,
                                              boolean centerToBlock, TextureAtlasSprite sprite, int tintIndex,
                                              float extraTx, float extraTy, float extraTz) {
        List<FaceGeometry> geometries = buildGeometry(partNames, roll, pitch, yaw, applyShading, centerToBlock, extraTx,
                extraTy, extraTz);
        List<BakedQuad> quads = new ArrayList<>(geometries.size());
        for (FaceGeometry geometry : geometries) {
            quads.add(geometry.buildQuad(sprite, tintIndex));
        }
        return quads;
    }

    protected List<FaceGeometry> buildGeometry(Set<String> partNames, float roll, float pitch, float yaw,
                                               boolean applyShading, boolean centerToBlock) {
        return buildGeometry(partNames, roll, pitch, yaw, applyShading, centerToBlock, 0.0F, 0.0F, 0.0F);
    }

    protected List<FaceGeometry> buildGeometry(Set<String> partNames, float roll, float pitch, float yaw,
                                               boolean applyShading,
                                               boolean centerToBlock, float extraTx, float extraTy, float extraTz) {
        List<FaceGeometry> geometries = new ArrayList<>();

        for (GroupObject group : model.groupObjects) {
            if (partNames != null && !partNames.contains(group.name)) {
                continue;
            }

            for (Face face : group.faces) {
                Vertex normal = face.faceNormal;

                float[] n1 = GeometryBakeUtil.rotateX(normal.x, normal.y, normal.z, roll);
                float[] n2 = GeometryBakeUtil.rotateZ(n1[0], n1[1], n1[2], pitch);
                float[] n3 = GeometryBakeUtil.rotateY(n2[0], n2[1], n2[2], yaw);

                int color = applyShading ? GeometryBakeUtil.computeShade(n3[0], n3[1], n3[2]) : 255;

                int vertexCount = face.vertices.length;
                if (vertexCount < 3) {
                    continue;
                }

                int[] indices = vertexCount >= 4 ? new int[]{0, 1, 2, 3} : new int[]{0, 1, 2, 2};

                float[] px = new float[4];
                float[] py = new float[4];
                float[] pz = new float[4];
                float[] uu = new float[4];
                float[] vv = new float[4];
                Vector3f[] vertexNormals = new Vector3f[4];

                for (int v = 0; v < 4; v++) {
                    int idx = indices[v];
                    Vertex vertex = face.vertices[idx];

                    float[] p1 = GeometryBakeUtil.rotateX(vertex.x, vertex.y, vertex.z, roll);
                    float[] p2 = GeometryBakeUtil.rotateZ(p1[0], p1[1], p1[2], pitch);
                    float[] p3 = GeometryBakeUtil.rotateY(p2[0], p2[1], p2[2], yaw);

                    float x = p3[0];
                    float y = p3[1];
                    float z = p3[2];

                    if (centerToBlock) {
                        x += 0.5F;
                        y += 0.5F;
                        z += 0.5F;
                    }

                    x = x * baseScale + baseTx + extraTx;
                    y = y * baseScale + baseTy + extraTy;
                    z = z * baseScale + baseTz + extraTz;

                    TextureCoordinate tex = face.textureCoordinates[idx];
                    uu[v] = tex.u * 16.0f;
                    vv[v] = tex.v * 16.0f;

                    Vertex vertexNormal = face.vertexNormals != null && idx < face.vertexNormals.length ? face.vertexNormals[idx] : null;
                    if (vertexNormal != null) {
                        float[] vn1 = GeometryBakeUtil.rotateX(vertexNormal.x, vertexNormal.y, vertexNormal.z, roll);
                        float[] vn2 = GeometryBakeUtil.rotateZ(vn1[0], vn1[1], vn1[2], pitch);
                        float[] vn3 = GeometryBakeUtil.rotateY(vn2[0], vn2[1], vn2[2], yaw);
                        Vector3f vectorNormal = new Vector3f(vn3[0], vn3[1], vn3[2]);
                        if (vectorNormal.lengthSquared() > 0.0F) {
                            vectorNormal.normalize();
                        } else {
                            vectorNormal.set(n3[0], n3[1], n3[2]);
                        }
                        vertexNormals[v] = vectorNormal;
                    } else {
                        vertexNormals[v] = new Vector3f(n3[0], n3[1], n3[2]);
                    }

                    px[v] = x;
                    py[v] = y;
                    pz[v] = z;
                }

                EnumFacing facing = EnumFacing.getFacingFromVector(n3[0], n3[1], n3[2]);
                geometries.add(new FaceGeometry(facing, px, py, pz, uu, vv, vertexNormals, color));
            }
        }

        return geometries;
    }

    protected final class FaceGeometry {
        private final EnumFacing facing;
        private final float[] px;
        private final float[] py;
        private final float[] pz;
        private final float[] uu;
        private final float[] vv;
        private final Vector3f[] vertexNormals;
        private final int color;

        private FaceGeometry(EnumFacing facing, float[] px, float[] py, float[] pz, float[] uu, float[] vv,
                             Vector3f[] vertexNormals, int color) {
            this.facing = facing;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.uu = uu;
            this.vv = vv;
            this.vertexNormals = vertexNormals;
            this.color = color;
        }

        public BakedQuad buildQuad(TextureAtlasSprite sprite, int tintIndex) {
            return buildQuad(sprite, tintIndex, 1.0F, 1.0F);
        }

        public BakedQuad buildQuad(TextureAtlasSprite sprite, int tintIndex, float uScale, float vScale) {
            int[] vertexData = new int[format.getIntegerSize() * 4];
            float[] scratch = new float[4];
            for (int i = 0; i < 4; i++)
                GeometryBakeUtil.putVertex(format, vertexData, i, px[i], py[i], pz[i], uu[i] * uScale, vv[i] * vScale,
                        color, color, color, vertexNormals[i], sprite, scratch);
            return new BakedQuad(vertexData, tintIndex, facing, sprite, false, format);
        }

        public BakedQuad buildBackQuad(TextureAtlasSprite sprite, int tintIndex) {
            return buildBackQuad(sprite, tintIndex, 1.0F, 1.0F);
        }

        public BakedQuad buildBackQuad(TextureAtlasSprite sprite, int tintIndex, float uScale, float vScale) {
            int[] vertexData = new int[format.getIntegerSize() * 4];
            float[] scratch = new float[4];
            int[] order = new int[]{0, 3, 2, 1};
            int outIndex = 0;
            for (int index : order) {
                Vector3f normal = vertexNormals[index];
                Vector3f reversedNormal = new Vector3f(-normal.x, -normal.y, -normal.z);
                int vertexIndex = outIndex++;
                GeometryBakeUtil.putVertex(format, vertexData, vertexIndex, px[index], py[index], pz[index],
                        uu[index] * uScale, vv[index] * vScale, color, color, color, reversedNormal, sprite, scratch);
            }
            return new BakedQuad(vertexData, tintIndex, facing.getOpposite(), sprite, false, format);
        }
    }
}
