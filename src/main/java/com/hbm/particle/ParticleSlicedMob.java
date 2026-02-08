package com.hbm.particle;

import com.hbm.config.GeneralConfig;
import com.hbm.handler.HbmShaderManager2;
import com.hbm.main.ClientProxy;
import com.hbm.physics.RigidBody;
import com.hbm.render.NTMRenderHelper;
import com.hbm.render.amlfrom1710.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class ParticleSlicedMob extends Particle {

	public ResourceLocation capTex;
	public ResourceLocation texture;
	public float capBloom;
	public int vaoBody;
	public int vaoCap;
	public int countBody;
	public int countCap;
	public RigidBody body;

	public ParticleSlicedMob(World worldIn, RigidBody body,
							 int vaoBody, int vaoCap,
							 int countBody, int countCap,
							 ResourceLocation tex, ResourceLocation capTex, float capBloom) {
		super(worldIn, body.globalCentroid.xCoord, body.globalCentroid.yCoord, body.globalCentroid.zCoord);
		this.body = body;
		this.vaoBody = vaoBody;
		this.vaoCap = vaoCap;
		this.countBody = countBody;
		this.countCap = countCap;
		this.capTex = capTex;
		this.capBloom = capBloom;
		this.texture = tex;
		this.particleMaxAge = 80 + worldIn.rand.nextInt(20);
	}

	@Override
	public void onUpdate() {
		body.minecraftTimestep();
		this.posX = body.globalCentroid.xCoord;
		this.posY = body.globalCentroid.yCoord;
		this.posZ = body.globalCentroid.zCoord;
		this.particleAge++;
		if(particleAge >= particleMaxAge){
			setExpired();
			GL30.glDeleteVertexArrays(vaoBody);
			GL30.glDeleteVertexArrays(vaoCap);
			// 如果你保存了 VBO ID，也要调用 GL15.glDeleteBuffers(vboId)
		}
	}

	@Override
	public int getFXLayer() {
		return 3;
	}

	@Override
	public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks,
							   float rotationX, float rotationZ, float rotationYZ,
							   float rotationXY, float rotationXZ) {
		GlStateManager.pushMatrix();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		GlStateManager.enableColorMaterial();
		GlStateManager.enableRescaleNormal();
		net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();

		int i = this.getBrightnessForRender(partialTicks);
		int j = i >> 16 & 65535;
		int k = i & 65535;
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, k, j);

		Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
		NTMRenderHelper.resetParticleInterpPos(entityIn, partialTicks);
		NTMRenderHelper.resetColor();
		body.doGlTransform(new Vec3(interpPosX, interpPosY, interpPosZ), partialTicks);

		// 绘制主体
		GL30.glBindVertexArray(vaoBody);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, countBody);
		GL30.glBindVertexArray(0);

		// 绘制切割面
		Minecraft.getMinecraft().getTextureManager().bindTexture(capTex);
		GlStateManager.enablePolygonOffset();
		GlStateManager.doPolygonOffset(-1, -1);

		float lx = OpenGlHelper.lastBrightnessX;
		float ly = OpenGlHelper.lastBrightnessY;
		if(capBloom > 0){
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
		}

		GL30.glBindVertexArray(vaoCap);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, countCap);
		GL30.glBindVertexArray(0);

		// Bloom 渲染逻辑保持不变，只是把 glCallList(cap) 改成 VAO 绘制
		if(capBloom > 0 && GeneralConfig.bloom){
			float[] matrix = new float[16];
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, ClientProxy.AUX_GL_BUFFER);
			ClientProxy.AUX_GL_BUFFER.get(matrix);
			ClientProxy.AUX_GL_BUFFER.rewind();
			ClientProxy.deferredRenderers.add(() -> {
				GlStateManager.pushMatrix();
				ClientProxy.AUX_GL_BUFFER.put(matrix);
				ClientProxy.AUX_GL_BUFFER.rewind();
				GL11.glLoadMatrix(ClientProxy.AUX_GL_BUFFER);
				HbmShaderManager2.bloomData.bindFramebuffer(false);

				Minecraft.getMinecraft().getTextureManager().bindTexture(capTex);
				GlStateManager.enablePolygonOffset();
				GlStateManager.disableLighting();
				float x = OpenGlHelper.lastBrightnessX;
				float y = OpenGlHelper.lastBrightnessY;
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
				GlStateManager.doPolygonOffset(-1, -1);

				GL30.glBindVertexArray(vaoCap);
				GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, countCap);
				GL30.glBindVertexArray(0);

				GlStateManager.disablePolygonOffset();
				GlStateManager.enableLighting();
				OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, x, y);

				Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
				GlStateManager.popMatrix();
			});
		}

		if(capBloom > 0){
			OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
		}
		GlStateManager.disablePolygonOffset();
		GlStateManager.disableRescaleNormal();
		net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
		GlStateManager.popMatrix();
	}
}
