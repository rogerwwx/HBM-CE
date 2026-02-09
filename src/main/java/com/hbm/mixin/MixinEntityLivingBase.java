package com.hbm.mixin;

import com.hbm.items.ModItems;
import com.hbm.items.gear.ArmorFSB;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {

    // 暴露私有静态 HEALTH
    @Shadow
    @Final
    static DataParameter<Float> HEALTH;


    @Shadow
    public abstract ItemStack getItemStackFromSlot(EntityEquipmentSlot slot);

    // 你已有的逻辑（force_Dead / dns_plate 等）
    private boolean isDnsPlateActive(EntityLivingBase self) {
        //判断玩家
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
        EntityLivingBase self = (EntityLivingBase) (Object) this;

        if (self.world.isRemote) {
            return;
        }

        if (self.getEntityData().getBoolean("force_Dead")) {
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
        EntityLivingBase self = (EntityLivingBase) (Object) this;

        if (isDnsPlateActive(self)) {
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "setHealth",
            at = @At("HEAD"),
            argsOnly = true
    )
    private float modifyHealthArg(float health) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        // 使用你原本的逻辑判断
        if (isDnsPlateActive(self)) {
            return 100.0F; // 强制改成 100
        }
        return health; // 否则保持原样
    }

    @Unique
    public void setHealthDirectly(float health) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;

        // 只在服务端执行
        if (self.world == null || self.world.isRemote) {
            return;
        }
        //EntityDataManager edm = ((MixinEntityAccessor) self).getDataManager(); if (edm == null) return;

        // 写入 dataManager（会触发 notifyDataManagerChange 与同步）
        EntityDataManager dataManager = ((MixinEntityAccessor)(Object)self).getDataManager();
        dataManager.set((DataParameter)HEALTH, Float.valueOf(health));
    }
}
