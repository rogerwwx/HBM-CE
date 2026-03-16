package com.hbm.render.util;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.ByteOrder;
import java.nio.IntBuffer;

@SideOnly(Side.CLIENT)
public class NTMBufferBuilder extends BufferBuilder {

    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    public static final int POSITION_COLOR_INTS_PER_VERTEX = 4;
    public static final int POSITION_COLOR_QUAD_INTS = POSITION_COLOR_INTS_PER_VERTEX * 4;

    private NTMFastVertexFormat fastFormat = NTMFastVertexFormat.GENERIC;
    private final PositionSink positionSink = new PositionSink();
    private final PositionColorSink positionColorSink = new PositionColorSink();
    private final PositionTexSink positionTexSink = new PositionTexSink();
    private final PositionTexColorSink positionTexColorSink = new PositionTexColorSink();
    private final PositionNormalSink positionNormalSink = new PositionNormalSink();
    private final ParticlePositionTexColorLmapSink particleSink = new ParticlePositionTexColorLmapSink();

    public NTMBufferBuilder(int bufferSizeInInts) {
        super(bufferSizeInInts);
    }

    public void beginFast(int drawMode, VertexFormat format, int expectedVertices) {
        super.begin(drawMode, format);
        this.rawIntBuffer.clear();
        this.fastFormat = NTMFastVertexFormat.from(format);
        reserveVertices(expectedVertices);
    }

    @Override
    public void reset() {
        super.reset();
        this.fastFormat = NTMFastVertexFormat.GENERIC;
    }

    public NTMFastVertexFormat getFastFormat() {
        return fastFormat;
    }

    public PositionSink position() {
        return positionSink;
    }

    public PositionColorSink positionColor() {
        return positionColorSink;
    }

    public PositionTexSink positionTex() {
        return positionTexSink;
    }

    public PositionTexColorSink positionTexColor() {
        return positionTexColorSink;
    }

    public PositionNormalSink positionNormal() {
        return positionNormalSink;
    }

    public ParticlePositionTexColorLmapSink particlePositionTexColorLmap() {
        return particleSink;
    }

    public void reserveVertices(int expectedVertices) {
        if (expectedVertices > 0) {
            reserveAdditionalBytes(expectedVertices * this.vertexFormat.getSize());
        }
    }

    public void reserveAdditionalBytes(int additionalBytes) {
        if (additionalBytes > 0) {
            growBuffer(additionalBytes);
        }
    }

    public void reservePositionColorQuads(int quadCount) {
        ensureDrawing(NTMFastVertexFormat.POSITION_COLOR);
        if (quadCount > 0) {
            growBuffer(quadCount * POSITION_COLOR_QUAD_INTS * Integer.BYTES);
        }
    }

    public void appendPosition(double x, double y, double z) {
        ensureDrawing(NTMFastVertexFormat.POSITION);
        if (!hasRemainingInts(3)) {
            growBuffer(3 * Integer.BYTES);
        }
        appendPositionUnchecked(x, y, z);
    }

    public void appendPositionColor(double x, double y, double z, int packedColor) {
        ensureDrawing(NTMFastVertexFormat.POSITION_COLOR);
        if (!hasRemainingInts(POSITION_COLOR_INTS_PER_VERTEX)) {
            growBuffer(POSITION_COLOR_INTS_PER_VERTEX * Integer.BYTES);
        }
        appendPositionColorUnchecked(x, y, z, packedColor);
    }

    public void appendPositionColorQuad(double x0, double y0, double z0,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        double x3, double y3, double z3,
                                        int packedColor) {
        ensureDrawing(NTMFastVertexFormat.POSITION_COLOR);
        if (!hasRemainingInts(POSITION_COLOR_QUAD_INTS)) {
            growBuffer(POSITION_COLOR_QUAD_INTS * Integer.BYTES);
        }
        appendPositionColorQuadUnchecked(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, packedColor);
    }

