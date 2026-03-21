package com.hbm.render.entity.effect;

import com.hbm.Tags;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.effect.EntityNukeTorex.Cloudlet;
import com.hbm.interfaces.AutoRegister;
import com.hbm.main.MainRegistry;
import com.hbm.main.ModEventHandlerClient;
import com.hbm.render.InstancedBillboardBatch;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.GlStateManager.DestFactor;
import net.minecraft.client.renderer.GlStateManager.SourceFactor;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.Random;

@AutoRegister(factory = "FACTORY")
public class RenderTorex extends Render<EntityNukeTorex> {

	public static final IRenderFactory<EntityNukeTorex> FACTORY = man -> new RenderTorex(man);
	
	private static final ResourceLocation cloudlet = new ResourceLocation(Tags.MODID + ":textures/particle/particle_base.png");
	private static final ResourceLocation flare = new ResourceLocation(Tags.MODID + ":textures/particle/flare.png");
	private static final float CLOUDLET_MIN_U = 0F;
	private static final float CLOUDLET_MIN_V = 0F;
	private static final float CLOUDLET_SIZE_U = 1F;
	private static final float CLOUDLET_SIZE_V = 1F;

	public static final int flashBaseDuration = 30;
	public static final int flareBaseDuration = 100;
	private static final InstancedBillboardBatch INSTANCED_CLOUDLET_BATCH = new InstancedBillboardBatch();

	protected RenderTorex(RenderManager renderManager){
		super(renderManager);
	}

	@Override
	public void doRender(EntityNukeTorex cloud, double x, double y, double z, float entityYaw, float partialTicks){
		float scale = (float)cloud.getScale();
		float flashDuration = scale * flashBaseDuration;
		float flareDuration = scale * flareBaseDuration;
		
		GlStateManager.pushMatrix();
		GlStateManager.translate(x, y, z);

		boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
		if(fog)
			GL11.glDisable(GL11.GL_FOG);

		if(GeneralConfig.instancedParticles) {
			cloudletWrapperInstanced(cloud, partialTicks);
		} else {
			cloudletWrapper(cloud, partialTicks);
		}

		if(cloud.ticksExisted < flareDuration+1) flareWrapper(cloud, partialTicks, flareDuration);
		if(cloud.ticksExisted < flashDuration+1) flashWrapper(cloud, partialTicks, flashDuration);
		if(cloud.ticksExisted < (flashDuration / 10) && System.currentTimeMillis() - ModEventHandlerClient.flashTimestamp > 1_000) ModEventHandlerClient.flashTimestamp = System.currentTimeMillis();
		if(cloud.didPlaySound && !cloud.didShake && System.currentTimeMillis() - ModEventHandlerClient.shakeTimestamp > 1_000) {
			ModEventHandlerClient.shakeTimestamp = System.currentTimeMillis();
			cloud.didShake = true;
			EntityPlayer player = MainRegistry.proxy.me();
			float dist = player.getDistance(cloud);
			player.hurtTime = (100 - (int) dist) > 0 ? (int) ((float) (100 - (int) dist) * 1.5F) : 0;
			player.maxHurtTime = (100 - (int) dist) > 0 ? (100 - (int) dist) : 0;
			player.attackedAtYaw = 0F;
		}

		if(fog)
			GL11.glEnable(GL11.GL_FOG);

		GlStateManager.popMatrix();
	}
	
	private static final Comparator<Cloudlet> CLOUD_SORTER = (first, second) -> Double.compare(second.renderSortDistanceSq, first.renderSortDistanceSq);

