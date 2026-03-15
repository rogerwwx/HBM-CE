package com.hbm.entity.mob.ai;

// REFACTORED: Remove the custom Vec3 import
// import com.hbm.render.amlfrom1710.Vec3;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
// REFACTORED: Use the standard Minecraft Vec3d class
import net.minecraft.util.math.Vec3d;

public class EntityAIMaskmanCasualApproach extends EntityAIBase {

	// --- Core Fields ---
	private final EntityCreature attacker;
	private final World worldObj;
	private EntityLivingBase attackTarget;

	// --- Movement & Targeting Parameters ---
	private final double speedTowardsTarget;
	private final boolean longMemory;
	private Class<? extends EntityLivingBase> classTarget;

	// --- Tactical Distance ---
	private static final double ENGAGE_RANGE = 50.0;
	private static final double IDEAL_MIN = 10.0;
	private static final double IDEAL_MAX = 30.0;

	// --- AI State & Timers ---
	private enum AIState { AGGRESSIVE, DEFENSIVE, STRAFING }
	private AIState currentState = AIState.STRAFING;
	private int pathUpdateCooldown;
	private int failedPathFindingPenalty;

	// --- Advanced Movement & Prediction (Using Vec3d) ---
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
		// REFACTORED: Instantiate standard Vec3d
		this.lastTargetPosition = new Vec3d(target.posX, target.posY, target.posZ);
		this.targetVelocity = Vec3d.ZERO; // Use the built-in zero vector constant
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
		this.failedPathFindingPenalty = 0;
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

		this.pathUpdateCooldown = 4 + attacker.getRNG().nextInt(7) + failedPathFindingPenalty;

		Vec3d targetPos = calculateStrategicPosition();

		if (!isPathClear(targetPos)) {
			targetPos = findFlankPosition(targetPos);
		}

		double moveSpeed = getDynamicMoveSpeed();

		// REFACTORED: Vec3d fields are x, y, z
		if (!attacker.getNavigator().tryMoveToXYZ(targetPos.x, targetPos.y, targetPos.z, moveSpeed)) {
			this.failedPathFindingPenalty = Math.min(this.failedPathFindingPenalty + 15, 60);
		} else {
			this.failedPathFindingPenalty = Math.max(this.failedPathFindingPenalty - 10, 0);
		}
	}

	private void updateAIState() {
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		double distanceToTarget = attacker.getDistance(attackTarget);

		if (healthRatio < 0.3 || distanceToTarget < IDEAL_MIN) {
			currentState = AIState.DEFENSIVE;
		} else if (healthRatio > 0.7 && distanceToTarget > IDEAL_MAX * 0.8) {
			currentState = AIState.AGGRESSIVE;
		} else {
			currentState = AIState.STRAFING;
		}
	}

	private void predictTargetMovement() {
		Vec3d currentTargetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ);
		Vec3d movementDelta = currentTargetPos.subtract(this.lastTargetPosition);

		// REFACTORED: Vec3d is immutable, so we create a new instance for the updated velocity.
		double newVelX = this.targetVelocity.x * 0.7 + movementDelta.x * 0.3;
		double newVelZ = this.targetVelocity.z * 0.7 + movementDelta.z * 0.3;
		this.targetVelocity = new Vec3d(newVelX, 0, newVelZ);

		this.lastTargetPosition = currentTargetPos;
	}

	private Vec3d calculateStrategicPosition() {
		int predictionTicks = 8;
		// REFACTORED: Use built-in vector math for prediction.
		Vec3d predictedTargetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ)
				.add(this.targetVelocity.scale(predictionTicks));

		Vec3d attackerPos = new Vec3d(attacker.posX, attacker.posY, attacker.posZ);
		Vec3d vectorToTarget = predictedTargetPos.subtract(attackerPos);
		vectorToTarget = new Vec3d(vectorToTarget.x, 0, vectorToTarget.z); // Flatten to 2D plane

		double currentDistance = vectorToTarget.length();
		if (currentDistance < 0.001) return attackerPos; // Avoid division by zero

		Vec3d directionToTarget = vectorToTarget.normalize();
		Vec3d strafeVector = new Vec3d(-directionToTarget.z, 0, directionToTarget.x);

		double idealDistance = getDynamicIdealDistance();
		double distanceError = currentDistance - idealDistance;

		Vec3d finalMoveVector = switch (currentState) {
            case AGGRESSIVE ->
                    directionToTarget.scale(-distanceError).add(strafeVector.scale(2.0 * this.strafeDirection));
            case DEFENSIVE -> {
                double retreatFactor = Math.max(0, idealDistance - currentDistance);
                yield directionToTarget.scale(retreatFactor * 1.5).add(strafeVector.scale(4.0 * this.strafeDirection));
            }
            default ->
                    directionToTarget.scale(-distanceError * 0.8).add(strafeVector.scale(3.0 * this.strafeDirection));
        };

		// REFACTORED: Simplified, readable, and efficient vector calculations.

        return attackerPos.add(finalMoveVector);
	}

	private double getDynamicIdealDistance() {
		double healthRatio = attacker.getHealth() / attacker.getMaxHealth();
		if (healthRatio > 0.7) return IDEAL_MIN + 4.0;
		if (healthRatio < 0.3) return IDEAL_MAX - 2.0;
		return (IDEAL_MIN + IDEAL_MAX) / 2.0;
	}

	private double getDynamicMoveSpeed() {
        return switch (currentState) {
            case AGGRESSIVE -> speedTowardsTarget * 1.2;
            case DEFENSIVE -> speedTowardsTarget * 1.0;
            default -> speedTowardsTarget;
        };
	}

	private boolean isPathClear(Vec3d targetPos) {
		// REFACTORED: No type conversion needed. It's clean and simple.
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
			// REFACTORED: Clean vector math for finding flank positions
			Vec3d flankPos = attackerPos.add(sideVector.scale(offset));
			if (isPathClear(flankPos)) return flankPos;

			flankPos = attackerPos.add(sideVector.scale(-offset));
			if (isPathClear(flankPos)) return flankPos;
		}
		return originalTargetPos;
	}
}