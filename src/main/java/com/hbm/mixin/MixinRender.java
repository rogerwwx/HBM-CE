package com.hbm.mixin;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Render.class)
public abstract class MixinRender<T extends Entity> {

    @Shadow
    protected abstract ResourceLocation getEntityTexture(T entity);

    // 提供静态桥接方法给工具类调用
    public static ResourceLocation callGetEntityTexture(Render<?> renderer, Entity entity) {
        return ((MixinRender<Entity>) (Object) renderer).getEntityTexture(entity);
    }
}
