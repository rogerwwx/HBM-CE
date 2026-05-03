package com.hbm.entity.mob.ai;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;

import java.util.List;

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

    // 排斥力性能优化缓存
    private int avoidanceUpdateTimer = 0;
    private Vec3d cachedAvoidanceVector = Vec3d.ZERO;

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
        this.pathUpdateCooldown = 4 + attacker.getRNG().nextInt(7);

        double distanceToTarget = attacker.getDistance(attackTarget);
        double healthRatio = attacker.getHealth() / attacker.getMaxHealth();

        if (healthRatio > 0.7 && distanceToTarget < IDEAL_MIN) {
            executeAggressiveCharge();
            return;
        }

        if (distanceToTarget < IDEAL_MIN) {
            executeFleeManeuver();
            return;
        }

        predictTargetMovement();
        executeStandardStrafing(healthRatio);
    }

    /**
     * 带缓存机制的环境排斥力计算（保护服务器 TPS 性能）
     */
    private Vec3d getAvoidanceVector() {
        if (this.avoidanceUpdateTimer-- > 0) {
            return this.cachedAvoidanceVector;
        }
        this.avoidanceUpdateTimer = 20; // 每 5 ticks 扫描一次即可，不需要每 tick 都扫

        Vec3d avoidanceVector = Vec3d.ZERO;
        List<EntityLivingBase> nearbyEntities = worldObj.getEntitiesWithinAABB(EntityLivingBase.class, attacker.getEntityBoundingBox().grow(5.0));

        for (EntityLivingBase entity : nearbyEntities) {
            if (entity != attacker && entity != attackTarget && entity.isEntityAlive()) {
                Vec3d awayFromB = new Vec3d(attacker.posX - entity.posX, 0, attacker.posZ - entity.posZ);
                double distance = awayFromB.length();
                if (distance < 5.0 && distance > 0.01) {
                    double force = (5.0 - distance) * 0.8;
                    avoidanceVector = avoidanceVector.add(awayFromB.normalize().scale(force));
                }
            }
        }
        this.cachedAvoidanceVector = avoidanceVector;
        return avoidanceVector;
    }

    private void executeAggressiveCharge() {
        Vec3d vectorToTarget = new Vec3d(attackTarget.posX - attacker.posX, 0, attackTarget.posZ - attacker.posZ).normalize();
        Vec3d chargePoint = new Vec3d(attacker.posX, attacker.posY, attacker.posZ).add(vectorToTarget.scale(-8.0));

        if (!this.attacker.getNavigator().tryMoveToXYZ(chargePoint.x, chargePoint.y, chargePoint.z, this.speedTowardsTarget * 2.5)) {
            executeFleeManeuver();
        }
    }

    private void executeFleeManeuver() {
        Vec3d attackerPos = new Vec3d(attacker.posX, attacker.posY, attacker.posZ);
        Vec3d targetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ);

        Vec3d fleeDirFromA = attackerPos.subtract(targetPos).normalize();
        Vec3d avoidanceForce = getAvoidanceVector();
        Vec3d combinedFleeDir = fleeDirFromA.add(avoidanceForce).normalize();

        Vec3d syntheticDangerPoint = attackerPos.subtract(combinedFleeDir.scale(10.0));
        Vec3d fleePos = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.attacker, 16, 7, syntheticDangerPoint);

        if (fleePos != null) {
            boolean success = this.attacker.getNavigator().tryMoveToXYZ(fleePos.x, fleePos.y, fleePos.z, this.speedTowardsTarget * 1.4);
            if (!success) {
                this.pathUpdateCooldown = 0;
            }
        } else {
            // 【关键修复】：使用 getJumpHelper() 这是原版 AI 操作实体跳跃的合法公开方法，不会报 protected 错误
            if (this.attacker.onGround) {
                this.attacker.getJumpHelper().setJumping();
            }
            this.pathUpdateCooldown = 0;
        }
    }

    private void executeStandardStrafing(double healthRatio) {
        Vec3d targetPos = calculateStrafingPosition();

        if (!isPathClear(targetPos)) {
            targetPos = findFlankPosition(targetPos);
        }

        double moveSpeed = (healthRatio < 0.3 || attacker.getDistance(attackTarget) > IDEAL_MAX) ? this.speedTowardsTarget * 0.7 : this.speedTowardsTarget;

        if (!this.attacker.getNavigator().tryMoveToXYZ(targetPos.x, targetPos.y, targetPos.z, moveSpeed)) {
            this.strafeDirection = -this.strafeDirection;
            this.pathUpdateCooldown = 0;
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
        Vec3d predictedTargetPos = new Vec3d(attackTarget.posX, attackTarget.posY, attackTarget.posZ).add(this.targetVelocity.scale(predictionTicks));
        Vec3d attackerPos = new Vec3d(attacker.posX, attacker.posY, attacker.posZ);
        Vec3d vectorToTarget = predictedTargetPos.subtract(attackerPos);
        vectorToTarget = new Vec3d(vectorToTarget.x, 0, vectorToTarget.z);

        double currentDistance = vectorToTarget.length();
        if (currentDistance < 0.001) return attackerPos;

        Vec3d directionToTarget = vectorToTarget.normalize();
        Vec3d strafeVector = new Vec3d(-directionToTarget.z, 0, directionToTarget.x);

        double idealDistance = (IDEAL_MIN + IDEAL_MAX) / 2.0;
        double distanceError = currentDistance - idealDistance;

        Vec3d finalMoveVector = directionToTarget.scale(-distanceError * 0.8).add(strafeVector.scale(3.0 * this.strafeDirection));

        Vec3d avoidanceForce = getAvoidanceVector();
        finalMoveVector = finalMoveVector.add(avoidanceForce);

        return attackerPos.add(finalMoveVector);
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