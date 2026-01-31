package com.hbm.mixin;

import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RenderLivingBase.class)
public interface MixinRenderLivingBase {

    // Invoker 方法：调用 protected/private 方法
    @Invoker("handleRotationFloat")
    float callHandleRotationFloat(EntityLivingBase entity, float partialTicks);

    @Invoker("applyRotations")
    void callApplyRotations(EntityLivingBase entity, float ageInTicks, float rotationYaw, float partialTicks);
}
