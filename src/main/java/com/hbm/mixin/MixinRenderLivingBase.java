package com.hbm.mixin;

import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBase<T extends EntityLivingBase> {

    @Shadow
    protected abstract float handleRotationFloat(T entity, float partialTicks);

    @Shadow
    protected abstract void applyRotations(T entity, float ageInTicks, float rotationYaw, float partialTicks);

    // 静态桥接方法
    public static float callHandleRotationFloat(RenderLivingBase<?> renderer, EntityLivingBase entity, float partialTicks) {
        return ((MixinRenderLivingBase<EntityLivingBase>) (Object) renderer).handleRotationFloat(entity, partialTicks);
    }

    public static void callApplyRotations(RenderLivingBase<?> renderer, EntityLivingBase entity, float ageInTicks, float rotationYaw, float partialTicks) {
        ((MixinRenderLivingBase<EntityLivingBase>) (Object) renderer).applyRotations(entity, ageInTicks, rotationYaw, partialTicks);
    }
}