	private void cloudletWrapper(EntityNukeTorex cloud, float partialTicks) {

		GlStateManager.pushMatrix();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		// To prevent particles cutting off before fully fading out
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
		GlStateManager.disableAlpha();
		GlStateManager.depthMask(false);
		RenderHelper.disableStandardItemLighting();
		
		bindTexture(cloudlet);

		Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
		buf.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
		
		sortCloudlets(cloud);
		float rotationX = ActiveRenderInfo.getRotationX();
		float rotationZ = ActiveRenderInfo.getRotationZ();
		float rotationYZ = ActiveRenderInfo.getRotationYZ();
		float rotationXY = ActiveRenderInfo.getRotationXY();
		float rotationXZ = ActiveRenderInfo.getRotationXZ();
		
		for(Cloudlet cloudlet : cloud.cloudlets) {
			double posX = cloudlet.prevPosX + (cloudlet.posX - cloudlet.prevPosX) * partialTicks - cloud.posX;
			double posY = cloudlet.prevPosY + (cloudlet.posY - cloudlet.prevPosY) * partialTicks - cloud.posY;
			double posZ = cloudlet.prevPosZ + (cloudlet.posZ - cloudlet.prevPosZ) * partialTicks - cloud.posZ;
			tessellateCloudlet(buf, posX, posY, posZ, cloudlet, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
		}

		tess.draw();

		GlStateManager.depthMask(true);
		GlStateManager.enableAlpha();
		RenderHelper.enableStandardItemLighting();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
		GlStateManager.disableBlend();
		GlStateManager.popMatrix();
	}

	private void cloudletWrapperInstanced(EntityNukeTorex cloud, float partialTicks) {
		GlStateManager.pushMatrix();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
		GlStateManager.disableAlpha();
		GlStateManager.depthMask(false);
		RenderHelper.disableStandardItemLighting();

		sortCloudlets(cloud);
		int cloudletCount = cloud.cloudlets.size();
		if(cloudletCount == 0) {
			GlStateManager.depthMask(true);
			GlStateManager.enableAlpha();
			RenderHelper.enableStandardItemLighting();
			GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
			GlStateManager.disableBlend();
			GlStateManager.popMatrix();
			return;
		}
		ByteBuffer instancedCloudletBuffer = INSTANCED_CLOUDLET_BATCH.begin(cloudletCount);
		for(Cloudlet cloudlet : cloud.cloudlets) {
			writeCloudletInstance(instancedCloudletBuffer, cloud, cloudlet, partialTicks);
		}

		bindTexture(cloudlet);
		INSTANCED_CLOUDLET_BATCH.draw(cloudletCount);

		GlStateManager.depthMask(true);
		GlStateManager.enableAlpha();
		RenderHelper.enableStandardItemLighting();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
		GlStateManager.disableBlend();
		GlStateManager.popMatrix();
	}

	private void sortCloudlets(EntityNukeTorex cloud) {
		if(cloud.lastRenderSortTick == cloud.ticksExisted) {
			return;
		}

		EntityPlayer player = MainRegistry.proxy.me();
		if(player == null) {
			cloud.lastRenderSortTick = cloud.ticksExisted;
			return;
		}

		double playerX = player.posX;
		double playerY = player.posY;
		double playerZ = player.posZ;

		for(Cloudlet cloudlet : cloud.cloudlets) {
			double dx = playerX - cloudlet.posX;
			double dy = playerY - cloudlet.posY;
			double dz = playerZ - cloudlet.posZ;
			cloudlet.renderSortDistanceSq = dx * dx + dy * dy + dz * dz;
		}

		cloud.cloudlets.sort(CLOUD_SORTER);
		cloud.lastRenderSortTick = cloud.ticksExisted;
	}

	private void writeCloudletInstance(ByteBuffer buf, EntityNukeTorex cloud, Cloudlet cloudlet, float partialTicks) {
		double posX = cloudlet.prevPosX + (cloudlet.posX - cloudlet.prevPosX) * partialTicks - cloud.posX;
		double posY = cloudlet.prevPosY + (cloudlet.posY - cloudlet.prevPosY) * partialTicks - cloud.posY;
		double posZ = cloudlet.prevPosZ + (cloudlet.posZ - cloudlet.prevPosZ) * partialTicks - cloud.posZ;
		float alpha = cloudlet.getAlpha();
		float scale = cloudlet.getScale();
		float brightness = cloudlet.type == EntityNukeTorex.TorexType.CONDENSATION ? 0.9F : 0.75F * cloudlet.colorMod;
		double greying = cloudlet.type == EntityNukeTorex.TorexType.RING ? 0.05D : 0D;
		double colorR = cloudlet.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloudlet.prevColorR + (cloudlet.colorR - cloudlet.prevColorR) * partialTicks) + greying;
		double colorG = cloudlet.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloudlet.prevColorG + (cloudlet.colorG - cloudlet.prevColorG) * partialTicks) + greying;
		double colorB = cloudlet.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloudlet.prevColorB + (cloudlet.colorB - cloudlet.prevColorB) * partialTicks) + greying;
		float r = Math.max(0.15F, (float) colorR * brightness);
		float g = Math.max(0.15F, (float) colorG * brightness);
		float b = Math.max(0.15F, (float) colorB * brightness);
		int br = (int) Math.max(48, Math.min((r + g + b) / 3D, 1D) * 240D);
		r = Math.min(1F, r);
		g = Math.min(1F, g);
		b = Math.min(1F, b);

		InstancedBillboardBatch.writeInstance(buf, (float) posX, (float) posY, (float) posZ, scale,
				CLOUDLET_MIN_U, CLOUDLET_MIN_V, CLOUDLET_SIZE_U, CLOUDLET_SIZE_V,
				r, g, b, alpha, br, br);
	}
	
	private void flareWrapper(EntityNukeTorex cloud, float partialTicks, float flareDuration) {

		GlStateManager.pushMatrix();
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
		GlStateManager.disableAlpha();
		GlStateManager.depthMask(false);
		RenderHelper.disableStandardItemLighting();
			
		bindTexture(flare);

		Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
		buf.begin(GL11.GL_QUADS, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
		
		double age = Math.min(cloud.ticksExisted + partialTicks, flareDuration);
		float alpha = (float) Math.min(1, (flareDuration - age) / flareDuration);
		
		Random rand = new Random(cloud.getEntityId());
		
		for(int i = 0; i < 3; i++) {
			float x = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float y = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			float z = (float) (rand.nextGaussian() * 0.5F * cloud.rollerSize);
			tessellateFlare(buf, x, y + cloud.coreHeight, z, (float) (10 * cloud.rollerSize), alpha, partialTicks);
		}

		tess.draw();

		GlStateManager.depthMask(true);
		GlStateManager.enableAlpha();
		RenderHelper.enableStandardItemLighting();
		GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
		GlStateManager.disableBlend();
		GlStateManager.popMatrix();
	}

	private void tessellateCloudlet(BufferBuilder buf, double posX, double posY, double posZ, Cloudlet cloud, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {

		float a = cloud.getAlpha();
		float scale = cloud.getScale();
		float brightness = cloud.type == EntityNukeTorex.TorexType.CONDENSATION ? 0.9F : 0.75F * cloud.colorMod;
		double greying = cloud.type == EntityNukeTorex.TorexType.RING ? 0.05D : 0D;
		double colorR = cloud.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloud.prevColorR + (cloud.colorR - cloud.prevColorR) * partialTicks) + greying;
		double colorG = cloud.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloud.prevColorG + (cloud.colorG - cloud.prevColorG) * partialTicks) + greying;
		double colorB = cloud.type == EntityNukeTorex.TorexType.CONDENSATION ? 1D : (cloud.prevColorB + (cloud.colorB - cloud.prevColorB) * partialTicks) + greying;
		float r = Math.max(0.15F, (float) colorR * brightness);
		float g = Math.max(0.15F, (float) colorG * brightness);
		float b = Math.max(0.15F, (float) colorB * brightness);

		int br = (int)Math.max(48, (Math.min((r+g+b) / 3D, 1) * 240));
		r = Math.min(1F, r);
		g = Math.min(1F, g);
		b = Math.min(1F, b);

		buf.pos((double) (posX - rotationX * scale - rotationYZ * scale), (double) (posY - rotationXZ * scale), (double) (posZ - rotationZ * scale - rotationXY * scale)).tex(1, 1).color(r, g, b, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX - rotationX * scale + rotationYZ * scale), (double) (posY + rotationXZ * scale), (double) (posZ - rotationZ * scale + rotationXY * scale)).tex(1, 0).color(r, g, b, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX + rotationX * scale + rotationYZ * scale), (double) (posY + rotationXZ * scale), (double) (posZ + rotationZ * scale + rotationXY * scale)).tex(0, 0).color(r, g, b, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX + rotationX * scale - rotationYZ * scale), (double) (posY - rotationXZ * scale), (double) (posZ + rotationZ * scale - rotationXY * scale)).tex(0, 1).color(r, g, b, a).lightmap(br, br).endVertex();
	}

	private void tessellateFlare(BufferBuilder buf, double posX, double posY, double posZ, float scale, float a, float partialTicks) {

		float f1 = ActiveRenderInfo.getRotationX();
		float f2 = ActiveRenderInfo.getRotationZ();
		float f3 = ActiveRenderInfo.getRotationYZ();
		float f4 = ActiveRenderInfo.getRotationXY();
		float f5 = ActiveRenderInfo.getRotationXZ();
		int br = (int)(a * 240);
		buf.pos((double) (posX - f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ - f2 * scale - f4 * scale)).tex(1, 1).color(1F, 1F, 1F, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX - f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ - f2 * scale + f4 * scale)).tex(1, 0).color(1F, 1F, 1F, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX + f1 * scale + f3 * scale), (double) (posY + f5 * scale), (double) (posZ + f2 * scale + f4 * scale)).tex(0, 0).color(1F, 1F, 1F, a).lightmap(br, br).endVertex();
		buf.pos((double) (posX + f1 * scale - f3 * scale), (double) (posY - f5 * scale), (double) (posZ + f2 * scale - f4 * scale)).tex(0, 1).color(1F, 1F, 1F, a).lightmap(br, br).endVertex();

	}

	private void flashWrapper(EntityNukeTorex cloud, float interp, float flashDuration) {

        if(cloud.ticksExisted < flashDuration) {

    		GlStateManager.pushMatrix();
    		//Function [0, 1] that determines the scale and intensity (inverse!) of the flash
        	double intensity = (cloud.ticksExisted + interp) / flashDuration;
        	GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);

        	//Euler function to slow down the scale as it progresses
        	//Makes it start fast and the fade-out is nice and smooth
        	intensity = intensity * Math.pow(Math.E, -intensity) * 2.717391304D;

        	renderFlash(50F * (float)flashDuration/(float)flashBaseDuration, intensity, cloud.coreHeight);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    		GlStateManager.popMatrix();
        }
	}

	private void renderFlash(float scale, double intensity, double height) {

    	GlStateManager.scale(0.2F, 0.2F, 0.2F);
    	GlStateManager.translate(0, height * 4, 0);

    	double inverse = 1.0D - intensity;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
		RenderHelper.disableStandardItemLighting();

        Random random = new Random(432L);
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
        GlStateManager.disableAlpha();
        GlStateManager.enableCull();
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
		
        GlStateManager.pushMatrix();

        for(int i = 0; i < 300; i++) {

            GlStateManager.rotate(random.nextFloat() * 360.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(random.nextFloat() * 360.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(random.nextFloat() * 360.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(random.nextFloat() * 360.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(random.nextFloat() * 360.0F, 0.0F, 1.0F, 0.0F);

            float vert1 = (random.nextFloat() * 20.0F + 5.0F + 1 * 10.0F) * (float)(intensity * scale);
            float vert2 = (random.nextFloat() * 2.0F + 1.0F + 1 * 2.0F) * (float)(intensity * scale);

            buf.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
            buf.pos(0D, 0D, 0D).color(1.0F, 1.0F, 1.0F, (float) inverse).endVertex();
            buf.pos(-0.866D * vert2, vert1, -0.5D * vert2).color(1.0F, 1.0F, 1.0F, 0.0F).endVertex();
            buf.pos(0.866D * vert2, vert1, -0.5D * vert2).color(1.0F, 1.0F, 1.0F, 0.0F).endVertex();
            buf.pos(0.0D, vert1, 1.0D * vert2).color(1.0F, 1.0F, 1.0F, 0.0F).endVertex();
            buf.pos(-0.866D * vert2, vert1, -0.5D * vert2).color(1.0F, 1.0F, 1.0F, 0.0F).endVertex();
            tessellator.draw();
        }

        GlStateManager.popMatrix();

        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        RenderHelper.enableStandardItemLighting();
	}

	@Override
	protected ResourceLocation getEntityTexture(EntityNukeTorex entity) {
		return null;
	}
}
