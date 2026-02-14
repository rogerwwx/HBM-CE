package com.hbm.mixin;

import com.hbm.items.weapon.ItemSwordCutter;
import com.hbm.items.weapon.ItemCrucible;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.world.World;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    /**
     * 拦截 ENTITY 分支里的 interactWithEntity 返回值，
     * 如果手持的是自定义剑/Crucible，就强制返回 FAIL，
     * 这样原版不会直接 return，而是继续往下走到 processRightClick。
     */
    @Redirect(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;interactWithEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/RayTraceResult;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"
            )
    )
    private EnumActionResult redirectInteractWithEntity1(
            net.minecraft.client.multiplayer.PlayerControllerMP controller,
            net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.entity.Entity target,
            RayTraceResult hit,
            EnumHand hand) {

        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty() && (stack.getItem() instanceof ItemSwordCutter || stack.getItem() instanceof ItemCrucible)) {
            // 跳过实体交互，返回 FAIL，让逻辑继续走到物品使用
            return EnumActionResult.PASS;
        }
        return controller.interactWithEntity(player, target, hit, hand);
    }

    @Redirect(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;interactWithEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"
            )
    )
    private EnumActionResult redirectInteractWithEntity2(
            net.minecraft.client.multiplayer.PlayerControllerMP controller,
            net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.entity.Entity target,
            EnumHand hand) {

        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty() && (stack.getItem() instanceof ItemSwordCutter || stack.getItem() instanceof ItemCrucible)) {
            return EnumActionResult.PASS;
        }
        return controller.interactWithEntity(player, target, hand);
    }
}
