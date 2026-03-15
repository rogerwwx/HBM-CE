package com.hbm.entity.mob.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;

public class EntityAIMaskmanCasualApproach extends EntityAIBase {

	private final EntityCreature attacker;
	private final World worldObj;
	private EntityLivingBase attackTarget;

	private final double speedTowardsTarget;
	private final boolean longMemory;
	private Class<? extends EntityLivingBase> classTarget;

	private static final double ENGAGE_RANGE = 50.0;
	private static final double IDEAL_MIN = 10.0;
	private static final double IDEAL_MAX = 30.0;
	// MODIFIED: Define the emergency distance as a constant per your request
	private static final double EMERGENCY_DISTANCE = 4.0;

	private enum AIState { AGGRESSIVE, DEFENSIVE, STRAFING }
	private AIState currentState = AIState.STRAFING;
	private int pathUpdateCooldown;

	private Vec3d lastTargetPosition;
	private Vec3d targetVelocity;
	private int strafeDirection = 1;

	public EntityAIMaskmanCasualApproach(EntityCreature owner, Class<? extends EntityLivingBase> target, double speed, boolean longMemory) {
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

		this.attackTarget = target;
		this.lastTargetPosition = new Vec3d(target.posX, target.posY, target.posZ);
		this.targetVelocity = Vec3d.ZERO;
		return true;
	}

	@Override
	public boolean shouldContinueExecuting() {
		if (attackTarget == null || !attackTarget.isEntityAlive()) return false;
		return !(attacker.getDistanceSq(attackTarget) > ENGAGE_RANGE * ENGAGE_RANGE && !attacker.getEntitySenses().canSee(attackTarget));
	}

	@Override
	public void startExecuting() {
		this.pathUpdateCooldown = 0;
		this.strafeDirection = attacker.getRNG().nextBoolean() ? 1 : -1;
	}

	@Override
	public void resetTask() {
		this.attackTarget = null;
		attacker.getNavigator().clearPath();
	}

	@Override
	public void updateTask() {
		if (attackTarget == null) return;

		attacker.getLookHelper().setLookPositionWithEntity(attackTarget, 30F, 30F);

		updateAIState();
		predictTargetMovement();

		this.pathUpdateCooldown--;
		if (this.pathUpdateCooldown > 0) return;

		this.pathUpdateCooldown = 4 + attacker.getRNG().nextInt(7);

		if (isEmergency()) {
			executeFleeManeuver();
			return;
		}

		Vec3d targetPos = calculateStrategicPosition();
		if (!isPathClear(targetPos)) {
			targetPos = findFlankPosition(targetPos);
		}

		double moveSpeed = getDynamicMoveSpeed();

		if (!attacker.getNavigator().tryMoveToXYZ(targetPos.x, targetPos.y, targetPos.z, moveSpeed)) {
			this.pathUpdateCooldown += 10;
		}
	}

	/**
	 * MODIFIED: Uses the new EMERGENCY_DISTANCE constant (4.0).
	 */
	private boolean isEmergency() {
		return attacker.getDistance(attackTarget) < EMERGENCY_DISTANCE;
	}

