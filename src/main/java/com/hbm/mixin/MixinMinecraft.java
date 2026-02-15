package com.hbm.mixin;

import com.hbm.items.weapon.ItemSwordCutter;
import com.hbm.items.weapon.ItemCrucible;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(net.minecraft.client.Minecraft.class)
public class MixinMinecraft {

    /**
     * 替换 rightClickMouse 中调用 PlayerControllerMP.interactWithEntity(player, entity, RayTraceResult, hand)
     * 当手持自定义武器时返回 PASS，让后续物品使用逻辑继续执行。
     */
    @Redirect(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;interactWithEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/RayTraceResult;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"
            )
    )
    private EnumActionResult redirectInteractWithEntityWithHit(
            PlayerControllerMP controller,
            EntityPlayer player,
            Entity target,
            RayTraceResult hit,
            EnumHand hand) {

        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty() && (stack.getItem() instanceof ItemSwordCutter || stack.getItem() instanceof ItemCrucible)) {
            return EnumActionResult.PASS;
        }
        return controller.interactWithEntity(player, target, hit, hand);
    }

    /**
     * 替换 rightClickMouse 中调用 PlayerControllerMP.interactWithEntity(player, entity, hand)
     * 当手持自定义武器时返回 PASS，让后续物品使用逻辑继续执行。
     */
    @Redirect(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;interactWithEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"
            )
    )
    private EnumActionResult redirectInteractWithEntityNoHit(
            PlayerControllerMP controller,
            EntityPlayer player,
            Entity target,
            EnumHand hand) {

        ItemStack stack = player.getHeldItem(hand);
        if (!stack.isEmpty() && (stack.getItem() instanceof ItemSwordCutter || stack.getItem() instanceof ItemCrucible)) {
            return EnumActionResult.PASS;
        }
        return controller.interactWithEntity(player, target, hand);
    }
}
