package com.hbm.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hbm.items.gear.ArmorFSB;
import com.hbm.items.ModItems;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.EntityEquipmentSlot;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    private boolean isDnsPlateActive(EntityLivingBase self) {
        if (!(self instanceof EntityPlayer)) {
            return false;
        }

        // 再取装备槽
        ItemStack chest = self.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return false;
        }

        // 判断是不是目标物品
        if (chest.getItem() != ModItems.dns_plate) {
            return false;
        }

        ArmorFSB armor = (ArmorFSB) chest.getItem();
        return armor.isArmorEnabled(chest);
    }


    @Inject(
            method = "getHealth",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceZeroHealth(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;

        if (self.world.isRemote) {
            return;
        }

        // 原有 Dead NBT，保留
        if (self.getEntityData().getBoolean("Dead")) {
            cir.setReturnValue(0.0F);
            return;
        }

        // dns_plate 防死
        if (isDnsPlateActive(self)) {
            cir.setReturnValue(100.0F);
        }
    }


    @Inject(method = "onDeathUpdate", at = @At("HEAD"), cancellable = true)
    private void cancelDeathUpdateIfDnsPlate(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;

        if (isDnsPlateActive(self)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "setHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;setHealth(F)V"
            )
    )
    private void redirectSetHealth(EntityLivingBase entity, float health) {
        if (isDnsPlateActive(entity)) { // 条件判断，示例用你原来的逻辑
            health = 100f;
        }
        entity.setHealth(health); // 调用原方法
    }

    @Redirect(
            method = "onDeathUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;setDead()V")
    )
    private void redirectSetDead(EntityLivingBase instance) {
        instance.isDead = true;
    }
}
