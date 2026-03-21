package com.hbm.render.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.vecmath.Vector3f;

public final class GeometryBakeUtil {

    private GeometryBakeUtil() {
    }

    public static int computeShade(float nx, float ny, float nz) {
        float brightness = (ny + 0.7F) * 0.9F - Math.abs(nx) * 0.1F + Math.abs(nz) * 0.1F;
        if (brightness < 0.45F) brightness = 0.45F;
        if (brightness > 1.0F) brightness = 1.0F;
        return clampColor((int) (brightness * 255.0F));
    }

    public static int clampColor(int value) {
        return Math.min(Math.max(value, 0), 255);
    }

    @Contract("_, _, _, _ -> new")
    public static double @NotNull [] rotateX(double x, double y, double z, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double ny = y * cos + z * sin;
        double nz = z * cos - y * sin;
        return new double[]{x, ny, nz};
    }

    @Contract("_, _, _, _ -> new")
    public static double @NotNull [] rotateY(double x, double y, double z, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nx = x * cos + z * sin;
        double nz = -x * sin + z * cos;
        return new double[]{nx, y, nz};
    }

    @Contract("_, _, _, _ -> new")
    public static double @NotNull [] rotateZ(double x, double y, double z, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nx = x * cos + y * sin;
        double ny = y * cos - x * sin;
        return new double[]{nx, ny, z};
    }

    public static void putVertex(VertexFormat format, int[] vertexData, int vertexIndex, float x, float y, float z, float u16, float v16,
                                 int cr, int cg, int cb, Vector3f normal, TextureAtlasSprite sprite, float[] scratch) {
        for (int elementIndex = 0; elementIndex < format.getElementCount(); elementIndex++) {
            VertexFormatElement element = format.getElement(elementIndex);
            switch (element.getUsage()) {
                case POSITION -> {
                    scratch[0] = x;
                    scratch[1] = y;
                    scratch[2] = z;
                    LightUtil.pack(scratch, vertexData, format, vertexIndex, elementIndex);
                }
                case COLOR -> {
                    scratch[0] = cr / 255.0F;
                    scratch[1] = cg / 255.0F;
                    scratch[2] = cb / 255.0F;
                    scratch[3] = 1.0F;
                    LightUtil.pack(scratch, vertexData, format, vertexIndex, elementIndex);
                }
                case UV -> {
                    if (element.getIndex() == 0) {
                        scratch[0] = sprite.getInterpolatedU(u16);
                        scratch[1] = sprite.getInterpolatedV(v16);
                    } else {
                        scratch[0] = 0.0F;
                        scratch[1] = 0.0F;
                    }
                    LightUtil.pack(scratch, vertexData, format, vertexIndex, elementIndex);
                }
                case NORMAL -> {
                    scratch[0] = normal.x;
                    scratch[1] = normal.y;
                    scratch[2] = normal.z;
                    LightUtil.pack(scratch, vertexData, format, vertexIndex, elementIndex);
                }
                case PADDING -> {
                    scratch[0] = 0.0F;
                    LightUtil.pack(scratch, vertexData, format, vertexIndex, elementIndex);
                }
                default -> {
                }
            }
        }
    }
}
