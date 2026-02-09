package com.hbm.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityDataManager.class)
public abstract class MixinEntityDataManager {

    @Shadow private Entity entity;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void onGet(DataParameter<T> key, CallbackInfoReturnable<T> cir) {
        if (key == MixinEntityLivingBase.getHealthKey()) {
            if (entity != null && entity.getEntityData().getBoolean("force_Dead")) {
                cir.setReturnValue((T)(Float)0.0F);
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
        if (entry.getKey() == MixinEntityLivingBase.getHealthKey()) {
            if (entity != null && entity.getEntityData().getBoolean("force_Dead")) {
                entry.setValue((T)(Float)0.0F);
                return;
            }
        }
        entry.setValue((T)value);
    }
}
