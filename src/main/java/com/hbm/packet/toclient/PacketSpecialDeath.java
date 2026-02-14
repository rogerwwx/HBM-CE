package com.hbm.packet.toclient;

import com.hbm.lib.internal.MethodHandleHelper;
import com.hbm.lib.ModDamageSource;
import com.hbm.main.MainRegistry;
import com.hbm.main.ModEventHandlerClient;
import com.hbm.main.ResourceManager;
import com.hbm.particle.DisintegrationParticleHandler;
import com.hbm.particle.ParticleBlood;
import com.hbm.particle.ParticleSlicedMob;
import com.hbm.particle.bullet_hit.EntityHitDataHandler;
import com.hbm.particle.bullet_hit.EntityHitDataHandler.BulletHit;
import com.hbm.particle.bullet_hit.ParticleMobGib;
import com.hbm.physics.RigidBody;
import com.hbm.render.amlfrom1710.Vec3;
import com.hbm.render.util.ModelRendererUtil;
import com.hbm.render.util.Triangle;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.util.vector.Matrix4f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Random;

public class PacketSpecialDeath implements IMessage {

	private static final MethodHandle rGetHurtSound = MethodHandleHelper.findVirtual(EntityLivingBase.class, "getHurtSound", "func_184601_bQ", MethodType.methodType(SoundEvent.class, DamageSource.class));
	
	Entity serverEntity;
	int entId;
	int effectId;
	float[] auxData;
	Object auxObj;
	
	public PacketSpecialDeath() {
	}
	
	public PacketSpecialDeath(Entity ent, int effectId, float... auxData) {
		serverEntity = ent;
		this.effectId = effectId;
		this.entId = ent.getEntityId();
		this.auxData = auxData;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		entId = buf.readInt();
		effectId = buf.readInt();
		int len = buf.readByte();
		auxData = new float[len];
		for(int i = 0; i < len; i++){
			auxData[i] = buf.readFloat();
		}
		if(effectId == 4){
			auxObj = EntityHitDataHandler.decodeData(buf);
		}
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeInt(entId);
		buf.writeInt(effectId);
		buf.writeByte(auxData.length);
		for(float f : auxData){
			buf.writeFloat(f);
		}
		if(effectId == 4){
			EntityHitDataHandler.encodeData(serverEntity, buf);
		}
	}

