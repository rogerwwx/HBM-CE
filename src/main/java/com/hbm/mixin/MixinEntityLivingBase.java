package com.hbm.mixin;

import com.hbm.api.entity.IHealthDirectAccess;
import com.hbm.items.ModItems;
import com.hbm.items.gear.ArmorFSB;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity implements IHealthDirectAccess {

    private MixinEntityLivingBase(World worldIn) {super(worldIn);}

    @Shadow @Final private static DataParameter<Float> HEALTH;

    // --- 缓存与配置（仅内存缓存，不写入 entityData） ---
    @Unique private Item lastChestItem = null;
    @Unique private int lastChestCount = 0;
    @Unique private boolean dnsPlateCached = false;
    @Unique private int dnsPlateLastCheckedTick = -1;
    @Unique private boolean inventoryAvailable = false; // 区分进入世界前/后
    private static final int RECHECK_INTERVAL = 5;

    /**
     * 完整检查函数（访问 inventory），仅在 inventory 可用时调用。
     * 保留原有保护逻辑：避免在实体刚进入世界、inventory 未加载时访问。
     */
    private boolean isDnsPlateActive(EntityLivingBase self) {
        if (!(self instanceof EntityPlayer player)) return false;

        if (player.world == null || player.world.isRemote) return false;
        if (player.ticksExisted <= 0) return false;
        if (player instanceof net.minecraftforge.common.util.FakePlayer) return false;
        if (player.inventory == null) return false; // 关键保护：inventory 未就绪时返回 false

        ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest == null || chest.isEmpty()) return false;
        if (chest.getItem() != ModItems.dns_plate) return false;
        if (!(chest.getItem() instanceof ArmorFSB)) return false;

        return ((ArmorFSB) chest.getItem()).isArmorEnabled(chest);
    }

    /**
     * 每 tick 执行：
     * - 如果 inventory 仍未就绪：保持 dnsPlateCached 不变（避免访问导致崩溃）。
     * - 当 inventory 首次从 null -> 非 null 时：立即做一次完整 isDnsPlateActive 检测（这是“进入世界前/后”分离的关键）。
     * - 进入世界后：用轻量签名比较（Item 引用 + count）和周期重评来决定是否调用 isDnsPlateActive。
     */
    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void onEntityUpdate(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;
        if (self.world == null || self.world.isRemote) return;
        if (!(self instanceof EntityPlayer player)) return;

        // 如果 inventory 还没加载，标记为不可用并返回（避免访问）
        if (player.inventory == null) {
            inventoryAvailable = false;
            return;
        }

        // inventory 现在可用
        if (!inventoryAvailable) {
            // inventory 刚变为可用：立即做一次完整检查以恢复正确状态
            inventoryAvailable = true;
            boolean activeNow;
            try {
                activeNow = isDnsPlateActive(self);
            } catch (Throwable t) {
                activeNow = false;
            }
            dnsPlateCached = activeNow;
            // 初始化签名，避免下一步误判为“变化”
            ItemStack chestInit = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            lastChestItem = (chestInit == null || chestInit.isEmpty()) ? null : chestInit.getItem();
            lastChestCount = (chestInit == null) ? 0 : chestInit.getCount();
            dnsPlateLastCheckedTick = player.ticksExisted;
            return;
        }

        // inventory 已就绪：轻量签名比较 + 周期重评
        ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        Item currentItem = (chest == null || chest.isEmpty()) ? null : chest.getItem();
        int currentCount = (chest == null) ? 0 : chest.getCount();

        int tick = player.ticksExisted;
        boolean signatureChanged = (currentItem != lastChestItem) || (currentCount != lastChestCount);
        boolean timeToRecheck = (dnsPlateLastCheckedTick < 0) || (tick - dnsPlateLastCheckedTick >= RECHECK_INTERVAL);

        if (!signatureChanged && !timeToRecheck) {
            return; // 极轻量路径：不做任何昂贵操作
        }

        // 更新签名与 tick
        lastChestItem = currentItem;
        lastChestCount = currentCount;
        dnsPlateLastCheckedTick = tick;

        // 在签名变化或重评时调用完整检查（isDnsPlateActive 很轻量或可视为安全）
        boolean active;
        if (currentItem == ModItems.dns_plate && chest != null && chest.getItem() instanceof ArmorFSB) {
            try {
                active = ((ArmorFSB) chest.getItem()).isArmorEnabled(chest);
            } catch (Throwable t) {
                active = false;
            }
        } else {
            active = false;
        }

        dnsPlateCached = active;
    }

    @Inject(
            method = "getHealth",
            at = @At("HEAD"),
            cancellable = true
    )
    private void forceZeroHealth(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase self = (EntityLivingBase) (Object) this;

        // 只在服务端生效
        if (self.world == null || self.world.isRemote) {
            return;
        }

        // 原有的强制死亡标志仍然生效
        if (self.getEntityData().getBoolean("force_Dead")) {
            cir.setReturnValue(0.0F);
            return;
        }

        // 只读取内存缓存 dnsPlateCached（不会访问 inventory）
        if (dnsPlateCached) {
            cir.setReturnValue(100.0F);
        }
    }

    @Inject(method = "onDeathUpdate", at = @At("HEAD"), cancellable = true)
    private void cancelDeathUpdateIfDnsPlate(CallbackInfo ci) {

        if (dnsPlateCached) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onDeathUpdate",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityLivingBase;setDead()V", shift = At.Shift.AFTER)
    )
    private void afterSetDead(CallbackInfo ci) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;
        self.isDead = true; // 再次标记死亡，不影响史莱姆分裂
    }
    

    @ModifyArg(
            method = "setHealth",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;setHealth(F)V",
                    shift = At.Shift.BEFORE
            ),
            index = 0
    )
    private float modifyArgBeforeSuper(float health) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;
        if (self.world == null || self.world.isRemote) {
            return health;
        }
        return dnsPlateCached ? 100.0F : health;
    }


    @Unique
    public void setHealthDirectly(float health) {
        EntityLivingBase self = (EntityLivingBase)(Object)this;

        if (self.world == null || self.world.isRemote) {
            return;
        }

        if (dataManager != null) {
            dataManager.set(HEALTH, health);
        }
    }
}