    public void appendPositionTex(double x, double y, double z, double u, double v) {
        ensureDrawing(NTMFastVertexFormat.POSITION_TEX);
        if (!hasRemainingInts(5)) {
            growBuffer(5 * Integer.BYTES);
        }
        appendPositionTexUnchecked(x, y, z, u, v);
    }

    public void appendPositionTexColor(double x, double y, double z, double u, double v, int packedColor) {
        ensureDrawing(NTMFastVertexFormat.POSITION_TEX_COLOR);
        if (!hasRemainingInts(6)) {
            growBuffer(6 * Integer.BYTES);
        }
        appendPositionTexColorUnchecked(x, y, z, u, v, packedColor);
    }

    public void appendPositionNormal(double x, double y, double z, int packedNormal) {
        ensureDrawing(NTMFastVertexFormat.POSITION_NORMAL);
        if (!hasRemainingInts(4)) {
            growBuffer(4 * Integer.BYTES);
        }
        appendPositionNormalUnchecked(x, y, z, packedNormal);
    }

    public void appendParticlePositionTexColorLmap(double x, double y, double z, double u, double v, int packedColor,
                                                   int packedLightmap) {
        ensureDrawing(NTMFastVertexFormat.PARTICLE_POSITION_TEX_COLOR_LMAP);
        if (!hasRemainingInts(7)) {
            growBuffer(7 * Integer.BYTES);
        }
        appendParticlePositionTexColorLmapUnchecked(x, y, z, u, v, packedColor, packedLightmap);
    }

    public void appendPositionUnchecked(double x, double y, double z) {
        int base = this.vertexCount * 3;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        this.vertexCount++;
    }

    public void appendPositionQuadUnchecked(double x0, double y0, double z0,
                                            double x1, double y1, double z1,
                                            double x2, double y2, double z2,
                                            double x3, double y3, double z3) {
        appendPositionUnchecked(x0, y0, z0);
        appendPositionUnchecked(x1, y1, z1);
        appendPositionUnchecked(x2, y2, z2);
        appendPositionUnchecked(x3, y3, z3);
    }

    public void appendPositionColorUnchecked(double x, double y, double z, int packedColor) {
        int base = this.vertexCount * POSITION_COLOR_INTS_PER_VERTEX;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        target.put(base + 3, packedColor);
        this.vertexCount++;
    }

    public void appendPositionColorQuadUnchecked(double x0, double y0, double z0,
                                                 double x1, double y1, double z1,
                                                 double x2, double y2, double z2,
                                                 double x3, double y3, double z3,
                                                 int packedColor) {
        int base = this.vertexCount * POSITION_COLOR_INTS_PER_VERTEX;
        IntBuffer target = this.rawIntBuffer;
        writePositionColorVertex(target, base, applyXOffset(x0), applyYOffset(y0), applyZOffset(z0), packedColor);
        writePositionColorVertex(target, base + POSITION_COLOR_INTS_PER_VERTEX, applyXOffset(x1), applyYOffset(y1), applyZOffset(z1), packedColor);
        writePositionColorVertex(target, base + POSITION_COLOR_INTS_PER_VERTEX * 2, applyXOffset(x2), applyYOffset(y2), applyZOffset(z2), packedColor);
        writePositionColorVertex(target, base + POSITION_COLOR_INTS_PER_VERTEX * 3, applyXOffset(x3), applyYOffset(y3), applyZOffset(z3), packedColor);
        this.vertexCount += 4;
    }

    public void appendPositionTexUnchecked(double x, double y, double z, double u, double v) {
        int base = this.vertexCount * 5;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        target.put(base + 3, Float.floatToRawIntBits((float) u));
        target.put(base + 4, Float.floatToRawIntBits((float) v));
        this.vertexCount++;
    }

    public void appendPositionTexQuadUnchecked(double x0, double y0, double z0, double u0, double v0,
                                               double x1, double y1, double z1, double u1, double v1,
                                               double x2, double y2, double z2, double u2, double v2,
                                               double x3, double y3, double z3, double u3, double v3) {
        appendPositionTexUnchecked(x0, y0, z0, u0, v0);
        appendPositionTexUnchecked(x1, y1, z1, u1, v1);
        appendPositionTexUnchecked(x2, y2, z2, u2, v2);
        appendPositionTexUnchecked(x3, y3, z3, u3, v3);
    }

