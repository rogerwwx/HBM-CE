package com.hbm.util;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

public final class DropItem {

    public static void dropPlayerInventory(EntityPlayer player, boolean clearBackpackOnly) {
        World world = player.world;

        if (world.isRemote) return;

        // 掉落主背包
        for (ItemStack s : player.inventory.mainInventory) {
            if (!s.isEmpty()) spawnItem(world, player.posX, player.posY, player.posZ, s.copy());
        }

        // 掉落副手
        for (ItemStack s : player.inventory.offHandInventory) {
            if (!s.isEmpty()) spawnItem(world, player.posX, player.posY, player.posZ, s.copy());
        }

        // 掉落盔甲（如果不是只清背包的话）

        for (ItemStack s : player.inventory.armorInventory) {
            if (!s.isEmpty()) spawnItem(world, player.posX, player.posY, player.posZ, s.copy());
        }

        // 清理背包和副手
        if (clearBackpackOnly) {
            for (int i = 0; i < player.inventory.mainInventory.size(); i++) player.inventory.mainInventory.set(i, ItemStack.EMPTY);
            for (int i = 0; i < player.inventory.offHandInventory.size(); i++) player.inventory.offHandInventory.set(i, ItemStack.EMPTY);
        } else {
            player.inventory.clear();
        }

        player.inventory.markDirty();

        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).sendContainerToPlayer(player.openContainer);
        }
    }

    private static void spawnItem(@NotNull World world, double x, double y, double z, ItemStack stack) {
        // 初始位置随机偏移小范围，模拟玩家掉落位置
        double startX = x + (world.rand.nextDouble() - 0.5) * 0.35; // [-0.175, 0.175]
        double startY = y + 0.25 + world.rand.nextDouble() * 0.25;  // 0.25~0.5
        double startZ = z + (world.rand.nextDouble() - 0.5) * 0.35; // [-0.175, 0.175]

        // 直接调用原版构造函数生成 EntityItem
        EntityItem item = new EntityItem(world, startX, startY, startZ, stack);
        item.setDefaultPickupDelay();
        world.spawnEntity(item);
    }
}