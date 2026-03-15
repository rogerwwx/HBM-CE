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
    private static final double IDEAL_MIN = 8.0;
    private static final double IDEAL_MAX = 30.0;

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

        this.pathUpdateCooldown--;
        if (this.pathUpdateCooldown > 0) return;
        // Base cooldown, gets reset on successful pathing
        this.pathUpdateCooldown = 4 + attacker.getRNG().nextInt(7);

        double distanceToTarget = attacker.getDistance(attackTarget);
        double healthRatio = attacker.getHealth() / attacker.getMaxHealth();

        // --- BEHAVIOR PRIORITY 1: AGGRESSIVE CHARGE ---
        // If at high health and the enemy is too close, execute a high-speed charge.
        if (healthRatio > 0.7 && distanceToTarget < IDEAL_MIN) {
            executeAggressiveCharge();
            return; // Action decided for this tick.
        }

        // --- BEHAVIOR PRIORITY 2: SURVIVAL FLEE ---
        // If the enemy is too close (and we're not charging), the ONLY priority is to retreat.
        if (distanceToTarget < IDEAL_MIN) {
            executeFleeManeuver();
            return; // Action decided for this tick.
        }

        // --- BEHAVIOR PRIORITY 3: STANDARD COMBAT STRAFING ---
        // If we are at a safe distance, perform standard strafing and distance-keeping.
        predictTargetMovement(); // Prediction is only needed for this state
        executeStandardStrafing(healthRatio);
    }

    /**
     * PRIORITY 1: Re-implements your original high-speed charge logic.
     * The goal is to move to a point past the target to disorient them.
     */
    private void executeAggressiveCharge() {
        Vec3d vectorToTarget = new Vec3d(attackTarget.posX - attacker.posX, 0, attackTarget.posZ - attacker.posZ).normalize();
        // Target a point 8 blocks BEHIND the current position, effectively charging through
        Vec3d chargePoint = new Vec3d(attacker.posX, attacker.posY, attacker.posZ).add(vectorToTarget.scale(-8.0));

        if (!this.attacker.getNavigator().tryMoveToXYZ(chargePoint.x, chargePoint.y, chargePoint.z, this.speedTowardsTarget * 2.5)) {
            // If charge fails, do a simple fast flee as a fallback
            executeFleeManeuver();
        }
    }

    /**
     * PRIORITY 2: Uses vanilla's robust method to find a valid position to run to, away from the target.
     * This is the most reliable way to create distance.
     */
    private void executeFleeManeuver() {
        Vec3d fleePos = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.attacker, 16, 7, new Vec3d(this.attackTarget.posX, this.attackTarget.posY, this.attackTarget.posZ));
        if (fleePos != null) {
            // Fleeing is always done at high speed.
            this.attacker.getNavigator().tryMoveToXYZ(fleePos.x, fleePos.y, fleePos.z, this.speedTowardsTarget * 1.4);
        }
    }

    /**
     * PRIORITY 3: The refined strafing logic for maintaining ideal combat distance (10-30 blocks).
     */
    private void executeStandardStrafing(double healthRatio) {
        Vec3d targetPos = calculateStrafingPosition();

        if (!isPathClear(targetPos)) {
            targetPos = findFlankPosition(targetPos);
        }

        // Your original logic: slow down if very far or low health
        double moveSpeed;
        if (healthRatio < 0.3 || attacker.getDistance(attackTarget) > IDEAL_MAX) {
            moveSpeed = this.speedTowardsTarget * 0.7;
        } else {
            moveSpeed = this.speedTowardsTarget;
        }

        if (!this.attacker.getNavigator().tryMoveToXYZ(targetPos.x, targetPos.y, targetPos.z, moveSpeed)) {
            this.pathUpdateCooldown += 10; // Penalize failed pathing
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

    private Vec3d calculateStrafingPosition() {
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

        // Ideal distance is the midpoint of the weapon range when in standard combat.
        double idealDistance = (IDEAL_MIN + IDEAL_MAX) / 2.0;
        double distanceError = currentDistance - idealDistance;

        // Balanced movement: corrects distance while strafing
        Vec3d finalMoveVector = directionToTarget.scale(-distanceError * 0.8).add(strafeVector.scale(3.0 * this.strafeDirection));

        return attackerPos.add(finalMoveVector);
    }

    // --- Unchanged Helper Methods ---
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