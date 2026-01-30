package com.hbm.mixin;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SharedMonsterAttributes.class)
public abstract class MixinAttribute {

    // Shadow 原版的静态字段
    @Shadow @Mutable
    public static IAttribute MAX_HEALTH;
    @Shadow @Mutable public static IAttribute ARMOR_TOUGHNESS;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void tweakAttributes(CallbackInfo ci) {
        // 修改最大生命值上限
        MAX_HEALTH = (RangedAttribute) new RangedAttribute(null, "generic.maxHealth",
                20.0D, 0.0D, 10000.0D) // 改成 10000
                .setDescription("Max Health").setShouldWatch(true);

        // 修改护甲韧性上限
        ARMOR_TOUGHNESS = (RangedAttribute) new RangedAttribute(null, "generic.armorToughness",
                0.0D, 0.0D, 100.0D) // 改成 100
                .setDescription("Armor Toughness").setShouldWatch(true);
    }
}
