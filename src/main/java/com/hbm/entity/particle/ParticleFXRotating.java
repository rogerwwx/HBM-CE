package com.hbm.entity.particle;

import com.hbm.render.util.NTMBufferBuilder;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ParticleFXRotating extends Particle {
    public float hue;
    protected float newScale;

    protected ParticleFXRotating(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public int getFXLayer() {
        return 1;
    }

    public void renderParticle(BufferBuilder buffer, Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        float scale = newScale;

        float minU = this.particleTexture.getMinU();
        float maxU = this.particleTexture.getMaxU();
        float minV = this.particleTexture.getMinV();
        float maxV = this.particleTexture.getMaxV();

        float f5 = (float) (this.prevPosX + (this.posX - this.prevPosX) * (double) partialTicks - interpPosX);
        float f6 = (float) (this.prevPosY + (this.posY - this.prevPosY) * (double) partialTicks - interpPosY);
        float f7 = (float) (this.prevPosZ + (this.posZ - this.prevPosZ) * (double) partialTicks - interpPosZ);
        int brightness = this.getBrightnessForRender(partialTicks);
        int lightMapJ = brightness >> 16 & 65535;
        int lightMapK = brightness & 65535;
        Vec3d[] avec3d = new Vec3d[]{new Vec3d((double) (-rotationX * scale - rotationXY * scale), (double) (-rotationZ * scale), (double) (-rotationYZ * scale - rotationXZ * scale)), new Vec3d((double) (-rotationX * scale + rotationXY * scale), (double) (rotationZ * scale), (double) (-rotationYZ * scale + rotationXZ * scale)), new Vec3d((double) (rotationX * scale + rotationXY * scale), (double) (rotationZ * scale), (double) (rotationYZ * scale + rotationXZ * scale)), new Vec3d((double) (rotationX * scale - rotationXY * scale), (double) (-rotationZ * scale), (double) (rotationYZ * scale - rotationXZ * scale))};

        float rotation = this.prevParticleAngle + (this.particleAngle - this.prevParticleAngle) * partialTicks;
        float f9 = MathHelper.cos(rotation * 0.5F);
        float f10 = MathHelper.sin(rotation * 0.5F) * (float) cameraViewDir.x;
        float f11 = MathHelper.sin(rotation * 0.5F) * (float) cameraViewDir.y;
        float f12 = MathHelper.sin(rotation * 0.5F) * (float) cameraViewDir.z;
        Vec3d vec3d = new Vec3d((double) f10, (double) f11, (double) f12); //FIXME: doesnt spin, jitters around

        for (int l = 0; l < 4; ++l) {
            Vec3d v = avec3d[l];
            Vec3d k = cameraViewDir.normalize();
            double cosTheta = Math.cos(rotation * Math.PI / 180D);
            double sinTheta = Math.sin(rotation * Math.PI / 180D);

            Vec3d term1 = v.scale(cosTheta);
            Vec3d term2 = k.crossProduct(v).scale(sinTheta);
            Vec3d term3 = k.scale(k.dotProduct(v) * (1.0 - cosTheta));

            avec3d[l] = term1.add(term2).add(term3);
        }


        NTMBufferBuilder fastBuffer = (NTMBufferBuilder) buffer;
        int packedColor = NTMBufferBuilder.packColor(this.particleRed, this.particleGreen, this.particleBlue, this.particleAlpha);
        int packedLightmap = NTMBufferBuilder.packLightmap(lightMapJ, lightMapK);
        fastBuffer.appendParticlePositionTexColorLmap((double) f5 + avec3d[0].x, (double) f6 + avec3d[0].y, (double) f7 + avec3d[0].z, (double) maxU, (double) maxV, packedColor, packedLightmap);
        fastBuffer.appendParticlePositionTexColorLmap((double) f5 + avec3d[1].x, (double) f6 + avec3d[1].y, (double) f7 + avec3d[1].z, (double) maxU, (double) minV, packedColor, packedLightmap);
        fastBuffer.appendParticlePositionTexColorLmap((double) f5 + avec3d[2].x, (double) f6 + avec3d[2].y, (double) f7 + avec3d[2].z, (double) minU, (double) minV, packedColor, packedLightmap);
        fastBuffer.appendParticlePositionTexColorLmap((double) f5 + avec3d[3].x, (double) f6 + avec3d[3].y, (double) f7 + avec3d[3].z, (double) minU, (double) maxV, packedColor, packedLightmap);
    }


}
