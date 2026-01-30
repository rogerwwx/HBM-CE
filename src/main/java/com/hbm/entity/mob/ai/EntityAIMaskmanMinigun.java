package com.hbm.entity.mob.ai;

import com.hbm.entity.projectile.EntityBulletBase;
import com.hbm.handler.BulletConfigSyncingUtil;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.render.amlfrom1710.Vec3;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

public class EntityAIMaskmanMinigun extends EntityAIBase {

	private EntityCreature owner;
	private EntityLivingBase target;
	private int delay;
	private int timer;

	public EntityAIMaskmanMinigun(EntityCreature owner, int delay) {
		this.owner = owner;
		this.delay = delay;
		this.timer = delay;
	}

	@Override
	public boolean shouldExecute() {
		EntityLivingBase entity = this.owner.getAttackTarget();

		if (entity == null || !entity.isEntityAlive()) {  // Check if the target is alive
			return false;
		} else {
			this.target = entity;
			double dist = Vec3.createVectorHelper(target.posX - owner.posX, target.posY - owner.posY, target.posZ - owner.posZ).length();
			return dist > 0 && dist <= 13; // Minigun only attacks in range of 1-13 blocks
		}
	}

	@Override
	public boolean shouldContinueExecuting() {
		// Ensure target is still alive and not out of range or no longer has a path
		return this.shouldExecute() || !this.owner.getNavigator().noPath();
	}

	@Override
	public void updateTask() {
		// If timer has reached 0, spawn a bullet
		timer--;

		if (timer <= 0) {
			timer = delay;

			// Spawn minigun bullet
			EntityBulletBase bullet = new EntityBulletBase(owner.world, BulletConfigSyncingUtil.MASKMAN_BULLET, owner, target, 1.0F, 0);
			owner.world.spawnEntity(bullet);
			owner.playSound(HBMSoundHandler.calShoot, 1.0F, 1.0F);
		}

		// Keep the rotation aligned with the target
		this.owner.rotationYaw = this.owner.rotationYawHead;
	}
}