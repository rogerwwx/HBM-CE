package com.hbm.mixin;

import com.hbm.render.util.NTMBufferBuilder;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder implements NTMBufferBuilder {
}
