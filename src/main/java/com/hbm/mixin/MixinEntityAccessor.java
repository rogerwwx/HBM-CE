package com.hbm.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.EntityDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface MixinEntityAccessor {
    @Accessor("dataManager")
    EntityDataManager getDataManager();
}
