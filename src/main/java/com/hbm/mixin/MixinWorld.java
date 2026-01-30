package com.hbm.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class MixinWorld {

    @Redirect(
            method = "updateEntities",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;isDead:Z",
                    opcode = Opcodes.GETFIELD
            )
    )

    private boolean redirectIsDeadInUpdateEntities(Entity entity) {
        if (entity instanceof EntityPlayerMP) {
            if (entity.getEntityData().getBoolean("wwj")) {
                return false;
            }
        }
        return entity.isDead;
    }
}

