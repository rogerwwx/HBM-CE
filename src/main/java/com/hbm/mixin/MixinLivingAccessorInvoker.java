package com.hbm.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.CombatTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityLivingBase.class)
public interface MixinLivingAccessorInvoker {

    // recentlyHit
    @Accessor("recentlyHit")
    int getRecentlyHit();

    @Accessor("recentlyHit")
    void setRecentlyHit(int value);

    // scoreValue
    @Accessor("scoreValue")
    int getScoreValue();

    @Accessor("scoreValue")
    void setScoreValue(int value);

    // attackingPlayer
    @Accessor("attackingPlayer")
    EntityPlayer getAttackingPlayer();

    @Accessor("attackingPlayer")
    void setAttackingPlayer(EntityPlayer player);

    // dropLoot
    @Invoker("dropLoot")
    void invokeDropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source);
}

/*
===========================
用法示例
===========================

// 获取 recentlyHit
MixinLivingAccessorInvoker accessor = (MixinLivingAccessorInvoker) target;
int hit = accessor.getRecentlyHit();

// 修改 recentlyHit
accessor.setRecentlyHit(0);

// 获取 scoreValue
int score = accessor.getScoreValue();

// 修改 scoreValue
accessor.setScoreValue(score + 10);

// 获取最后攻击者
EntityPlayer killer = accessor.getAttackingPlayer();

// 修改最后攻击者
accessor.setAttackingPlayer(null);

// 调用掉落逻辑
accessor.invokeDropLoot(true, 2, DamageSource.GENERIC);
*/