    public void appendPositionTexColorUnchecked(double x, double y, double z, double u, double v, int packedColor) {
        int base = this.vertexCount * 6;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        target.put(base + 3, Float.floatToRawIntBits((float) u));
        target.put(base + 4, Float.floatToRawIntBits((float) v));
        target.put(base + 5, packedColor);
        this.vertexCount++;
    }

    public void appendPositionTexColorQuadUnchecked(double x0, double y0, double z0, double u0, double v0, int c0,
                                                    double x1, double y1, double z1, double u1, double v1, int c1,
                                                    double x2, double y2, double z2, double u2, double v2, int c2,
                                                    double x3, double y3, double z3, double u3, double v3, int c3) {
        appendPositionTexColorUnchecked(x0, y0, z0, u0, v0, c0);
        appendPositionTexColorUnchecked(x1, y1, z1, u1, v1, c1);
        appendPositionTexColorUnchecked(x2, y2, z2, u2, v2, c2);
        appendPositionTexColorUnchecked(x3, y3, z3, u3, v3, c3);
    }

    public void appendPositionNormalUnchecked(double x, double y, double z, int packedNormal) {
        int base = this.vertexCount * 4;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        target.put(base + 3, packedNormal);
        this.vertexCount++;
    }

    public void appendParticlePositionTexColorLmapUnchecked(double x, double y, double z, double u, double v,
                                                            int packedColor, int packedLightmap) {
        int base = this.vertexCount * 7;
        IntBuffer target = this.rawIntBuffer;
        target.put(base, Float.floatToRawIntBits(applyXOffset(x)));
        target.put(base + 1, Float.floatToRawIntBits(applyYOffset(y)));
        target.put(base + 2, Float.floatToRawIntBits(applyZOffset(z)));
        target.put(base + 3, Float.floatToRawIntBits((float) u));
        target.put(base + 4, Float.floatToRawIntBits((float) v));
        target.put(base + 5, packedColor);
        target.put(base + 6, packedLightmap);
        this.vertexCount++;
    }

    private boolean hasRemainingInts(int additionalInts) {
        return this.vertexCount * this.vertexFormat.getIntegerSize() + additionalInts <= this.rawIntBuffer.capacity();
    }

    private void ensureDrawing(NTMFastVertexFormat requiredFormat) {
        if (!this.isDrawing) {
            throw new IllegalStateException("Not building!");
        }
        if (this.fastFormat != requiredFormat) {
            throw new IllegalStateException("Expected " + requiredFormat + ", got " + this.fastFormat);
        }
    }

    private float applyXOffset(double x) {
        return (float) (x + this.xOffset);
    }

    private float applyYOffset(double y) {
        return (float) (y + this.yOffset);
    }

    private float applyZOffset(double z) {
        return (float) (z + this.zOffset);
    }

    private static void writePositionColorVertex(IntBuffer target, int offset, double x, double y, double z, int packedColor) {
        target.put(offset, Float.floatToRawIntBits((float) x));
        target.put(offset + 1, Float.floatToRawIntBits((float) y));
        target.put(offset + 2, Float.floatToRawIntBits((float) z));
        target.put(offset + 3, packedColor);
    }

    public static int packColor(int red, int green, int blue, int alpha) {
        int r = red & 255;
        int g = green & 255;
        int b = blue & 255;
        int a = alpha & 255;
        return NATIVE_LITTLE_ENDIAN ? (a << 24) | (b << 16) | (g << 8) | r : (r << 24) | (g << 16) | (b << 8) | a;
    }

    public static int packColor(float red, float green, float blue, float alpha) {
        return packColor((int) (red * 255.0f), (int) (green * 255.0f), (int) (blue * 255.0f), (int) (alpha * 255.0f));
    }

