package com.hbm.entity.mob.ai;

import com.hbm.render.amlfrom1710.Vec3;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.pathfinding.Path;
import net.minecraft.world.World;

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

		// ===== 敌方运动预测 =====
		double tx = target.posX;
		double tz = target.posZ;

		double vx = tx - lastTX;
		double vz = tz - lastTZ;

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
		Vec3 away = Vec3.createVectorHelper(-dir.xCoord, 0, -dir.zCoord);

		// ===== 计算距离误差 =====
		double dx = attacker.posX - predictX;
		double dz = attacker.posZ - predictZ;
		double currentDist = Math.sqrt(dx * dx + dz * dz);
		double error = currentDist - IDEAL_DISTANCE;  // 当前误差（目标距离 - 实际距离）

		// ===== 修正移动方向 =====
		Vec3 correction = Vec3.createVectorHelper(
				dir.xCoord * error, // 根据误差修正
				0,
				dir.zCoord * error
		);

		// ===== 加入随机微调（增加自然感） =====
		double wobble = attacker.getRNG().nextGaussian() * IDEAL_WOBBLE;
		Vec3 finalMove = Vec3.createVectorHelper(
				correction.xCoord + side.xCoord * wobble,
				0,
				correction.zCoord + side.zCoord * wobble
		);

		// ===== 得到新的目标位置 =====
		double px = attacker.posX + finalMove.xCoord;
		double pz = attacker.posZ + finalMove.zCoord;

		// ===== 路径紧急打断机制 =====
		boolean emergencyDisengage = dist <= IDEAL_MIN + 4;
		if (emergencyDisengage) {
			attacker.getNavigator().clearPath();
			pathTimer = 0;
		}

		// ===== 路径调度 =====
		pathTimer--;

		if (pathTimer <= 0) {
			pathTimer = failedPathFindingPenalty + 6 + attacker.getRNG().nextInt(8);
			// 确保移动速度控制
			double moveSpeed = speedTowardsTarget;
			// 如果敌人靠近，增加移动速度
			if (dist <= IDEAL_MIN) {
				moveSpeed = 2.5 * speedTowardsTarget;  // 加快速度，1.5 倍是一个示例
			}
			if (!attacker.getNavigator().tryMoveToXYZ(px, attacker.posY, pz, moveSpeed)) {
				failedPathFindingPenalty += 10;
			} else {
				failedPathFindingPenalty = 0;
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