	public static class Handler implements IMessageHandler<PacketSpecialDeath, IMessage> {
		@Override
		@SideOnly(Side.CLIENT)
		public IMessage onMessage(PacketSpecialDeath m, MessageContext ctx) {
			Minecraft.getMinecraft().addScheduledTask(() -> {
				Entity ent = Minecraft.getMinecraft().world.getEntityByID(m.entId);
				if (ent instanceof EntityLivingBase livingBase) {
					switch (m.effectId) {
						case 0 -> { ent.isDead = true; ModEventHandlerClient.specialDeathEffectEntities.add(livingBase); DisintegrationParticleHandler.spawnGluonDisintegrateParticles(ent); }
						case 1 -> { livingBase.hurtTime = 2; try { SoundEvent s = (SoundEvent) rGetHurtSound.invokeExact(livingBase, ModDamageSource.radiation); Minecraft.getMinecraft().world.playSound(ent.posX, ent.posY, ent.posZ, s, SoundCategory.MASTER, 1, 1, false); } catch (Throwable e) { MainRegistry.logger.catching(e); throw new RuntimeException(e); } }
						case 2 -> { ent.isDead = true; ModEventHandlerClient.specialDeathEffectEntities.add(livingBase); DisintegrationParticleHandler.spawnLightningDisintegrateParticles(ent, new Vec3(m.auxData[0], m.auxData[1], m.auxData[2])); }
						case 3 -> {
							ent.isDead = true;
							float[] data = m.auxData;
							int id = Float.floatToIntBits(data[4]);
							ResourceLocation capTex = id == 0 ? ResourceManager.gore_generic : ResourceManager.crucible_cap;

							ModelRendererUtil.generateCutParticlesAsync(
									ent, data, capTex, id == 1 ? 1 : 0,
									// capConsumer: 在主线程执行（生成血液与火花）
									capTris -> {
										if (capTris.isEmpty()) return;
										Random rand = ent.world.rand;
										int bloodCount = 5, cCount = id == 1 ? 8 : 0;
										for (int i = 0; i < bloodCount + cCount; i++) {
											Triangle tri = capTris.get(rand.nextInt(capTris.size()));
											float r1 = rand.nextFloat(), r2 = rand.nextFloat();
											if (r2 < r1) { float tmp = r2; r2 = r1; r1 = tmp; }
											Vec3d pos = tri.p1.pos.scale(r1)
													.add(tri.p2.pos.scale(r2 - r1))
													.add(tri.p3.pos.scale(1 - r2))
													.add(ent.posX, ent.posY, ent.posZ);

											if (i < bloodCount) {
												ParticleBlood blood = new ParticleBlood(ent.world, pos.x, pos.y, pos.z,
														1, 0.4F + rand.nextFloat() * 0.4F, 18 + rand.nextInt(10), 0.05F);
												Vec3d dir = Minecraft.getMinecraft().player.getLook(1)
														.crossProduct(new Vec3d(data[0], data[1], data[2]))
														.normalize().scale(-0.6F)
														.add(new Vec3d(rand.nextDouble()*2-1, rand.nextDouble()*2-1, rand.nextDouble()*2-1).scale(0.2F));
												blood.motion((float)dir.x,(float)dir.y,(float)dir.z);
												blood.color(0.5F,0.1F,0.1F,1F);
												blood.onUpdate();
												Minecraft.getMinecraft().effectRenderer.addEffect(blood);
											} else {
												Vec3d dir = capTris.get(0).p2.pos.subtract(capTris.get(0).p1.pos)
														.crossProduct(capTris.get(2).p2.pos.subtract(capTris.get(0).p1.pos))
														.normalize().scale(i % 2 == 0 ? 0.4 : -0.4);
												NBTTagCompound tag = new NBTTagCompound();
												tag.setString("type","spark"); tag.setString("mode","coneBurst");
												tag.setDouble("posX",pos.x); tag.setDouble("posY",pos.y); tag.setDouble("posZ",pos.z);
												tag.setDouble("dirX",dir.x); tag.setDouble("dirY",dir.y); tag.setDouble("dirZ",dir.z);
												tag.setFloat("r",0.8F); tag.setFloat("g",0.6F); tag.setFloat("b",0.5F); tag.setFloat("a",1.5F);
												tag.setInteger("lifetime",20); tag.setFloat("width",0.02F); tag.setFloat("length",0.8F);
												tag.setFloat("randLength",1.3F); tag.setFloat("gravity",0.1F); tag.setFloat("angle",60F);
												tag.setInteger("count",12); tag.setFloat("randomVelocity",0.3F);
												MainRegistry.proxy.effectNT(tag);
											}
										}
									},
									// callback: 在主线程执行，把生成的粒子加入 effectRenderer
									particles -> {
										for (ParticleSlicedMob p : particles) {
											Minecraft.getMinecraft().effectRenderer.addEffect(p);
										}
									}
							);
						}
						case 4 -> {
							ent.setDead();
							@SuppressWarnings("unchecked") List<BulletHit> bHits = (List<BulletHit>) m.auxObj;
							List<Pair<Matrix4f, ModelRenderer>> boxes = ModelRendererUtil.getBoxesFromMob(livingBase);
							RigidBody[] bodies = ModelRendererUtil.generateRigidBodiesFromBoxes(ent, boxes);
							int[] displayLists = ModelRendererUtil.generateDisplayListsFromBoxes(boxes);
							ResourceLocation tex = ModelRendererUtil.getEntityTexture(ent);
							for (int i = 0; i < bodies.length; i++) {
								for (BulletHit b : bHits) {
									float dist = (float)b.pos.distanceTo(bodies[i].globalCentroid.toVec3d());
									float falloff = pointLightFalloff(1, dist);
									float regular = 1.5F * falloff;
									bodies[i].impulseVelocityDirect(new Vec3(b.direction.scale(regular)), new Vec3(b.pos));
								}
								bodies[i].angularVelocity = bodies[i].angularVelocity.min(10).max(-10);
								Minecraft.getMinecraft().effectRenderer.addEffect(new ParticleMobGib(ent.world, bodies[i], tex, displayLists[i]));
							}
						}
					}
				}
			});
			return null;
		}
	}



	//Epic games lighting model falloff
	public static float pointLightFalloff(float radius, float dist){
		float distOverRad = dist/radius;
		float distOverRad2 = distOverRad*distOverRad;
		float distOverRad4 = distOverRad2*distOverRad2;
		
		float falloff = MathHelper.clamp(1-distOverRad4, 0, 1);
		return (falloff * falloff)/(dist*dist + 1);
	}
	
}