    public static int packNormal(float x, float y, float z) {
        int nx = (byte) ((int) (x * 127.0F)) & 255;
        int ny = (byte) ((int) (y * 127.0F)) & 255;
        int nz = (byte) ((int) (z * 127.0F)) & 255;
        return NATIVE_LITTLE_ENDIAN ? (nz << 16) | (ny << 8) | nx : (nx << 24) | (ny << 16) | (nz << 8);
    }

    public static int packLightmap(int skyLight, int blockLight) {
        int block = blockLight & 0xFFFF;
        int sky = skyLight & 0xFFFF;
        return NATIVE_LITTLE_ENDIAN ? (sky << 16) | block : (block << 16) | sky;
    }

    public enum NTMFastVertexFormat {
        GENERIC,
        POSITION,
        POSITION_COLOR,
        POSITION_TEX,
        POSITION_NORMAL,
        POSITION_TEX_COLOR,
        POSITION_TEX_NORMAL,
        POSITION_TEX_LMAP_COLOR,
        POSITION_TEX_COLOR_NORMAL,
        PARTICLE_POSITION_TEX_COLOR_LMAP;

        public static NTMFastVertexFormat from(VertexFormat format) {
            if (format == DefaultVertexFormats.POSITION || DefaultVertexFormats.POSITION.equals(format)) return POSITION;
            if (format == DefaultVertexFormats.POSITION_COLOR || DefaultVertexFormats.POSITION_COLOR.equals(format)) return POSITION_COLOR;
            if (format == DefaultVertexFormats.POSITION_TEX || DefaultVertexFormats.POSITION_TEX.equals(format)) return POSITION_TEX;
            if (format == DefaultVertexFormats.POSITION_NORMAL || DefaultVertexFormats.POSITION_NORMAL.equals(format)) return POSITION_NORMAL;
            if (format == DefaultVertexFormats.POSITION_TEX_COLOR || DefaultVertexFormats.POSITION_TEX_COLOR.equals(format)) return POSITION_TEX_COLOR;
            if (format == DefaultVertexFormats.POSITION_TEX_NORMAL || DefaultVertexFormats.POSITION_TEX_NORMAL.equals(format)) return POSITION_TEX_NORMAL;
            if (format == DefaultVertexFormats.POSITION_TEX_LMAP_COLOR || DefaultVertexFormats.POSITION_TEX_LMAP_COLOR.equals(format)) return POSITION_TEX_LMAP_COLOR;
            if (format == DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL || DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL.equals(format)) return POSITION_TEX_COLOR_NORMAL;
            if (format == DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP || DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP.equals(format)) return PARTICLE_POSITION_TEX_COLOR_LMAP;
            return GENERIC;
        }
    }

    public final class PositionSink {
        public void vertex(double x, double y, double z) {
            appendPosition(x, y, z);
        }

        public void vertexUnchecked(double x, double y, double z) {
            appendPositionUnchecked(x, y, z);
        }

        public void quad(double x0, double y0, double z0,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double x3, double y3, double z3) {
            ensureDrawing(NTMFastVertexFormat.POSITION);
            if (!hasRemainingInts(12)) {
                growBuffer(12 * Integer.BYTES);
            }
            appendPositionQuadUnchecked(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        }

        public void quadUnchecked(double x0, double y0, double z0,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  double x3, double y3, double z3) {
            appendPositionQuadUnchecked(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3);
        }
    }

    public final class PositionColorSink {
        public void vertex(double x, double y, double z, int packedColor) {
            appendPositionColor(x, y, z, packedColor);
        }

        public void vertexUnchecked(double x, double y, double z, int packedColor) {
            appendPositionColorUnchecked(x, y, z, packedColor);
        }

        public void quad(double x0, double y0, double z0,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         double x3, double y3, double z3,
                         int packedColor) {
            appendPositionColorQuad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, packedColor);
        }

        public void quadUnchecked(double x0, double y0, double z0,
                                  double x1, double y1, double z1,
                                  double x2, double y2, double z2,
                                  double x3, double y3, double z3,
                                  int packedColor) {
            appendPositionColorQuadUnchecked(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, packedColor);
        }
    }

