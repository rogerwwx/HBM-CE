package com.hbm.render.util;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

@SideOnly(Side.CLIENT)
public final class NTMImmediate {

    public static final NTMImmediate INSTANCE = new NTMImmediate(262_144);

    private final NTMBufferBuilder buffer;
    private final WorldVertexBufferUploader genericUploader = new WorldVertexBufferUploader();
    private final SpecializedUploader[] uploaders = new SpecializedUploader[NTMBufferBuilder.NTMFastVertexFormat.values().length];

    private NTMImmediate(int initialBufferSizeInInts) {
        this.buffer = new NTMBufferBuilder(initialBufferSizeInInts);
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION.ordinal()] = NTMImmediate::drawPosition;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_COLOR.ordinal()] = NTMImmediate::drawPositionColor;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_TEX.ordinal()] = NTMImmediate::drawPositionTex;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_NORMAL.ordinal()] = NTMImmediate::drawPositionNormal;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_TEX_COLOR.ordinal()] = NTMImmediate::drawPositionTexColor;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_TEX_NORMAL.ordinal()] = NTMImmediate::drawPositionTexNormal;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_TEX_LMAP_COLOR.ordinal()] = NTMImmediate::drawPositionTexLmapColor;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.POSITION_TEX_COLOR_NORMAL.ordinal()] = NTMImmediate::drawPositionTexColorNormal;
        uploaders[NTMBufferBuilder.NTMFastVertexFormat.PARTICLE_POSITION_TEX_COLOR_LMAP.ordinal()] = NTMImmediate::drawParticlePositionTexColorLmap;
    }

    public BufferBuilder begin(int drawMode, VertexFormat format) {
        return begin(drawMode, format, 0);
    }

    public BufferBuilder begin(int drawMode, VertexFormat format, int expectedVertices) {
        buffer.beginFast(drawMode, format, expectedVertices);
        return buffer;
    }

    public NTMBufferBuilder.PositionSink beginPosition(int drawMode, int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.POSITION, expectedVertices);
        return buffer.position();
    }

    public NTMBufferBuilder.PositionSink beginPositionQuads(int expectedQuads) {
        return beginPosition(GL11.GL_QUADS, expectedQuads * 4);
    }

    public NTMBufferBuilder.PositionColorSink beginPositionColor(int drawMode, int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.POSITION_COLOR, expectedVertices);
        return buffer.positionColor();
    }

    public NTMBufferBuilder.PositionColorSink beginPositionColorQuads(int expectedQuads) {
        return beginPositionColor(GL11.GL_QUADS, expectedQuads * 4);
    }

    public NTMBufferBuilder.PositionTexSink beginPositionTex(int drawMode, int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.POSITION_TEX, expectedVertices);
        return buffer.positionTex();
    }

    public NTMBufferBuilder.PositionTexSink beginPositionTexQuads(int expectedQuads) {
        return beginPositionTex(GL11.GL_QUADS, expectedQuads * 4);
    }

    public NTMBufferBuilder.PositionTexColorSink beginPositionTexColor(int drawMode, int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.POSITION_TEX_COLOR, expectedVertices);
        return buffer.positionTexColor();
    }

    public NTMBufferBuilder.PositionTexColorSink beginPositionTexColorQuads(int expectedQuads) {
        return beginPositionTexColor(GL11.GL_QUADS, expectedQuads * 4);
    }

    public NTMBufferBuilder.PositionNormalSink beginPositionNormal(int drawMode, int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.POSITION_NORMAL, expectedVertices);
        return buffer.positionNormal();
    }

    public NTMBufferBuilder.ParticlePositionTexColorLmapSink beginParticlePositionTexColorLmap(int drawMode,
                                                                                                int expectedVertices) {
        buffer.beginFast(drawMode, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP, expectedVertices);
        return buffer.particlePositionTexColorLmap();
    }

    public NTMBufferBuilder getBuffer() {
        return buffer;
    }

    public void draw() {
        buffer.finishDrawing();
        SpecializedUploader uploader = uploaders[buffer.getFastFormat().ordinal()];
        if (uploader != null) {
            uploader.draw(buffer);
            buffer.reset();
        } else {
            genericUploader.draw(buffer);
        }
    }

    private static void drawPosition(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 12, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionColor(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 16, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.resetColor();
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionTex(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 20, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 20, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionNormal(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 16, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glNormalPointer(GL11.GL_BYTE, 16, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionTexColor(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 24, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 24, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        byteBuffer.position(20);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 24, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.resetColor();
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionTexNormal(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 24, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 24, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        byteBuffer.position(20);
        GlStateManager.glNormalPointer(GL11.GL_BYTE, 24, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionTexLmapColor(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(20);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        byteBuffer.position(24);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.resetColor();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawPositionTexColorNormal(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        byteBuffer.position(20);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        byteBuffer.position(24);
        GlStateManager.glNormalPointer(GL11.GL_BYTE, 28, byteBuffer);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        GlStateManager.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.resetColor();
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    private static void drawParticlePositionTexColorLmap(NTMBufferBuilder buffer) {
        ByteBuffer byteBuffer = buffer.getByteBuffer();
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        byteBuffer.position(0);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, byteBuffer);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
        byteBuffer.position(20);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        byteBuffer.position(24);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, byteBuffer);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glDrawArrays(buffer.getDrawMode(), 0, buffer.getVertexCount());
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GlStateManager.resetColor();
        GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
    }

    @FunctionalInterface
    private interface SpecializedUploader {
        void draw(NTMBufferBuilder buffer);
    }
}