	/**
	 * MODIFIED: Flee speed is now clearly defined.
	 */
	private void executeFleeManeuver() {
		Vec3d fleePos = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.attacker, 16, 7, new Vec3d(this.attackTarget.posX, this.attackTarget.posY, this.attackTarget.posZ));
		if (fleePos != null) {
			// This is the flee speed boost. You can adjust the 1.4 multiplier.
			double fleeSpeed = this.speedTowardsTarget * 1.6;
			this.attacker.getNavigator().tryMoveToXYZ(fleePos.x, fleePos.y, fleePos.z, fleeSpeed);
		}
	}

	private void updateAIState() {
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		double distanceToTarget = attacker.getDistance(attackTarget);

		if (healthRatio < 0.4 || distanceToTarget < IDEAL_MIN) {
			currentState = AIState.DEFENSIVE;
		} else if (healthRatio > 0.7 && distanceToTarget > IDEAL_MAX * 0.7) {
			currentState = AIState.AGGRESSIVE;
		} else {
			currentState = AIState.STRAFING;
		}
	}

	private void predictTargetMovement() {
		Vec3d currentTargetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ);
		Vec3d movementDelta = currentTargetPos.subtract(this.lastTargetPosition);

		double newVelX = this.targetVelocity.x * 0.7 + movementDelta.x * 0.3;
		double newVelZ = this.targetVelocity.z * 0.7 + movementDelta.z * 0.3;
		this.targetVelocity = new Vec3d(newVelX, 0, newVelZ);

		this.lastTargetPosition = currentTargetPos;
	}

	private Vec3d calculateStrategicPosition() {
		int predictionTicks = 8;
		Vec3d predictedTargetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ)
				.add(this.targetVelocity.scale(predictionTicks));

		Vec3d attackerPos = new Vec3d(attacker.posX, attacker.posY, attacker.posZ);
		Vec3d vectorToTarget = predictedTargetPos.subtract(attackerPos);
		vectorToTarget = new Vec3d(vectorToTarget.x, 0, vectorToTarget.z);

		double currentDistance = vectorToTarget.length();
		if (currentDistance < 0.001) return attackerPos;

		Vec3d directionToTarget = vectorToTarget.normalize();
		Vec3d strafeVector = new Vec3d(-directionToTarget.z, 0, directionToTarget.x);

		double idealDistance = getDynamicIdealDistance();
		double distanceError = currentDistance - idealDistance;

		Vec3d finalMoveVector;

		switch (currentState) {
			case AGGRESSIVE:
				finalMoveVector = directionToTarget.scale(-distanceError).add(strafeVector.scale(2.0 * this.strafeDirection));
				break;
			case DEFENSIVE:
				double retreatFactor = Math.max(0, idealDistance - currentDistance);
				finalMoveVector = directionToTarget.scale(retreatFactor * 1.2).add(strafeVector.scale(1.5 * this.strafeDirection));
				break;
			case STRAFING:
			default:
				finalMoveVector = directionToTarget.scale(-distanceError * 0.8).add(strafeVector.scale(3.0 * this.strafeDirection));
				break;
		}

		return attackerPos.add(finalMoveVector);
	}

	private double getDynamicIdealDistance() {
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		if (healthRatio > 0.7) return IDEAL_MIN + 4.0;
		if (healthRatio < 0.4) return IDEAL_MAX - 2.0;
		return (IDEAL_MIN + IDEAL_MAX) / 2.0;
	}

	/**
	 * MODIFIED: Re-integrates your original aggressive speed boost logic.
	 */
	private double getDynamicMoveSpeed() {
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		double distanceToTarget = attacker.getDistance(attackTarget);

		// Your original aggressive speed boost: If high health and too close, charge forward to create space.
		if (healthRatio > 0.7 && distanceToTarget <= IDEAL_MIN) {
			return this.speedTowardsTarget * 2.5;
		}

		switch (currentState) {
			case AGGRESSIVE: return speedTowardsTarget * 1.2;
			case DEFENSIVE: return speedTowardsTarget * 1.1;
			case STRAFING: default: return speedTowardsTarget;
		}
	}

	private boolean isPathClear(Vec3d targetPos) {
		Vec3d startPos = new Vec3d(attacker.posX, attacker.posY + attacker.getEyeHeight(), attacker.posZ);
		Vec3d endPos = new Vec3d(targetPos.x, attacker.posY + attacker.getEyeHeight(), targetPos.z);
		RayTraceResult result = worldObj.rayTraceBlocks(startPos, endPos, false, true, false);
		return result == null || result.typeOfHit == RayTraceResult.Type.MISS;
	}

	private Vec3d findFlankPosition(Vec3d originalTargetPos) {
		Vec3d attackerPos = new Vec3d(attacker.posX, attacker.posY, attacker.posZ);
		Vec3d vectorToTarget = originalTargetPos.subtract(attackerPos).normalize();
		Vec3d sideVector = new Vec3d(-vectorToTarget.z, 0, vectorToTarget.x);

		for (int i = 1; i <= 3; i++) {
			double offset = i * 4.0;
			Vec3d flankPos = attackerPos.add(sideVector.scale(offset));
			if (isPathClear(flankPos)) return flankPos;

			flankPos = attackerPos.add(sideVector.scale(-offset));
			if (isPathClear(flankPos)) return flankPos;
		}
		return originalTargetPos;
	}
}