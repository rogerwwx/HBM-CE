package com.hbm.packet.toserver;

import com.hbm.items.weapon.ItemCrucible;
import com.hbm.items.weapon.ItemSwordCutter;
import com.hbm.lib.Library;
import com.hbm.lib.ModDamageSource;
import com.hbm.packet.PacketDispatcher;
import com.hbm.packet.toclient.PacketSpecialDeath;
import com.hbm.packet.toclient.GuiDeathPacket;
import com.hbm.util.DropItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PacketMobSlicer implements IMessage {

	public static final ScheduledExecutorService scheduler =
			Executors.newSingleThreadScheduledExecutor();

	public Vec3d pos;
	public Vec3d norm;
	public byte tex;

	// 必须的无参构造器（FML 反射用）
	public PacketMobSlicer() {
	}

	public PacketMobSlicer(Vec3d position, Vec3d normal, byte tex) {
		this.pos = position;
		this.norm = normal;
		this.tex = tex;
	}

	@Override
	public void fromBytes(ByteBuf buf) {
		pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
		norm = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
		tex = buf.readByte();
	}

	@Override
	public void toBytes(ByteBuf buf) {
		buf.writeDouble(pos.x);
		buf.writeDouble(pos.y);
		buf.writeDouble(pos.z);
		buf.writeDouble(norm.x);
		buf.writeDouble(norm.y);
		buf.writeDouble(norm.z);
		buf.writeByte(tex);
	}

	public static class Handler implements IMessageHandler<PacketMobSlicer, IMessage> {

		// 必须的无参构造器
		public Handler() {
		}

		@Override
		public IMessage onMessage(PacketMobSlicer m, MessageContext ctx) {
			var p = ctx.getServerHandler().player;
			var heldStack = p.getHeldItemMainhand();

			if (!(heldStack.getItem() instanceof ItemSwordCutter)) return null;

			p.getServer().addScheduledTask(() -> {
				var attack = new ArrayList<EntityLivingBase>();
				var eye = p.getPositionEyes(1);
				var v1 = p.getLookVec();
				var v2 = m.pos.subtract(eye);

				for (float i = 0; i <= 1; i += 0.1F) {
					var dir = new Vec3d(
							v1.x + (v2.x - v1.x) * i,
							v1.y + (v2.y - v1.y) * i,
							v1.z + (v2.z - v1.z) * i
					).normalize();

					var r = Library.rayTraceIncludeEntities(p.world, eye, eye.add(dir.scale(3)), p);

					if (r != null && r.typeOfHit == RayTraceResult.Type.ENTITY
							&& r.entityHit instanceof EntityLivingBase victim
							&& !attack.contains(victim)) {
						attack.add(victim);
					}
				}

				if (heldStack.getItem() instanceof ItemCrucible crucible) {
					if (ItemCrucible.getCharges(heldStack) == 0) return;
					if (!attack.isEmpty()) ItemCrucible.discharge(heldStack);
				}

				for (var victim : attack) {
					var pos = m.pos.subtract(victim.posX, victim.posY, victim.posZ);
					var data = new float[]{
							(float) m.norm.x,
							(float) m.norm.y,
							(float) m.norm.z,
							-(float) m.norm.dotProduct(pos),
							Float.intBitsToFloat(m.tex)
					};

					// 立即处理死亡相关效果（保持原逻辑顺序）
					applyDeathEffects(victim, m, data);

					// 捕获 finalTarget 并按原逻辑安排延迟清理（但避免使用 instanceof pattern 绑定）
					final Entity finalTarget = victim;
					if (finalTarget.world instanceof WorldServer) {
						WorldServer ws = (WorldServer) finalTarget.world;
						scheduler.schedule(() -> ws.addScheduledTask(() -> {
							if (finalTarget instanceof EntityPlayerMP) return;

							if (finalTarget.isBeingRidden()) finalTarget.removePassengers();
							if (finalTarget.isRiding()) finalTarget.dismountRidingEntity();

							if (!finalTarget.isDead && finalTarget.world != null
									&& finalTarget.world.loadedEntityList.contains(finalTarget)) {
								finalTarget.onRemovedFromWorld();
								ws.onEntityRemoved(finalTarget);
								var chunk = finalTarget.world.getChunk(finalTarget.chunkCoordX, finalTarget.chunkCoordZ);
								if (chunk != null) chunk.removeEntity(finalTarget);

								ws.loadedEntityList.remove(finalTarget);
								ws.removeEntityDangerously(finalTarget);

								finalTarget.world = null;
							}
						}), 150, TimeUnit.MILLISECONDS);
					}
				}
			});
			return null;
		}

		/**
		 * 对单个实体执行死亡相关的处理：发送特殊死亡包、设置死亡状态、处理玩家掉落、并发送玩家专属死亡 GUI。
		 * 仅把原来循环内的死亡相关代码抽出，逻辑与原来完全一致。
		 */
		private static void applyDeathEffects(EntityLivingBase victim, PacketMobSlicer m, float[] data) {
			var source = (m.tex == 1) ? ModDamageSource.crucible : ModDamageSource.slicer;

			victim.getCombatTracker().trackDamage(source, victim.getHealth(), victim.getHealth());

			if (victim instanceof EntityPlayerMP playerVictim) {
				DropItem.dropPlayerInventory(playerVictim, true);
				victim.getEntityData().setBoolean("NoDrop", true);
			}

			victim.onDeath(source);
			victim.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(Double.NEGATIVE_INFINITY);
			victim.heal(0.5f);
			victim.setHealth(0.0F);
			victim.onKillCommand();
			victim.setDead();
			victim.isDead = true;

			PacketDispatcher.wrapper.sendToAllTracking(new PacketSpecialDeath(victim, 3, data), victim);
			victim.world.setEntityState(victim, (byte)2);

			if (victim instanceof EntityPlayerMP playerVictim) {
				PacketDispatcher.wrapper.sendTo(new PacketSpecialDeath(victim, 3, data), playerVictim);
				var deathText = source.getDeathMessage(victim).getFormattedText();
				PacketDispatcher.sendTo(new GuiDeathPacket(deathText), playerVictim);
			}
		}
	}
}
