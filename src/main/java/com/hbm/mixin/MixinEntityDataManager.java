package com.hbm.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase; // 引入生物基类
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityDataManager.class)
public abstract class MixinEntityDataManager {

    @Shadow @Final
    private Entity entity;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void onGet(DataParameter<T> key, CallbackInfoReturnable<T> cir) {
        // 1. 核心修复：必须先判断它是不是活的生物！直接过滤掉掉落物、矿车等实体
        if (this.entity instanceof EntityLivingBase) {
            // 2. 判断强制死亡状态
            if (this.entity.getEntityData().getBoolean("force_Dead")) {
                // 3. 最后再对比 Key (避免给不相干的实体频繁调用 getHealthKey)
                if (key == MixinLivingAccessorInvoker.getHealthKey()) {
                    cir.setReturnValue((T)(Float)0.0F);
                }
            }
        }
    }

    @Redirect(
            method = "set",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/datasync/EntityDataManager$DataEntry;setValue(Ljava/lang/Object;)V"
            )
    )
    private <T> void onSetRedirect(EntityDataManager.DataEntry<T> entry, Object value) {
        // 同样：先判断是不是生物实例！
        if (this.entity instanceof EntityLivingBase) {
            if (this.entity.getEntityData().getBoolean("force_Dead")) {
                if (entry.getKey() == MixinLivingAccessorInvoker.getHealthKey()) {
                    entry.setValue((T)(Float)0.0F);
                    return;
                }
            }
        }
        entry.setValue((T)value);
    }
}