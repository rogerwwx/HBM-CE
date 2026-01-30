package com.hbm.entity.mob.ai;

import com.hbm.entity.projectile.EntityBulletBase;
import com.hbm.handler.BulletConfigSyncingUtil;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.render.amlfrom1710.Vec3;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

public class EntityAIMaskmanLasergun extends EntityAIBase {

	private final EntityCreature owner;
	private EntityLivingBase target;

	private EnumLaserAttack attack;
	private int timer;
	private int attackCount;

	// 混合攻击有效距离
	private static final double MIN_RANGE = 4.0D;
	private static final double MAX_RANGE = 30.0D;

	public EntityAIMaskmanLasergun(EntityCreature owner) {
		this.owner = owner;
		// 初始攻击：完全随机
		this.attack = EnumLaserAttack.values()[owner.getRNG().nextInt(EnumLaserAttack.values().length)];
	}

	@Override
	public boolean shouldExecute() {
		EntityLivingBase entity = owner.getAttackTarget();
		if (entity == null || !entity.isEntityAlive()) {
			return false;
		}

		this.target = entity;

		double dist = Vec3.createVectorHelper(
				target.posX - owner.posX,
				target.posY - owner.posY,
				target.posZ - owner.posZ
		).length();

		return dist >= MIN_RANGE && dist <= MAX_RANGE;
	}

	@Override
	public void updateTask() {
		if (target == null || !target.isEntityAlive()) {
			return;
		}

		timer--;

		if (timer <= 0) {
			timer = attack.delay;

			double dist = Vec3.createVectorHelper(
					target.posX - owner.posX,
					target.posY - owner.posY,
					target.posZ - owner.posZ
			).length();

			if (dist >= MIN_RANGE && dist <= MAX_RANGE) {
				switch (attack) {
					case ORB: {
						EntityBulletBase orb = new EntityBulletBase(
								owner.world,
								BulletConfigSyncingUtil.MASKMAN_ORB,
								owner,
								target,
								2.0F,
								0
						);
						// ORB 弹道上抬，适合近距离
						orb.motionY += 0.5D;
						owner.world.spawnEntity(orb);
						owner.playSound(HBMSoundHandler.teslaShoot, 1.0F, 1.0F);
						break;
					}

					case MISSILE: {
						EntityBulletBase missile = new EntityBulletBase(
								owner.world,
								BulletConfigSyncingUtil.MASKMAN_ROCKET,
								owner,
								target,
								1.0F,
								0
						);
						Vec3 vec = Vec3.createVectorHelper(
								target.posX - owner.posX,
								0,
								target.posZ - owner.posZ
						);
						missile.motionX = vec.xCoord * 0.05D;
						missile.motionY = 0.5D + owner.getRNG().nextDouble() * 0.5D;
						missile.motionZ = vec.zCoord * 0.05D;
						owner.world.spawnEntity(missile);
						owner.playSound(HBMSoundHandler.hkShoot, 1.0F, 1.0F);
						break;
					}

					case SPLASH: {
						for (int i = 0; i < 5; i++) {
							EntityBulletBase tracer = new EntityBulletBase(
									owner.world,
									BulletConfigSyncingUtil.MASKMAN_TRACER,
									owner,
									target,
									1.0F,
									0.05F
							);
							owner.world.spawnEntity(tracer);
						}
						break;
					}
				}
			}

			attackCount++;
			if (attackCount >= attack.amount) {
				attackCount = 0;
				attack = chooseNextAttack(dist);
			}
		}

		// 保持面向目标
		owner.rotationYaw = owner.rotationYawHead;
	}

	/**
	 * 距离感知的加权随机选择
	 * - 仍然是随机
	 * - ORB 距离越远权重越低
	 */
	private EnumLaserAttack chooseNextAttack(double dist) {

		int missileWeight = 3;
		int splashWeight = 3;
		int orbWeight;

		if (dist <= 6.0D) {
			orbWeight = 3;   // 近战：ORB 常见
		} else if (dist <= 17.0D) {
			orbWeight = 1;   // 中距离：ORB 偶尔
		} else {
			orbWeight = 0;   // 远距离：极低
		}

		int total = orbWeight + missileWeight + splashWeight;
		int roll = owner.getRNG().nextInt(total);

		if (roll < orbWeight) {
			return EnumLaserAttack.ORB;
		}
		roll -= orbWeight;

		if (roll < missileWeight) {
			return EnumLaserAttack.MISSILE;
		}
		return EnumLaserAttack.SPLASH;
	}

	private enum EnumLaserAttack {
		ORB(60, 5),
		MISSILE(10, 10),
		SPLASH(40, 3);

		public final int delay;
		public final int amount;

		EnumLaserAttack(int delay, int amount) {
			this.delay = delay;
			this.amount = amount;
		}
	}
}
