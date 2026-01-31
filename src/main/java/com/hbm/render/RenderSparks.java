package com.hbm.render;

import com.hbm.util.Vec3NT;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.util.Random;

public class RenderSparks {

    public static void renderSpark(int seed, double x, double y, double z, float length, int min, int max, int color1, int color2) {
        float r1 = (color1 >> 16 & 255) / 255F;
        float g1 = (color1 >> 8 & 255) / 255F;
        float b1 = (color1 & 255) / 255F;

        float r2 = (color2 >> 16 & 255) / 255F;
        float g2 = (color2 >> 8 & 255) / 255F;
        float b2 = (color2 & 255) / 255F;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Random rand = new Random(seed);

        Vec3NT directionBase = new Vec3NT(rand.nextDouble() - 0.5, rand.nextDouble() - 0.5, rand.nextDouble() - 0.5);
        directionBase.normalizeSelf();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();

        int segments = min + rand.nextInt(max - min + 1);

        for (int i = 0; i < segments; i++) {
            double prevX = x;
            double prevY = y;
            double prevZ = z;

            float segmentScale = length * rand.nextFloat();
            Vec3d step = new Vec3d(directionBase.x * segmentScale, directionBase.y * segmentScale, directionBase.z * segmentScale);

            x = prevX + step.x;
            y = prevY + step.y;
            z = prevZ + step.z;

            GlStateManager.glLineWidth(5F);
            bufferbuilder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            bufferbuilder.pos(prevX, prevY, prevZ).color(r1, g1, b1, 1.0F).endVertex();
            bufferbuilder.pos(x, y, z).color(r1, g1, b1, 1.0F).endVertex();
            tessellator.draw();

            GlStateManager.glLineWidth(2F);
            bufferbuilder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            bufferbuilder.pos(prevX, prevY, prevZ).color(r2, g2, b2, 1.0F).endVertex();
            bufferbuilder.pos(x, y, z).color(r2, g2, b2, 1.0F).endVertex();
            tessellator.draw();
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

}