    public final class PositionTexSink {
        public void vertex(double x, double y, double z, double u, double v) {
            appendPositionTex(x, y, z, u, v);
        }

        public void vertexUnchecked(double x, double y, double z, double u, double v) {
            appendPositionTexUnchecked(x, y, z, u, v);
        }

        public void quad(double x0, double y0, double z0, double u0, double v0,
                         double x1, double y1, double z1, double u1, double v1,
                         double x2, double y2, double z2, double u2, double v2,
                         double x3, double y3, double z3, double u3, double v3) {
            ensureDrawing(NTMFastVertexFormat.POSITION_TEX);
            if (!hasRemainingInts(20)) {
                growBuffer(20 * Integer.BYTES);
            }
            appendPositionTexQuadUnchecked(x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3);
        }

        public void quadUnchecked(double x0, double y0, double z0, double u0, double v0,
                                  double x1, double y1, double z1, double u1, double v1,
                                  double x2, double y2, double z2, double u2, double v2,
                                  double x3, double y3, double z3, double u3, double v3) {
            appendPositionTexQuadUnchecked(x0, y0, z0, u0, v0, x1, y1, z1, u1, v1, x2, y2, z2, u2, v2, x3, y3, z3, u3, v3);
        }
    }

    public final class PositionTexColorSink {
        public void vertex(double x, double y, double z, double u, double v, int packedColor) {
            appendPositionTexColor(x, y, z, u, v, packedColor);
        }

        public void vertexUnchecked(double x, double y, double z, double u, double v, int packedColor) {
            appendPositionTexColorUnchecked(x, y, z, u, v, packedColor);
        }

        public void quad(double x0, double y0, double z0, double u0, double v0, int c0,
                         double x1, double y1, double z1, double u1, double v1, int c1,
                         double x2, double y2, double z2, double u2, double v2, int c2,
                         double x3, double y3, double z3, double u3, double v3, int c3) {
            ensureDrawing(NTMFastVertexFormat.POSITION_TEX_COLOR);
            if (!hasRemainingInts(24)) {
                growBuffer(24 * Integer.BYTES);
            }
            appendPositionTexColorQuadUnchecked(x0, y0, z0, u0, v0, c0, x1, y1, z1, u1, v1, c1, x2, y2, z2, u2, v2, c2, x3, y3, z3, u3, v3, c3);
        }

        public void quadUnchecked(double x0, double y0, double z0, double u0, double v0, int c0,
                                  double x1, double y1, double z1, double u1, double v1, int c1,
                                  double x2, double y2, double z2, double u2, double v2, int c2,
                                  double x3, double y3, double z3, double u3, double v3, int c3) {
            appendPositionTexColorQuadUnchecked(x0, y0, z0, u0, v0, c0, x1, y1, z1, u1, v1, c1, x2, y2, z2, u2, v2, c2, x3, y3, z3, u3, v3, c3);
        }
    }

    public final class PositionNormalSink {
        public void vertex(double x, double y, double z, int packedNormal) {
            appendPositionNormal(x, y, z, packedNormal);
        }

        public void vertexUnchecked(double x, double y, double z, int packedNormal) {
            appendPositionNormalUnchecked(x, y, z, packedNormal);
        }

        public void vertex(double x, double y, double z, float nx, float ny, float nz) {
            appendPositionNormal(x, y, z, packNormal(nx, ny, nz));
        }
    }

    public final class ParticlePositionTexColorLmapSink {
        public void vertex(double x, double y, double z, double u, double v, int packedColor, int packedLightmap) {
            appendParticlePositionTexColorLmap(x, y, z, u, v, packedColor, packedLightmap);
        }

        public void vertexUnchecked(double x, double y, double z, double u, double v, int packedColor, int packedLightmap) {
            appendParticlePositionTexColorLmapUnchecked(x, y, z, u, v, packedColor, packedLightmap);
        }

        public void vertex(double x, double y, double z, double u, double v, int packedColor, int skyLight, int blockLight) {
            appendParticlePositionTexColorLmap(x, y, z, u, v, packedColor, packLightmap(skyLight, blockLight));
        }
    }
}
