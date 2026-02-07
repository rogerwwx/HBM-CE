package com.hbm.entity.mob.ai;

import com.hbm.render.amlfrom1710.Vec3;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.Path;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;

public class EntityAIMaskmanCasualApproach extends EntityAIBase {

	World worldObj;
	EntityCreature attacker;
	int attackTick;
	double speedTowardsTarget;
	boolean longMemory;
	Path entityPathEntity;
	Class classTarget;

	private int pathTimer;
	private int failedPathFindingPenalty;

	// 目标运动预测
	private double lastTX, lastTZ;

	// 战术距离
	private static final double ENGAGE_RANGE = 50.0;
	private static final double IDEAL_MIN = 10.0;
	private static final double IDEAL_MAX = 30.0;

	// 理想作战距离：动态微调
	private static final double IDEAL_DISTANCE = 16.0; // 可以微调
	private static final double IDEAL_WOBBLE = 2.0; // 随机抖动范围

	public EntityAIMaskmanCasualApproach(EntityCreature owner, Class target, double speed, boolean longMemory) {
		this(owner, speed, longMemory);
		this.classTarget = target;
	}

	public EntityAIMaskmanCasualApproach(EntityCreature owner, double speed, boolean longMemory) {
		this.attacker = owner;
		this.worldObj = owner.world;
		this.speedTowardsTarget = speed;
		this.longMemory = longMemory;
		this.setMutexBits(3);
	}

	@Override
	public boolean shouldExecute() {
		EntityLivingBase target = attacker.getAttackTarget();
		if (target == null || !target.isEntityAlive()) return false;
		if (classTarget != null && !classTarget.isAssignableFrom(target.getClass())) return false;

		double dist = attacker.getDistance(target);
		return dist <= ENGAGE_RANGE || attacker.getEntitySenses().canSee(target);
	}

	@Override
	public boolean shouldContinueExecuting() {
		EntityLivingBase target = attacker.getAttackTarget();
		if (target == null || !target.isEntityAlive()) return false;

		double dist = attacker.getDistance(target);
		return !(dist > ENGAGE_RANGE * 1.5 && !attacker.getEntitySenses().canSee(target));
	}

	@Override
	public void startExecuting() {
		pathTimer = 0;
	}

	@Override
	public void resetTask() {
		attacker.getNavigator().clearPath();
	}

	@Override
	public void updateTask() {
		EntityLivingBase target = attacker.getAttackTarget();
		if (target == null) return;

		attacker.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);

		double dist = attacker.getDistance(target);

		// ===== 敌方运动预测（平滑速度） =====
		double tx = target.posX;
		double tz = target.posZ;

		double vx = tx - lastTX;
		double vz = tz - lastTZ;

		vx = 0.7 * vx + 0.3 * (tx - lastTX);
		vz = 0.7 * vz + 0.3 * (tz - lastTZ);

		lastTX = tx;
		lastTZ = tz;

		double predictX = tx + vx * 6;
		double predictZ = tz + vz * 6;

		Vec3 toPred = Vec3.createVectorHelper(
				predictX - attacker.posX,
				0,
				predictZ - attacker.posZ
		);

		Vec3 dir = toPred.normalize();
		Vec3 side = Vec3.createVectorHelper(-dir.zCoord, 0, dir.xCoord);

		// ===== 动态理想距离（血量感知） =====
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		double dynamicIdeal = IDEAL_DISTANCE;

		if (healthRatio > 0.7) {
			dynamicIdeal = IDEAL_MIN + 2; // 高血量 → 更靠近
		} else if (healthRatio < 0.3) {
			dynamicIdeal = IDEAL_MAX;     // 低血量 → 拉远
		}

		// ===== 计算距离误差 =====
		double dx = attacker.posX - predictX;
		double dz = attacker.posZ - predictZ;
		double currentDist = Math.sqrt(dx * dx + dz * dz);
		double error = currentDist - dynamicIdeal;

		Vec3 correction = Vec3.createVectorHelper(
				dir.xCoord * error,
				0,
				dir.zCoord * error
		);

		// ===== 随机抖动 + 绕圈行为 =====
		double wobble = attacker.getRNG().nextGaussian() * IDEAL_WOBBLE;
		Vec3 finalMove = Vec3.createVectorHelper(
				correction.xCoord + side.xCoord * wobble,
				0,
				correction.zCoord + side.zCoord * wobble
		);

		if (attacker.getRNG().nextBoolean()) {
			finalMove = Vec3.createVectorHelper(
					finalMove.xCoord + side.xCoord * 0.5,
					0,
					finalMove.zCoord + side.zCoord * 0.5
			);
		}

		double px = attacker.posX + finalMove.xCoord;
		double pz = attacker.posZ + finalMove.zCoord;

		// ===== 环境感知 =====
		if (!worldObj.isAirBlock(new BlockPos(px, attacker.posY - 1, pz))) {
			px = attacker.posX + attacker.getRNG().nextGaussian() * 2;
			pz = attacker.posZ + attacker.getRNG().nextGaussian() * 2;
		}

		// ===== 路径紧急打断 =====
		boolean emergencyDisengage = dist <= IDEAL_MIN + 4;
		if (emergencyDisengage) {
			attacker.getNavigator().clearPath();
			pathTimer = 0;
		}

		// ===== 路径调度 =====
		pathTimer--;

		if (pathTimer <= 0) {
			pathTimer = Math.min(failedPathFindingPenalty + 6 + attacker.getRNG().nextInt(8), 40);

			double moveSpeed = speedTowardsTarget;

			// 血量高 → 更激进，靠近时加速
			if (healthRatio > 0.7 && dist <= IDEAL_MIN) {
				moveSpeed = 2.5 * speedTowardsTarget;
			}
			// 血量低 → 更保守，减速
			else if (healthRatio < 0.3) {
				moveSpeed = 0.6 * speedTowardsTarget;
			}
			// 原有逻辑：过远时减速
			else if (dist > IDEAL_MAX) {
				moveSpeed = 0.7 * speedTowardsTarget;
			}

			if (!attacker.getNavigator().tryMoveToXYZ(px, attacker.posY, pz, moveSpeed)) {
				failedPathFindingPenalty = Math.min(failedPathFindingPenalty + 5, 40);
			} else {
				failedPathFindingPenalty = Math.max(failedPathFindingPenalty - 2, 0);
			}
		}
	}

	// 原接口保持
	public double[] getApproachPos() {
		EntityLivingBase target = attacker.getAttackTarget();
		Vec3 vec = Vec3.createVectorHelper(attacker.posX - target.posX, attacker.posY - target.posY, attacker.posZ - target.posZ);
		double range = Math.min(vec.length(), 20) - 10;
		vec = vec.normalize();

		double x = attacker.posX + vec.xCoord * range + attacker.getRNG().nextGaussian() * 2;
		double y = attacker.posY + vec.yCoord - 5 + attacker.getRNG().nextInt(11);
		double z = attacker.posZ + vec.zCoord * range + attacker.getRNG().nextGaussian() * 2;

		return new double[]{x, y, z};
	}
}
