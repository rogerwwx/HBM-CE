package com.hbm.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.entity.Entity;

@Mixin(WorldServer.class)
public abstract class MixinWorldServer {

    @Redirect(
            method = "tickPlayers",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/entity/Entity;isDead:Z",
                    opcode = org.objectweb.asm.Opcodes.GETFIELD
            )
    )
    private boolean redirectPlayerIsDead(Entity entity) {

        if (entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;

            // 读取玩家 NBT
            NBTTagCompound nbt = player.getEntityData();
            if (nbt.getBoolean("wwj")) {
                // 对被标记玩家强制视为未死亡
                return false;
            }
        }
        // 其他情况保持原行为
        return entity.isDead;
    }
}


